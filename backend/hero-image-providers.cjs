'use strict';
const {createHash} = require('node:crypto');
const {HttpsError} = require('firebase-functions/v2/https');
const cloudinary = require('cloudinary').v2;
function configuration(generation = true) {
  const env = process.env;
  if (generation && (env.HERO_GENERATION_ENABLED !== 'true' || !env.OPENAI_API_KEY)) {
    throw new HttpsError('failed-precondition', 'Configure e habilite a geração no ambiente privado do backend local.');
  }
  if (!/^[a-z0-9-]+$/.test(env.CLOUDINARY_CLOUD_NAME || '') || !env.CLOUDINARY_API_KEY || !env.CLOUDINARY_API_SECRET) {
    throw new HttpsError('failed-precondition', 'Configure o Cloudinary no ambiente privado do backend local.');
  }
  return {cloud_name: env.CLOUDINARY_CLOUD_NAME, api_key: env.CLOUDINARY_API_KEY, api_secret: env.CLOUDINARY_API_SECRET, secure: true};
}
async function verifyModelAccess() {
  try {
    const result = await fetch('https://api.openai.com/v1/models/gpt-image-2', {
      headers: {Authorization: `Bearer ${process.env.OPENAI_API_KEY}`}, redirect: 'error', signal: AbortSignal.timeout(20000)});
    if (!result.ok || (await result.json()).id !== 'gpt-image-2') throw new Error();
  } catch (_) { throw new HttpsError('failed-precondition', 'Não foi possível verificar o acesso desta conta a gpt-image-2. Nenhuma geração iniciada.'); }
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
async function generate(input) {
  let result;
  try {
    result = await fetch('https://api.openai.com/v1/images/generations', {
      method: 'POST', redirect: 'error', headers: {Authorization: `Bearer ${process.env.OPENAI_API_KEY}`, 'Content-Type': 'application/json'},
      body: JSON.stringify({model: input.model, quality: input.quality, n: input.n, size: input.size,
        output_format: input.output_format, prompt: input.prompt}), signal: AbortSignal.timeout(180000)});
  } catch (_) { throw new Error('generation-unknown'); }
  if (!result.ok) throw new Error(result.status >= 400 && result.status < 500 ? 'generation-failed' : 'generation-unknown');
  try {
    const data = await result.json();
    const encoded = data.data?.[0]?.b64_json;
    if (data.data?.length !== 1 || typeof encoded !== 'string' || encoded.length > 24 * 1024 * 1024
        || !/^[A-Za-z0-9+/]*={0,2}$/.test(encoded) || (data.quality && data.quality !== 'low')
        || (data.size && data.size !== '1024x1536')) throw new Error();
    const bytes = png(Buffer.from(encoded, 'base64'));
    const usage = {};
    for (const key of ['input_tokens','output_tokens','total_tokens']) {
      if (Number.isSafeInteger(data.usage?.[key]) && data.usage[key] >= 0) usage[key] = data.usage[key];
    }
    return {bytes, usage, requestId: result.headers.get('x-request-id') || null};
  } catch (_) { throw new Error('generation-unknown'); }
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
module.exports = {configuration, verifyModelAccess, generate, png, publicId, upload, recoverUpload, downloadUrl};
