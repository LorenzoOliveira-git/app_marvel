'use strict';
// Integração real com Auth/Functions/Firestore emulados e catálogo ComicVine real.
// Nenhuma chamada OpenAI/Cloudinary, mock ou teste unitário.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {randomUUID} = require('node:crypto');
const project = 'demo-marvel-local';
for (const key of ['FIREBASE_AUTH_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST']) assert.match(process.env[key] || '', /^(127\.0\.0\.1|localhost):\d+$/);
async function request(url, token, method = 'GET', body) {
  return fetch(url, {method, headers: {'Content-Type': 'application/json', ...(token ? {Authorization: `Bearer ${token}`} : {})},
    body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000)});
}
async function user(label) {
  const result = await request(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-local`, null, 'POST',
    {email: `${label}-${randomUUID()}@example.test`, password: 'local-emulator-only-928374', returnSecureToken: true});
  assert.equal(result.status, 200); return result.json();
}
async function call(name, data, token) {
  return request(`http://127.0.0.1:5001/${project}/us-central1/${name}`, token, 'POST', {data});
}
function document(uid, collection, id) {
  return `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/users/${uid}/${collection}/${id}`;
}
(async () => {
  const [owner, other] = await Promise.all([user('creation-owner'), user('creation-other')]);
  const source = await require('../backend/hero-drafts.cjs').catalogs();
  const draft = {draftId: randomUUID(), heroName: 'Herói de integração', realName: 'Nome do personagem',
    description: 'Descrição para verificar a preparação local, sem criar imagem.',
    originId: source.origins[0].id, powerIds: [source.powers[0].id]};
  assert.equal((await call('saveHeroDraft', draft, owner.idToken)).status, 200);
  const saved = await (await request(document(owner.localId, 'heroDrafts', draft.draftId), owner.idToken)).json();
  const data = {draftId: draft.draftId, operationId: randomUUID(), contentHash: saved.fields.contentHash.stringValue};
  const prepare = (value = data, token = owner.idToken) => call('prepareHeroCreation', value, token);
  assert.equal((await prepare(data, null)).status, 401);
  assert.equal((await prepare(data, other.idToken)).status, 404);
  for (const invalid of [{...data, uid: other.localId}, {...data, quality: 'high'}, {...data, model: 'other'},
    {...data, monthlyBudgetUsdMicros: 999999999}, {...data, contentHash: 'x'}, {...data, operationId: 1}]) {
    assert.equal((await prepare(invalid)).status, 400);
  }
  assert.equal((await prepare({...data, contentHash: '0'.repeat(64)})).status, 400);
  // IDs diferentes para o mesmo rascunho/versão convergem para uma única operação.
  const simultaneous = await Promise.all([prepare(), prepare({...data, operationId: randomUUID()}), prepare()]);
  assert.ok(simultaneous.every(result => result.status === 200));
  const results = await Promise.all(simultaneous.map(result => result.json()));
  const job = results[0].result;
  assert.ok(results.every(result => result.result.operationId === job.operationId));
  assert.equal(job.state, 'prepared'); assert.equal(job.generatedImage, false);
  assert.equal(job.policy.model, 'gpt-image-2'); assert.equal(job.policy.quality, 'low');
  assert.equal(job.policy.size, '1024x1536'); assert.equal(job.policy.n, 1);
  assert.equal(job.policy.monthlyBudgetUsdMicros, 5000000);
  assert.equal(job.policy.dailyAttemptsPerUser, 3); assert.equal(job.policy.monthlyAttemptsTotal, 100);
  const jobUrl = document(owner.localId, 'heroCreationJobs', job.operationId);
  const first = await (await request(jobUrl, owner.idToken)).json();
  assert.equal(first.fields.snapshot.mapValue.fields.description.stringValue, draft.description);
  const retry = await prepare({...data, operationId: job.operationId}); assert.equal(retry.status, 200);
  const repeated = await (await request(jobUrl, owner.idToken)).json();
  assert.deepEqual(repeated.fields.updatedAt, first.fields.updatedAt);
  for (const token of [null, other.idToken]) assert.equal((await request(jobUrl, token)).status, 403);
  for (const collection of ['heroCreationInputs', 'heroCreationClaims']) {
    const id = collection === 'heroCreationInputs' ? job.operationId : data.draftId;
    assert.equal((await request(document(owner.localId, collection, id), owner.idToken)).status, 403);
  }
  assert.equal((await request(jobUrl, owner.idToken, 'PATCH', {fields: {state: {stringValue: 'completed'}}})).status, 403);
  assert.equal((await request(jobUrl, owner.idToken, 'DELETE')).status, 403);
  const edited = {...draft, description: 'Nova versão revisável do mesmo personagem.', birthday: '2020-02-29'};
  assert.equal((await call('saveHeroDraft', edited, owner.idToken)).status, 200);
  const updated = await (await request(document(owner.localId, 'heroDrafts', draft.draftId), owner.idToken)).json();
  assert.equal((await prepare({...data, operationId: randomUUID()})).status, 400);
  assert.equal((await prepare({...data, operationId: job.operationId, contentHash: updated.fields.contentHash.stringValue})).status, 409);
  const next = await prepare({...data, operationId: randomUUID(), contentHash: updated.fields.contentHash.stringValue});
  assert.equal(next.status, 200);
  assert.equal((await (await request(jobUrl, owner.idToken)).json()).fields.state.stringValue, 'superseded');
  assert.equal((await (await request(jobUrl, owner.idToken)).json()).fields.snapshot.mapValue.fields.description.stringValue, draft.description);
  const newJob = (await next.json()).result;
  const newSaved = await (await request(document(owner.localId, 'heroCreationJobs', newJob.operationId), owner.idToken)).json();
  assert.equal(newSaved.fields.snapshot.mapValue.fields.birthday.stringValue, '2020-02-29');
  const base = jobUrl.slice(0, jobUrl.lastIndexOf('/'));
  assert.equal((await (await request(base, owner.idToken)).json()).documents.length, 2);
  // Conferência administrativa no emulador: prompt real, política privada e sem lançamento financeiro.
  const backendRequire = require('node:module').createRequire(require.resolve('../backend/package.json'));
  const {initializeApp} = backendRequire('firebase-admin/app');
  const {getFirestore} = backendRequire('firebase-admin/firestore');
  initializeApp({projectId: project});
  const db = getFirestore();
  const privateInput = (await db.doc(`users/${owner.localId}/heroCreationInputs/${job.operationId}`).get()).data();
  assert.ok(privateInput.prompt.includes('composição vertical 2:3'));
  assert.ok(privateInput.prompt.includes(JSON.stringify(first.fields.snapshot.mapValue.fields.description.stringValue)));
  assert.ok(!privateInput.prompt.includes('"birthday"'));
  assert.equal((await db.collection('heroGenerationUsage').get()).size, 0);
  assert.equal((await db.collection(`users/${owner.localId}/heroes`).get()).size, 0);
  fs.mkdirSync('firebase-local-check', {recursive: true});
  fs.writeFileSync('firebase-local-check/hero-preparation.json', JSON.stringify({success: true, source: 'ComicVine real',
    concurrentDeduplication: true, immutableSnapshot: true, staleDraftRejected: true, operationReuseRejected: true,
    ownerIsolation: true, privatePrompt: true, approvedPolicy: true, supersededPreparedVersion: true,
    generatedImage: false, providerCalls: 0}, null, 2));
  console.log('Preparação: versão imutável, concorrência, titularidade, prompt privado e política aprovada verificados.');
})().catch(error => {console.error(error.message); process.exitCode = 1;});
