'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const project = 'demo-marvel-local';
for (const name of ['FIREBASE_AUTH_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST', 'FIREBASE_STORAGE_EMULATOR_HOST']) {
  assert.match(process.env[name] || '', /^(127\.0\.0\.1|localhost):\d+$/, `Use somente emuladores locais: ${name}`);
}
async function request(url, token, method = 'GET', body, type = 'application/json') {
  return fetch(url, {method, headers: {'Content-Type': type, ...(token ? {Authorization: `Bearer ${token}`} : {})}, body: body == null ? undefined : typeof body === 'string' ? body : JSON.stringify(body), signal: AbortSignal.timeout(30000)});
}
async function user(label) {
  const result = await request(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-local`, null, 'POST', {email: `${label}-${Date.now()}@example.test`, password: 'local-emulator-only-928374', returnSecureToken: true});
  assert.equal(result.status, 200); return result.json();
}
(async () => {
  const owner = await user('owner'), other = await user('other');
  const callable = `http://127.0.0.1:5001/${project}/us-central1/localSessionCheck`;
  assert.equal((await request(callable, null, 'POST', {data: {}})).status, 401);
  const called = await request(callable, owner.idToken, 'POST', {data: {}});
  assert.equal(called.status, 200);
  assert.deepEqual((await called.json()).result, {uid: owner.localId, environment: 'emulator', generatedImage: false});
  const document = `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/users/${owner.localId}/localChecks/session`;
  assert.equal((await request(document, owner.idToken)).status, 200);
  for (const token of [null, other.idToken]) assert.equal((await request(document, token)).status, 403);
  assert.equal((await request(document, owner.idToken, 'PATCH', {fields: {uid: {stringValue: 'wrong'}}})).status, 403);
  const storage = `http://${process.env.FIREBASE_STORAGE_EMULATOR_HOST}/v0/b/${project}.appspot.com/o`;
  const name = `localChecks/${owner.localId}/session.txt`, object = `${storage}/${encodeURIComponent(name)}`;
  assert.equal((await request(`${storage}?uploadType=media&name=${encodeURIComponent(name)}`, owner.idToken, 'POST', 'local diagnostic', 'text/plain')).status, 200);
  assert.equal((await request(`${object}?alt=media`, owner.idToken)).status, 200);
  for (const token of [null, other.idToken]) assert.ok([401, 403].includes((await request(`${object}?alt=media`, token)).status));
  for (const [body, type] of [['bad type', 'image/png'], ['x'.repeat(1025), 'text/plain']]) {
    assert.ok([401, 403].includes((await request(`${storage}?uploadType=media&name=${encodeURIComponent(name + '-invalid')}`, owner.idToken, 'POST', body, type)).status));
  }
  assert.ok((await request(object, owner.idToken, 'DELETE')).ok);
  fs.mkdirSync('firebase-local-check', {recursive: true});
  fs.writeFileSync('firebase-local-check/backend.json', JSON.stringify({success: true, environment: 'emulator', authenticatedFunction: true, firestoreOwnerIsolation: true, storageOwnerIsolation: true, storageTypeAndSizeRules: true, generatedImage: false}, null, 2));
  console.log('Auth, Functions, Firestore e Storage: integração local e isolamento por usuário verificados.');
})().catch(error => {console.error(error.message); process.exitCode = 1;});
