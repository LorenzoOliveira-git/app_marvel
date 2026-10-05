'use strict';
const {createHash} = require('node:crypto');
const {getStorage} = require('firebase-admin/storage');
const {Timestamp, FieldValue} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');
const state = require('./hero-generation-state.cjs');
const provider = require('./hero-image-providers.cjs');
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function identity(request) {
  if (process.env.FUNCTIONS_EMULATOR !== 'true' || process.env.GCLOUD_PROJECT !== 'demo-marvel-local'
      || ['FIRESTORE_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST']
        .some(key => !/^(127\.0\.0\.1|localhost):\d+$/.test(process.env[key] || ''))) {
    throw new HttpsError('failed-precondition', 'Execução disponível somente nos emuladores demo locais.');
  }
  if (!request.auth) throw new HttpsError('unauthenticated', 'Entre na sua conta local.');
  return request.auth.uid;
}
function parse(data, keys = ['operationId']) {
  if (!data || typeof data !== 'object' || Array.isArray(data) || Object.keys(data).some(key => !keys.includes(key))) throw new HttpsError('invalid-argument', 'Pedido inválido.');
  for (const key of keys.filter(key => key.endsWith('Id'))) {
    if (typeof data[key] !== 'string' || !UUID.test(data[key])) throw new HttpsError('invalid-argument', 'Identificador inválido.');
  }
  return Object.fromEntries(Object.entries(data).map(([key,value]) => [key,key.endsWith('Id') ? value.toLowerCase() : value]));
}
function result(job) {
  return {uid: job.uid, operationId: job.operationId, state: job.state, generatedImage: job.generatedImage === true,
    ...(job.heroId ? {heroId: job.heroId} : {})};
}
function temporary(uid,id) { return getStorage().bucket('demo-marvel-local.appspot.com').file(`heroPending/${uid}/${id}.png`); }
async function saveImage(uid,id,job,generated) {
  const now = Date.now();
  await temporary(uid,id).save(generated.bytes, {resumable: false, contentType: 'image/png',
    preconditionOpts: {ifGenerationMatch: 0}, metadata: {cacheControl: 'private, no-store', metadata: {
      uid, operationId: id, contentHash: job.contentHash, imageSha256: createHash('sha256').update(generated.bytes).digest('hex'),
      expiresAtMs: String(now + 7 * 86400000), usage: JSON.stringify(generated.usage), requestId: generated.requestId || ''}}});
}
async function storedImage(uid,id,job) {
  const file = temporary(uid,id);
  if (!(await file.exists())[0]) return null;
  const [metadata] = await file.getMetadata(), custom = metadata.metadata || {};
  if (custom.uid !== uid || custom.operationId !== id || custom.contentHash !== job.contentHash
      || !Number.isFinite(Number(custom.expiresAtMs))) throw new Error('invalid-image');
  if (Number(custom.expiresAtMs) <= Date.now()) { await file.delete(); throw new Error('expired-image'); }
  const [bytes] = await file.download();
  provider.png(bytes);
  if (createHash('sha256').update(bytes).digest('hex') !== custom.imageSha256) throw new Error('invalid-image');
  return bytes;
}
async function releaseFailure(uid,id,lease,nextState) {
  return state.transition(uid,id,lease,{state: nextState, leaseUntil: Timestamp.fromMillis(0)});
}
async function finish(uid,id,lease,job,bytes) {
  let asset = job.cloudinary;
  if (!asset) {
    await state.transition(uid,id,lease,{state: 'uploading', generatedImage: true});
    try { asset = await provider.upload(bytes,uid,id); }
    catch (_) { return result(await releaseFailure(uid,id,lease,'upload_failed')); }
  }
  try {
    await state.transition(uid,id,lease,{state: 'saving', generatedImage: true, cloudinary: asset});
    const completed = await state.complete(uid,id,lease,asset);
    // Limpeza não reverte sucesso nem provoca outra geração.
    try { await temporary(uid,id).delete({ignoreNotFound: true}); } catch (_) { /* manutenção local */ }
    return result(completed);
  } catch (_) { return result(await releaseFailure(uid,id,lease,'save_failed')); }
}
async function executeHeroCreation(request) {
  const uid = identity(request), data = parse(request.data,['operationId','confirmPaidGeneration']);
  const r = state.refs(uid,data.operationId), job = state.owner(await r.job.get(),uid);
  if (job.state !== 'prepared') return result(job); // Duplicação nunca repete chamada à Cloudflare.
  if (data.confirmPaidGeneration !== true) throw new HttpsError('failed-precondition', 'Confirme esta tentativa paga antes de gerar.');
  provider.configuration();
  await provider.verifyModelAccess(); // Consulta sem geração; não comprova faturamento/permissão de gerar.
  const reservation = await state.reserveAttempt(uid,data.operationId);
  if (!reservation.claimed) return result(reservation.job);
  const {lease} = reservation;
  let generated;
  try { generated = await provider.generate(reservation.input); }
  catch (error) {
    return result(await releaseFailure(uid,data.operationId,lease,error.message === 'generation-failed' ? 'generation_failed' : 'generation_unknown'));
  }
  try {
    await state.transition(uid,data.operationId,lease,{state: 'storing'});
    await saveImage(uid,data.operationId,reservation.job,generated);
    await state.transition(uid,data.operationId,lease,{state: 'image_stored', generatedImage: true,
      observedUsage: generated.usage, providerRequestId: generated.requestId});
  } catch (_) { return result(await releaseFailure(uid,data.operationId,lease,'generation_unknown')); }
  return finish(uid,data.operationId,lease,reservation.job,generated.bytes);
}
async function resumeHeroCreation(request) {
  const uid = identity(request), {operationId: id} = parse(request.data);
  const claim = await state.claimResume(uid,id);
  if (!claim.claimed) return result(claim.job);
  const {lease,job} = claim;
  if (job.cloudinary) return finish(uid,id,lease,job,null);
  // Upload pode ter sido aceito antes da gravação da resposta/queda do processo.
  if (['uploading','upload_failed','saving','save_failed'].includes(job.state)) {
    try {
      const asset = await provider.recoverUpload(uid,id);
      if (asset) return finish(uid,id,lease,{...job,cloudinary:asset},null);
    } catch (_) { return result(await releaseFailure(uid,id,lease,'upload_failed')); }
  }
  let bytes;
  try { bytes = await storedImage(uid,id,job); }
  catch (error) { return result(await releaseFailure(uid,id,lease,error.message === 'expired-image' ? 'image_expired' : 'generation_unknown')); }
  if (!bytes) return result(await releaseFailure(uid,id,lease,'generation_unknown'));
  // Não existe chamada a generate nesta função, nem nova reserva financeira.
  return finish(uid,id,lease,job,bytes);
}
async function retryHeroGeneration(request) {
  const uid = identity(request), data = parse(request.data,['previousOperationId','operationId','confirmNewPaidAttempt']);
  if (data.confirmNewPaidAttempt !== true || data.operationId === data.previousOperationId) throw new HttpsError('failed-precondition', 'Confirme uma nova tentativa com outra identidade.');
  const previous = state.refs(uid,data.previousOperationId), next = state.refs(uid,data.operationId);
  const existingRetry = await next.job.get();
  if (existingRetry.exists) {
    const saved = state.owner(existingRetry,uid);
    if (saved.retryOf !== data.previousOperationId) throw new HttpsError('already-exists', 'Identidade de operação já utilizada.');
    return result(saved);
  }
  const oldJob = state.owner(await previous.job.get(),uid);
  if (!['generation_failed','generation_unknown','image_expired'].includes(oldJob.state)) throw new HttpsError('failed-precondition', 'Retome o envio da mesma imagem ou aguarde a operação atual.');
  // Uma imagem recuperável precisa ser retomada; não desperdiçar gerando outra.
  if ((await temporary(uid,data.previousOperationId).exists())[0]) throw new HttpsError('failed-precondition', 'Retome a imagem existente antes de solicitar outra geração.');
  return previous.db.runTransaction(async tx => {
    const [doc, input, existing, draft, claim] = await tx.getAll(previous.job, previous.input, next.job,
      previous.db.doc(`users/${uid}/heroDrafts/${oldJob.draftId}`), previous.claim(oldJob.draftId));
    const job = state.owner(doc,uid);
    if (existing.exists) {
      const saved = state.owner(existing,uid);
      if (saved.retryOf !== data.previousOperationId) throw new HttpsError('already-exists', 'Identidade de operação já utilizada.');
      return result(saved);
    }
    if (!['generation_failed','generation_unknown','image_expired'].includes(job.state) || job.leaseUntil?.toMillis() > Date.now()
        || !input.exists || !draft.exists || draft.get('contentHash') !== job.contentHash
        || !claim.exists || claim.get('operationId') !== data.previousOperationId) throw new HttpsError('failed-precondition', 'Revise o rascunho atual antes de tentar novamente.');
    const prepared = {uid, draftId: job.draftId, operationId: data.operationId, contentHash: job.contentHash,
      state: 'prepared', generatedImage: false, snapshot: job.snapshot, policy: job.policy, schemaVersion: 1,
      retryOf: data.previousOperationId, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()};
    tx.create(next.job,prepared);
    tx.create(next.input,{...input.data(),operationId: data.operationId});
    tx.set(previous.claim(job.draftId),{operationId: data.operationId,contentHash: job.contentHash});
    tx.update(previous.job,{state: 'superseded',updatedAt: FieldValue.serverTimestamp()});
    return result(prepared);
  });
}
async function heroImageUrl(request) {
  const uid = identity(request), {operationId: id} = parse(request.data), r = state.refs(uid,id);
  const doc = await r.hero.get();
  if (!doc.exists || doc.get('uid') !== uid || doc.get('image.publicId') !== provider.publicId(uid,id)) throw new HttpsError('not-found', 'Imagem não encontrada na sua conta.');
  return provider.downloadUrl(doc.get('image'));
}
module.exports = {executeHeroCreation,resumeHeroCreation,retryHeroGeneration,heroImageUrl};
