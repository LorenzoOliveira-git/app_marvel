'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {randomBytes}=require('node:crypto');
async function post(url,data,token){return fetch(url,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(data),signal:AbortSignal.timeout(65000)});}
(async()=>{
 assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST||'',/^(127\.0\.0\.1|localhost):\d+$/);
 const auth=`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:`;
 const user=await (await post(auth+'signUp?key=demo-local',{email:`config-${randomBytes(12).toString('hex')}@example.test`,password:randomBytes(24).toString('hex'),returnSecureToken:true})).json();
 const url='http://127.0.0.1:5001/demo-marvel-local/us-central1/providerConfigurationCheck';
 try {
  assert.equal((await post(url,{data:{}},null)).status,401);
  for(const data of [{checkConnections:'true'},{uid:user.localId},{generate:true},{model:'other'}])assert.equal((await post(url,{data},user.idToken)).status,400);
  for(const checkConnections of [false,true]){
   const response=await post(url,{data:{checkConnections}},user.idToken);assert.equal(response.status,200);
   const report=(await response.json()).result;
   assert.equal(report.uid,user.localId);assert.equal(report.generationEnabled,false);assert.equal(report.configurationComplete,false);
   assert.deepEqual(report.openai,{configured:false,connection:'not_configured'});
   assert.deepEqual(report.cloudinary,{configured:false,connection:'not_configured'});
   assert.equal(report.generatedImage,false);assert.equal(report.firstRealAttemptPending,true);
   assert.deepEqual(Object.keys(report).sort(),['uid','localEnvironment','generationEnabled','comicVineConfigured','firestoreConfigured','storageConfigured','model','openai','cloudinary','connectionsRequested','firstRealAttemptPending','generatedImage','configurationComplete'].sort());
  }
  const cli=await require('./provider-status.cjs').localStatus(false);assert.equal(cli.generatedImage,false);assert.ok(!('uid' in cli));

 }finally{assert.equal((await post(auth+'delete?key=demo-local',{idToken:user.idToken})).status,200);}
  fs.mkdirSync('firebase-local-check',{recursive:true});fs.writeFileSync('firebase-local-check/provider-configuration.json',JSON.stringify({success:true,authRequired:true,unknownFieldsDenied:true,missingConfigurationReported:true,connectionsSkippedWithoutKeys:true,cliActualCallable:true,temporaryAccountsDeleted:true,generatedImage:false,externalProvidersNotExercised:true},null,2));
  console.log('Diagnóstico local autenticado e CLI verificados; nenhuma geração, upload ou credencial exposta.');
})().catch(()=>{console.error('Falha na integração real do diagnóstico local.');process.exitCode=1;});
