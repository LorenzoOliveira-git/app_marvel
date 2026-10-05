'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {randomUUID} = require('node:crypto');
const project = 'demo-marvel-local';
for (const name of ['FIREBASE_AUTH_EMULATOR_HOST', 'FIRESTORE_EMULATOR_HOST']) assert.match(process.env[name] || '', /^(127\.0\.0\.1|localhost):\d+$/);
async function request(url, token, method = 'GET', body) {
  return fetch(url, {method, headers: {'Content-Type':'application/json', ...(token ? {Authorization:`Bearer ${token}`} : {})}, body: body ? JSON.stringify(body) : undefined, signal: AbortSignal.timeout(30000)});
}
async function user(label) {
  const result = await request(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-local`, null, 'POST', {email:`${label}-${Date.now()}@example.test`,password:'local-emulator-only-928374',returnSecureToken:true});
  assert.equal(result.status,200); return result.json();
}
(async () => {
  const source = await require('../backend/hero-drafts.cjs').catalogs();
  const owner = await user('draft-owner'), other = await user('draft-other');
  const data = {draftId:randomUUID(),heroName:'Rascunho de integração',realName:'Nome de teste',description:'Dados de diagnóstico local, sem criação de imagem.',originId:source.origins[0].id,powerIds:[source.powers[0].id]};
  const endpoint = `http://127.0.0.1:5001/${project}/us-central1/saveHeroDraft`;
  async function call(value,token=owner.idToken){return request(endpoint,token,'POST',{data:value});}
  assert.equal((await call(data,null)).status,401);
  for (const invalid of [{...data,uid:other.localId},{...data,description:''},{...data,birthday:'2026-02-30'},{...data,powerIds:[-1]},{...data,heroName:'x'.repeat(101)},{...data,originId:2147483647}]) assert.equal((await call(invalid)).status,400);
  const first = await call(data); assert.equal(first.status,200);
  assert.deepEqual((await first.json()).result,{uid:owner.localId,draftId:data.draftId,state:'draft',generatedImage:false});
  const base = `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/users/${owner.localId}/heroDrafts`;
  const document = `${base}/${data.draftId}`, before = await (await request(document,owner.idToken)).json();
  const repeats = await Promise.all([call(data),call(data),call(data)]); assert.ok(repeats.every(response => response.status===200));
  assert.deepEqual((await (await request(document,owner.idToken)).json()).fields.updatedAt,before.fields.updatedAt);
  for (const token of [null,other.idToken]) assert.equal((await request(document,token)).status,403);
  assert.equal((await request(document,owner.idToken,'PATCH',{fields:{state:{stringValue:'hero'}}})).status,403);
  assert.equal((await request(document,owner.idToken,'DELETE')).status,403);
  const edited = {...data,description:'Rascunho editado, continua sem imagem.',birthday:'2020-02-29'};
  assert.equal((await call(edited)).status,200);
  const saved = await (await request(document,owner.idToken)).json();
  assert.equal(saved.fields.description.stringValue,edited.description);
  assert.equal(saved.fields.birthday.stringValue,'2020-02-29');
  assert.equal(saved.fields.state.stringValue,'draft'); assert.equal(saved.fields.generatedImage.booleanValue,false);
  assert.equal((await (await request(base,owner.idToken)).json()).documents.length,1);
  fs.mkdirSync('firebase-local-check',{recursive:true});
  fs.writeFileSync('firebase-local-check/hero-drafts-backend.json',JSON.stringify({success:true,source:'ComicVine real',originId:data.originId,powerId:data.powerIds[0],ownerIsolation:true,serverValidation:true,idempotentConcurrentSaves:true,editsReuseDraft:true,generatedImage:false},null,2));
  console.log('Rascunhos: catálogo real, validação no backend, isolamento, duplicação e edição verificados.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
