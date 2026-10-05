'use strict';
const {randomBytes} = require('node:crypto');
function host(value,fallback) {
  const selected=value||fallback;
  if(!/^(127\.0\.0\.1|localhost):\d+$/.test(selected))throw new Error('Use somente endereços dos emuladores locais.');
  return selected;
}
async function localStatus(connections=false) {
  const authHost=host(process.env.FIREBASE_AUTH_EMULATOR_HOST,'127.0.0.1:9099');
  const functionsHost=host(process.env.FUNCTIONS_EMULATOR_HOST,'127.0.0.1:5001');
  const authRoot=`http://${authHost}/identitytoolkit.googleapis.com/v1/accounts:`;
  async function post(url,data,token) {
    let response;
    try {response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},body:JSON.stringify(data),redirect:'error',signal:AbortSignal.timeout(65000)});}
    catch(_){throw new Error('Emuladores indisponíveis. Inicie o ambiente local antes de verificar.');}
    if(!response.ok)throw new Error('Não foi possível concluir o diagnóstico local.');
    return response.json();
  }
  let token;
  try {
    const user=await post(authRoot+'signUp?key=demo-local',{email:`provider-check-${randomBytes(12).toString('hex')}@example.test`,password:randomBytes(24).toString('hex'),returnSecureToken:true});
    token=user.idToken;
    const value=await post(`http://${functionsHost}/demo-marvel-local/us-central1/providerConfigurationCheck`,{data:{checkConnections:connections}},token);
    const report=value.result;
    if(!report||report.uid!==user.localId||report.localEnvironment!==true||report.generatedImage!==false)throw new Error('Resposta do diagnóstico local inválida.');
    // Saída explicitamente restrita; não imprime token, conta temporária, cloud name ou resposta bruta.
    return {localEnvironment:true,generationEnabled:report.generationEnabled,comicVineConfigured:report.comicVineConfigured,
      firestoreConfigured:report.firestoreConfigured,storageConfigured:report.storageConfigured,model:report.model,
      openai:{configured:report.openai.configured,connection:report.openai.connection},
      cloudinary:{configured:report.cloudinary.configured,connection:report.cloudinary.connection},
      connectionsRequested:report.connectionsRequested,configurationComplete:report.configurationComplete,
      firstRealAttemptPending:true,generatedImage:false};
  } finally {
    if(token)await post(authRoot+'delete?key=demo-local',{idToken:token}).catch(()=>{throw new Error('Diagnóstico terminou, mas a conta temporária não foi removida.');});
  }
}
if(require.main===module){
  const args=process.argv.slice(2);
  if(args.some(arg=>arg!=='--connections')){console.error('Use sem opções ou apenas --connections.');process.exitCode=1;}
  else localStatus(args.includes('--connections')).then(report=>console.log(JSON.stringify(report,null,2))).catch(error=>{console.error(error.message);process.exitCode=1;});
}
module.exports={localStatus};
