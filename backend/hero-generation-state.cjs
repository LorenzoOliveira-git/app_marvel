'use strict';
const {randomUUID} = require('node:crypto');
const {getFirestore, FieldValue, Timestamp} = require('firebase-admin/firestore');
const {HttpsError} = require('firebase-functions/v2/https');
const {POLICY} = require('./hero-creation.cjs');
const RESERVATION_USD_MICROS = 50000; // US$ 0,05 conservadores; não é preço/custo observado.
const LEASE_MS = 600000; // Maior que o timeout de execução (540s); nunca liberar durante execução viva.
function refs(uid, id) {
  const db = getFirestore(), root = `users/${uid}`;
  return {db, job: db.doc(`${root}/heroCreationJobs/${id}`), input: db.doc(`${root}/heroCreationInputs/${id}`),
    hero: db.doc(`${root}/heroes/${id}`), claim: draftId => db.doc(`${root}/heroCreationClaims/${draftId}`)};
}
function owner(doc, uid) {
  if (!doc.exists) throw new HttpsError('not-found', 'Operação não encontrada na sua conta.');
  const job = doc.data();
  if (job.uid !== uid) throw new HttpsError('permission-denied', 'Operação indisponível.');
  return job;
}
function periods() {
  const parts = new Intl.DateTimeFormat('en-CA', {timeZone: POLICY.timezone, year: 'numeric', month: '2-digit', day: '2-digit'}).formatToParts(new Date());
  const value = kind => parts.find(part => part.type === kind).value;
  const day = `${value('year')}-${value('month')}-${value('day')}`;
  return {day, month: day.slice(0,7)};
}
async function reserveAttempt(uid, id) {
  const r = refs(uid, id), {day, month} = periods(), lease = randomUUID();
  const daily = r.db.doc(`users/${uid}/heroGenerationDays/${day}`), monthly = r.db.doc(`heroGenerationUsage/${month}`);
  return r.db.runTransaction(async tx => {
    const [doc, input, dailyDoc, monthDoc] = await tx.getAll(r.job, r.input, daily, monthly);
    const job = owner(doc, uid);
    if (job.state !== 'prepared') return {claimed: false, job};
    const claim = await tx.get(r.claim(job.draftId));
    const draft = await tx.get(r.db.doc(`users/${uid}/heroDrafts/${job.draftId}`));
    if (!claim.exists || claim.get('operationId') !== id || !draft.exists || draft.get('uid') !== uid || draft.get('contentHash') !== job.contentHash) {
      throw new HttpsError('failed-precondition', 'O rascunho mudou ou esta operação foi substituída. Revise novamente.');
    }
    if (!input.exists || input.get('uid') !== uid || input.get('contentHash') !== job.contentHash
        || Object.entries(POLICY).some(([key,value]) => job.policy?.[key] !== value)
        || input.get('policyVersion') !== POLICY.version || input.get('model') !== POLICY.model
        || input.get('quality') !== POLICY.quality || input.get('n') !== POLICY.n
        || input.get('size') !== POLICY.size || input.get('output_format') !== POLICY.output_format
        || typeof input.get('prompt') !== 'string' || Buffer.byteLength(input.get('prompt'), 'utf8') > 12000) {
      throw new HttpsError('failed-precondition', 'Parâmetros ou tamanho do prompt indisponíveis para geração.');
    }
    const dailyAttempts = dailyDoc.get('attempts') || 0, attempts = monthDoc.get('attempts') || 0;
    const reserved = monthDoc.get('reservedUsdMicros') || 0;
    if (monthDoc.get('blocked') === true || dailyAttempts >= POLICY.dailyAttemptsPerUser || attempts >= POLICY.monthlyAttemptsTotal
        || reserved + RESERVATION_USD_MICROS > POLICY.monthlyBudgetUsdMicros) {
      throw new HttpsError('resource-exhausted', 'Limite diário, mensal ou de orçamento atingido.');
    }
    const billing = {month, day, reservedUsdMicros: RESERVATION_USD_MICROS, status: 'reserved_pending_reconciliation'};
    const leaseUntil = Timestamp.fromMillis(Date.now() + LEASE_MS);
    tx.set(daily, {attempts: dailyAttempts + 1, updatedAt: FieldValue.serverTimestamp()});
    tx.set(monthly, {attempts: attempts + 1, reservedUsdMicros: reserved + RESERVATION_USD_MICROS,
      blocked: false, updatedAt: FieldValue.serverTimestamp()}, {merge: true});
    tx.update(r.job, {state: 'generating', lease, leaseUntil, billing, updatedAt: FieldValue.serverTimestamp()});
    return {claimed: true, lease, input: input.data(), job: {...job, state: 'generating', billing, lease, leaseUntil}};
  });
}
async function transition(uid, id, lease, update) {
  const r = refs(uid,id);
  return r.db.runTransaction(async tx => {
    const job = owner(await tx.get(r.job), uid);
    if (job.lease !== lease || job.state === 'completed') throw new HttpsError('aborted', 'Operação já retomada ou concluída.');
    tx.update(r.job, {...update, updatedAt: FieldValue.serverTimestamp()});
    return {...job, ...update};
  });
}
async function claimResume(uid, id) {
  const r = refs(uid,id), lease = randomUUID();
  return r.db.runTransaction(async tx => {
    const job = owner(await tx.get(r.job), uid);
    if (job.state === 'completed') return {claimed: false, job};
    if (!['generating','storing','image_stored','uploading','upload_failed','saving','save_failed','generation_unknown'].includes(job.state)) {
      throw new HttpsError('failed-precondition', 'Esta operação não pode ser retomada.');
    }
    if (job.leaseUntil?.toMillis() > Date.now()) return {claimed: false, job};
    tx.update(r.job, {lease, leaseUntil: Timestamp.fromMillis(Date.now() + LEASE_MS), updatedAt: FieldValue.serverTimestamp()});
    return {claimed: true, lease, job: {...job, lease}};
  });
}
async function complete(uid, id, lease, asset) {
  const r = refs(uid,id);
  return r.db.runTransaction(async tx => {
    const [doc, existing] = await tx.getAll(r.job, r.hero), job = owner(doc, uid);
    if (job.state === 'completed') return job;
    if (job.lease !== lease || job.generatedImage !== true || job.state !== 'saving') throw new HttpsError('aborted', 'Etapa de salvamento indisponível.');
    if (existing.exists && (existing.get('uid') !== uid || existing.get('operationId') !== id)) throw new HttpsError('already-exists', 'Identidade do herói já utilizada.');
    if (!existing.exists) tx.create(r.hero, {uid, heroId: id, operationId: id, ...job.snapshot, image: asset,
      schemaVersion: 1, createdAt: FieldValue.serverTimestamp(), updatedAt: FieldValue.serverTimestamp()});
    tx.update(r.job, {state: 'completed', cloudinary: asset, heroId: id, leaseUntil: Timestamp.fromMillis(0), updatedAt: FieldValue.serverTimestamp()});
    return {...job, state: 'completed', heroId: id};
  });
}
module.exports = {refs, owner, reserveAttempt, claimResume, transition, complete, RESERVATION_USD_MICROS, periods};
