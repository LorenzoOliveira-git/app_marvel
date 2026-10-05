'use strict';
// Serviços Firebase realmente emulados; reservas sem despachar a Cloudflare.
// Quotas nas bordas são configuradas administrativamente; sem resposta/imagem de provedor simulada.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const {randomUUID} = require('node:crypto');
const req = require('node:module').createRequire(require.resolve('../backend/package.json'));
const {initializeApp} = req('firebase-admin/app');
const {getFirestore,Timestamp} = req('firebase-admin/firestore');
const {getStorage} = req('firebase-admin/storage');
const project = 'demo-marvel-local';
for (const key of ['FIRESTORE_EMULATOR_HOST','FIREBASE_STORAGE_EMULATOR_HOST','FIREBASE_AUTH_EMULATOR_HOST']) assert.match(process.env[key] || '',/^(localhost|127\.0\.0\.1):\d+$/);
assert.notEqual(process.env.HERO_GENERATION_ENABLED,'true');
initializeApp({projectId: project});
const db = getFirestore(), core = require('../backend/hero-generation-state.cjs');
async function request(url,token,method='GET',body) {
  return fetch(url,{method,headers:{'Content-Type':'application/json',...(token ? {Authorization:`Bearer ${token}`} : {})},body:body ? JSON.stringify(body) : undefined,signal:AbortSignal.timeout(30000)});
}
async function user() {
  const result = await request(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-local`,null,'POST',
    {email:`generation-${randomUUID()}@example.test`,password:'local-emulator-only-928374',returnSecureToken:true});
  assert.equal(result.status,200);return result.json();
}
function call(name,data,token) {return request(`http://127.0.0.1:5001/${project}/us-central1/${name}`,token,'POST',{data});}
function document(uid,collection,id) {return `http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/users/${uid}/${collection}/${id}`;}
(async()=>{
  const [owner,other] = await Promise.all([user(),user()]);
  const source = await require('../backend/hero-drafts.cjs').catalogs();
  async function prepared(account=owner) {
    const draft = {draftId:randomUUID(),heroName:'Preparação de execução',realName:'Personagem de diagnóstico',description:'Integração de cotas locais, sem geração de imagem.',
      originId:source.origins[0].id,powerIds:[source.powers[0].id]};
    assert.equal((await call('saveHeroDraft',draft,account.idToken)).status,200);
    const saved = (await db.doc(`users/${account.localId}/heroDrafts/${draft.draftId}`).get()).data();
    const data = {draftId:draft.draftId,operationId:randomUUID(),contentHash:saved.contentHash};
    const result = await call('prepareHeroCreation',data,account.idToken); assert.equal(result.status,200);
    return (await result.json()).result;
  }
  const job = await prepared(), input={operationId:job.operationId,confirmPaidGeneration:true};
  assert.equal((await call('executeHeroCreation',input,null)).status,401);
  assert.equal((await call('executeHeroCreation',input,other.idToken)).status,404);
  assert.equal((await call('executeHeroCreation',{...input,uid:other.localId},owner.idToken)).status,400);
  assert.equal((await call('executeHeroCreation',{operationId:job.operationId},owner.idToken)).status,400);
  // Desativada: nenhuma reserva nem requisição externa, mesmo com confirmação no payload.
  assert.equal((await call('executeHeroCreation',input,owner.idToken)).status,400);
  assert.equal((await db.doc(`users/${owner.localId}/heroCreationJobs/${job.operationId}`).get()).get('state'),'prepared');
  const {day,month}=core.periods(), ledger=db.doc(`heroGenerationUsage/${month}`), daily=db.doc(`users/${owner.localId}/heroGenerationDays/${day}`);
  assert.equal((await ledger.get()).exists,false);
  const attempts=await Promise.all([core.reserveAttempt(owner.localId,job.operationId),core.reserveAttempt(owner.localId,job.operationId),core.reserveAttempt(owner.localId,job.operationId)]);
  assert.equal(attempts.filter(value=>value.claimed).length,1);
  assert.equal((await ledger.get()).get('attempts'),1);
  assert.equal((await ledger.get()).get('reservedUsdMicros'),50000);
  assert.equal((await daily.get()).get('attempts'),1);
  // Chamada pública repetida devolve o estado, sem despachar a geração.
  const duplicate=await call('executeHeroCreation',input,owner.idToken); assert.equal(duplicate.status,200);
  assert.equal((await duplicate.json()).result.state,'generating');
  const activeResume=await call('resumeHeroCreation',{operationId:job.operationId},owner.idToken);assert.equal(activeResume.status,200);
  assert.equal((await activeResume.json()).result.state,'generating');
  const more=await Promise.all([prepared(),prepared(),prepared()]);
  const quota=await Promise.allSettled(more.map(value=>core.reserveAttempt(owner.localId,value.operationId)));
  assert.equal(quota.filter(value=>value.status==='fulfilled').length,2);
  assert.equal(quota.filter(value=>value.status==='rejected' && value.reason.code==='resource-exhausted').length,1);
  assert.equal((await daily.get()).get('attempts'),3);
  assert.equal((await ledger.get()).get('attempts'),3);
  assert.equal((await ledger.get()).get('reservedUsdMicros'),150000);
  for(const url of [document(owner.localId,'heroGenerationDays',day),`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/heroGenerationUsage/${month}`]) {
    assert.equal((await request(url,owner.idToken)).status,403);
  }
  const jobs=await Promise.all([prepared(other),prepared(other),prepared(other)]);
  await ledger.set({attempts:99,reservedUsdMicros:4950000,blocked:false}); // borda mensal real no emulador
  const finalSlot=await Promise.allSettled(jobs.slice(0,2).map(value=>core.reserveAttempt(other.localId,value.operationId)));
  assert.equal(finalSlot.filter(value=>value.status==='fulfilled').length,1);
  assert.equal((await ledger.get()).get('attempts'),100);assert.equal((await ledger.get()).get('reservedUsdMicros'),5000000);
  await ledger.set({attempts:0,reservedUsdMicros:4975000,blocked:false}); // orçamento independente da cota
  await assert.rejects(core.reserveAttempt(other.localId,jobs[2].operationId),{code:'resource-exhausted'});
  assert.equal((await ledger.get()).get('reservedUsdMicros'),4975000);
  // Execução interrompida sem arquivo: retomada marca desconhecido e não reserva/gera novamente.
  const jobRef=db.doc(`users/${owner.localId}/heroCreationJobs/${job.operationId}`);
  await jobRef.update({leaseUntil:Timestamp.fromMillis(0)});
  const resumed=await call('resumeHeroCreation',{operationId:job.operationId},owner.idToken);assert.equal(resumed.status,200);
  assert.equal((await resumed.json()).result.state,'generation_unknown');
  assert.equal((await ledger.get()).get('reservedUsdMicros'),4975000);
  const retryData={previousOperationId:job.operationId,operationId:randomUUID(),confirmNewPaidAttempt:true};
  assert.equal((await call('retryHeroGeneration',{...retryData,confirmNewPaidAttempt:false},owner.idToken)).status,400);
  const retry=await call('retryHeroGeneration',retryData,owner.idToken);assert.equal(retry.status,200);
  assert.equal((await retry.json()).result.state,'prepared');
  assert.equal((await call('retryHeroGeneration',retryData,owner.idToken)).status,200);
  assert.equal((await jobRef.get()).get('state'),'superseded');
  assert.equal((await ledger.get()).get('reservedUsdMicros'),4975000);
  assert.equal((await call('heroImageUrl',{operationId:job.operationId},owner.idToken)).status,404);
  // Objeto administrativo de diagnóstico: testa bloqueio de acesso, sem fingir imagem gerada.
  const file=getStorage().bucket(`${project}.appspot.com`).file(`heroPending/${owner.localId}/${randomUUID()}.txt`);
  await file.save('storage-owner-isolation-diagnostic',{resumable:false,contentType:'text/plain'});
  const url=`http://${process.env.FIREBASE_STORAGE_EMULATOR_HOST}/v0/b/${project}.appspot.com/o/${encodeURIComponent(file.name)}?alt=media`;
  for(const token of [null,owner.idToken,other.idToken]) assert.ok([401,403].includes((await request(url,token)).status));
  await file.delete();
  const expiredId=randomUUID(), expired=getStorage().bucket(`${project}.appspot.com`).file(`heroPending/${owner.localId}/${expiredId}.png`);
  // Texto de diagnóstico de retenção, sem simular PNG ou sucesso de geração.
  await expired.save('expired-retention-diagnostic',{resumable:false,contentType:'text/plain',metadata:{metadata:{
    uid:owner.localId,operationId:expiredId,expiresAtMs:String(Date.now()-1000)}}});
  assert.equal((await require('../backend/hero-temporary-cleanup.cjs').cleanupExpiredTemporaryImages()).deleted,1);
  assert.equal((await expired.exists())[0],false);
  assert.equal((await db.collection(`users/${owner.localId}/heroes`).get()).size,0);
  fs.mkdirSync('firebase-local-check',{recursive:true});
  fs.writeFileSync('firebase-local-check/hero-generation.json',JSON.stringify({success:true,paidExecutionDisabled:true,source:'ComicVine real',
    transactionalDuplicateReservation:true,dailyLimit:true,monthlyLimit:true,budgetLimit:true,concurrentLastSlot:true,
    interruptedAttemptDoesNotRegenerate:true,explicitRetry:true,privateLedger:true,privateTemporaryStorage:true,expiredTemporaryCleanup:true,
    generatedImage:false,externalProvidersNotExercised:true},null,2));
  console.log('Execução local: reserva concorrente, cotas/orçamento, bloqueio pago, recuperação sem regeneração e regras verificadas.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
