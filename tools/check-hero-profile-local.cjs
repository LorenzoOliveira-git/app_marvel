'use strict';
const assert=require('node:assert/strict');
const fs=require('node:fs');
const {randomUUID}=require('node:crypto');
const project='demo-marvel-local';
assert.match(process.env.FIREBASE_AUTH_EMULATOR_HOST||'',/^(localhost|127\.0\.0\.1):\d+$/);
async function request(url,token,method='GET',body){return fetch(url,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:body?JSON.stringify(body):undefined,signal:AbortSignal.timeout(30000)});}
(async()=>{
 const owner=await (await request(`http://${process.env.FIREBASE_AUTH_EMULATOR_HOST}/identitytoolkit.googleapis.com/v1/accounts:signUp?key=demo-local`,null,'POST',{email:`profile-${Date.now()}@example.test`,password:'local-emulator-only-928374',returnSecureToken:true})).json();
 const data={heroId:randomUUID(),revision:0,heroName:'Nome editado',realName:'Identidade',description:'Edição textual sem criação de personagem de exemplo.'};
 const url=`http://127.0.0.1:5001/${project}/us-central1/updateHeroText`;
 const call=(data,token=owner.idToken)=>request(url,token,'POST',{data});
 assert.equal((await call(data,null)).status,401);
 for(const value of [{...data,image:{}},{...data,uid:owner.localId},{...data,description:''},{...data,heroName:'x'.repeat(101)},{...data,revision:-1},{...data,heroId:'../other'}])assert.equal((await call(value)).status,400);
 assert.equal((await call(data)).status,404);
 const base=`http://${process.env.FIRESTORE_EMULATOR_HOST}/v1/projects/${project}/databases/(default)/documents/users/${owner.localId}/heroes`;
 assert.equal((await request(`${base}/${data.heroId}`,owner.idToken,'PATCH',{fields:{heroName:{stringValue:'Registro proibido'}}})).status,403);
 assert.equal((await request(`${base}/${data.heroId}`,null)).status,403);
 const list=await request(base,owner.idToken);assert.equal(list.status,200);assert.equal(((await list.json()).documents||[]).length,0);
 fs.mkdirSync('firebase-local-check',{recursive:true});fs.writeFileSync('firebase-local-check/hero-profile-backend.json',JSON.stringify({success:true,authRequired:true,unknownFieldsDenied:true,inputValidated:true,missingHeroNotCreated:true,clientWritesDenied:true,actualCollectionEmpty:true,successfulEditNotExercised:true},null,2));
 console.log('Coleção vazia real, validação/auth e escrita direta bloqueada; nenhum herói de exemplo criado.');
})().catch(error=>{console.error(error.message);process.exitCode=1;});
