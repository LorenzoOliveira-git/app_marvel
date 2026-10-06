'use strict';
const {createHash} = require('node:crypto');
const {HttpsError} = require('firebase-functions/v2/https');
const sharp = require('sharp');
const {POLICY} = require('./hero-creation.cjs');
const cloudinary = require('cloudinary').v2;
function configuration(generation = true) {
  const env = process.env;
  if (generation && (env.HERO_GENERATION_ENABLED !== 'true' || !cloudflareConfigured())) {
    throw new HttpsError('failed-precondition', 'Configure e habilite a geração no ambiente privado do backend local.');
  }
  if (!/^[a-z0-9-]+$/.test(env.CLOUDINARY_CLOUD_NAME || '') || !env.CLOUDINARY_API_KEY || !env.CLOUDINARY_API_SECRET) {
    throw new HttpsError('failed-precondition', 'Configure o Cloudinary no ambiente privado do backend local.');
  }
  return {cloud_name: env.CLOUDINARY_CLOUD_NAME, api_key: env.CLOUDINARY_API_KEY, api_secret: env.CLOUDINARY_API_SECRET, secure: true};
}
function cloudflareConfigured() {
  return /^[a-f0-9]{32}$/i.test(process.env.CLOUDFLARE_ACCOUNT_ID || '')
    && typeof process.env.CLOUDFLARE_API_KEY === 'string' && process.env.CLOUDFLARE_API_KEY.trim().length > 0;
}
function cloudflareEndpoint(path) {
  if (!cloudflareConfigured()) throw new HttpsError('failed-precondition', 'Configure Cloudflare no ambiente privado do backend local.');
  return `https://api.cloudflare.com/client/v4/accounts/${process.env.CLOUDFLARE_ACCOUNT_ID}/ai/${path}`;
}
async function verifyModelAccess() {
  try {
    const result = await fetch(cloudflareEndpoint(`models/search?search=${encodeURIComponent(POLICY.model)}`), {
      headers: {Authorization: `Bearer ${process.env.CLOUDFLARE_API_KEY}`}, redirect: 'error', signal: AbortSignal.timeout(20000)});
    if (!result.ok) throw new Error();
    const data = await result.json();
    if (data.success !== true || !Array.isArray(data.result) || !data.result.some(model => model.name === POLICY.model)) throw new Error();
  } catch (_) { throw new HttpsError('failed-precondition', 'Não foi possível consultar o modelo FLUX.2 klein 9B nesta conta Cloudflare. Nenhuma geração iniciada.'); }
}
function png(bytes) {
  if (!Buffer.isBuffer(bytes) || bytes.length < 33 || bytes.length > 16 * 1024 * 1024
      || bytes.subarray(0,8).toString('hex') !== '89504e470d0a1a0a'
      || bytes.subarray(12,16).toString('ascii') !== 'IHDR'
      || bytes.readUInt32BE(16) !== 1024 || bytes.readUInt32BE(20) !== 1536) {
    throw new Error('invalid-image');
  }
  return bytes;
}
async function normalizeImage(source) {
  if (!Buffer.isBuffer(source) || !source.length || source.length > 16 * 1024 * 1024) throw new Error('invalid-image');
  const image = sharp(source, {limitInputPixels: 1024 * 1536, animated: false});
  const metadata = await image.metadata();
  if (!['jpeg', 'png', 'webp'].includes(metadata.format) || metadata.width !== 1024 || metadata.height !== 1536
      || (metadata.pages || 1) !== 1) throw new Error('invalid-image');
  return png(await image.png().toBuffer());
}
function generationError(state, stage, httpStatus = null, apiCodes = [], apiSignals = []) {
  const diagnostic = {stage, httpStatus, apiCodes, apiSignals};
  // Não registrar resposta bruta, mensagens externas, prompt, conta ou token.
  if (process.env.FUNCTIONS_EMULATOR === 'true') console.warn('CloudflareGeneration', JSON.stringify(diagnostic));
  return Object.assign(new Error(state), {diagnostic});
}
async function generate(input) {
  // Não despachar parâmetros antigos ou escolhidos pelo cliente.
  if (input.model !== POLICY.model || input.size !== POLICY.size || input.n !== 1
      || input.quality !== POLICY.quality || input.output_format !== 'png'
      || typeof input.prompt !== 'string' || !input.prompt.trim() || Buffer.byteLength(input.prompt, 'utf8') > 12000) {
    throw generationError('generation-failed', 'input-validation');
  }
  const endpoint = cloudflareEndpoint(`run/${POLICY.model}`);
  const form = new FormData();
  form.set('prompt', input.prompt); form.set('width', '1024'); form.set('height', '1536');
  let result;
  try {
    result = await fetch(endpoint, {
      method: 'POST', redirect: 'error', headers: {Authorization: `Bearer ${process.env.CLOUDFLARE_API_KEY}`},
      body: form, signal: AbortSignal.timeout(180000)});
  } catch (_) { throw generationError('generation-unknown', 'transport'); }
  if (!result.ok) {
    let apiCodes = [], apiSignals = [];
    try {
      const body = await result.json();
      if (Array.isArray(body.errors)) {
        const errors = body.errors.slice(0, 8);
        apiCodes = errors.map(error => error?.code).filter(Number.isSafeInteger);
        // Sinais de categorias fixas; nunca retornar/registrar a mensagem externa.
        const signals = new Set();
        for (const error of errors) {
          if (typeof error?.message !== 'string') continue;
          const message = error.message.slice(0, 10000).toLowerCase();
          if (/nsfw|content.?filter|output has been flagged|unsafe content/.test(message)) signals.add('content_filter');
          if (/validat|invalid input|missing required|multipart|boundary/.test(message)) {
            signals.add('input_validation');
            for (const field of ['prompt','width','height','multipart','boundary']) if (message.includes(field)) signals.add('field_' + field);
          }
          if (/paid plan|billing|payment|quota|allocation|rate limit/.test(message)) signals.add('plan_or_quota');
          if (/unauthori|permission|forbidden|access denied/.test(message)) signals.add('access');
        }
        apiSignals = [...signals];
      }
    } catch (_) { /* HTTP permanece útil mesmo se a resposta não for JSON. */ }
    throw generationError(result.status >= 400 && result.status < 500 ? 'generation-failed' : 'generation-unknown',
      'http-response', result.status, apiCodes, apiSignals);
  }
  try {
    const data = await result.json();
    const encoded = data.result?.image;
    if (data.success !== true || typeof encoded !== 'string' || !encoded.length || encoded.length > 24 * 1024 * 1024
        || !/^[A-Za-z0-9+/]*={0,2}$/.test(encoded)) throw new Error();
    const source = Buffer.from(encoded, 'base64');
    if (source.length > 16 * 1024 * 1024 || source.toString('base64') !== encoded) throw new Error();
    // O provedor não promete PNG: validar dimensões antes de normalizar para o contrato existente.
    const bytes = await normalizeImage(source);
    return {bytes, usage: {}, requestId: result.headers.get('cf-ray') || null};
  } catch (_) { throw generationError('generation-unknown', 'image-response', result.status); }
}
function publicId(uid, id) { return `marvel-local/${createHash('sha256').update(uid).digest('hex')}/${id}`; }
function assetReference(asset,expected) {
  if (asset.public_id !== expected || asset.type !== 'authenticated' || asset.resource_type !== 'image'
      || asset.format !== 'png' || asset.width !== 1024 || asset.height !== 1536 || typeof asset.asset_id !== 'string') throw new Error('upload-failed');
  return {publicId: expected, assetId: asset.asset_id, version: asset.version, format: 'png', resourceType: 'image', type: 'authenticated'};
}
async function recoverUpload(uid,id) {
  const config = configuration(false), expected = publicId(uid,id);
  try {
    const asset = await cloudinary.api.resource(expected,{...config,resource_type:'image',type:'authenticated',timeout:20000});
    return assetReference(asset,expected);
  } catch (error) {
    if (error.http_code === 404) return null;
    throw new Error('upload-failed');
  }
}
async function upload(bytes, uid, id) {
  const config = configuration(false), expected = publicId(uid,id);
  let asset;
  try {
    asset = await new Promise((resolve,reject) => {
      const stream = cloudinary.uploader.upload_stream({...config, resource_type: 'image', type: 'authenticated',
        public_id: expected, overwrite: false, timeout: 60000, disable_promises: true}, (error,result) => error ? reject(new Error()) : resolve(result));
      stream.on('error', () => reject(new Error())); stream.end(bytes);
    });
    if (!asset.asset_id || asset.existing) asset = await cloudinary.api.resource(expected, {...config, resource_type: 'image', type: 'authenticated', timeout: 20000});
    return assetReference(asset,expected);
  } catch (_) { throw new Error('upload-failed'); }
}
function downloadUrl(asset) {
  const config = configuration(false), expiresAt = Math.floor(Date.now()/1000) + 300;
  return {url: cloudinary.utils.private_download_url(asset.publicId, 'png', {...config, resource_type: 'image', type: 'authenticated', expires_at: expiresAt}), expiresAt};
}
module.exports = {cloudflareConfigured, configuration, verifyModelAccess, generate, normalizeImage, png, publicId, upload, recoverUpload, downloadUrl};
