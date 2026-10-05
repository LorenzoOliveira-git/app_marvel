"""Formulário real → preparação → consentimento/cancelamento → bloqueio pago real → recuperação."""
import runpy
import json
import subprocess
import time
import urllib.request

# Uma passagem reaproveita os diagnósticos existentes, catálogos reais e a conta do fluxo nativo.
state = runpy.run_path('.github/scripts/check-hero-drafts-local.py')
tap, wait, adb, nodes, find, top = [state[key] for key in ('tap','wait','adb','nodes','find','top')]
identity, OUT, PACKAGE = [state[key] for key in ('identity','OUT','PACKAGE')]

def server(collection):
    url = f"http://127.0.0.1:8080/v1/projects/demo-marvel-local/databases/(default)/documents/users/{identity['localId']}/{collection}"
    request = urllib.request.Request(url, headers={'Authorization':'Bearer '+identity['idToken']})
    return json.load(urllib.request.urlopen(request, timeout=20)).get('documents', [])

tap(resource='hero_generate')
wait('button1', 'Confirmar e gerar', timeout=90)
message = find(nodes(), resource='message')
assert message is not None and 'US$ 0,05' in message.get('text') and '3 tentativas' in message.get('text')
jobs = server('heroCreationJobs'); assert len(jobs) == 1
operation = jobs[0]['name']; assert jobs[0]['fields']['state']['stringValue'] == 'prepared'
assert jobs[0]['fields']['generatedImage']['booleanValue'] is False
# Cancelar não despacha executeHeroCreation; a versão preparada é reutilizada.
tap(resource='button2'); wait('hero_creation_status','Personagem preparado')
assert not server('heroes')
tap(resource='hero_generate'); wait('button1','Confirmar e gerar')
tap(resource='button1'); wait('hero_creation_status','A geração não está disponível',timeout=90)
jobs = server('heroCreationJobs'); assert len(jobs) == 1 and jobs[0]['name'] == operation
assert jobs[0]['fields']['state']['stringValue'] == 'prepared'
assert jobs[0]['fields']['generatedImage']['booleanValue'] is False
top(); assert 'Guardiao Local' in wait('hero_review_data').get('text')
# Verificação da mesma operação, sem gerar nem trocar de UUID.
tap(resource='hero_resume_creation'); wait('hero_creation_status','Personagem preparado')
assert server('heroCreationJobs')[0]['name'] == operation
# Recuperação após encerramento do processo: estado vem do servidor, não de sucesso artificial/cache.
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-n',PACKAGE+'/.MainActivity')
tap(resource='createHeroFragment'); wait('hero_creation_status','Personagem preparado',timeout=90)
top(); review=wait('hero_review_data','Guardiao Local',timeout=90)
assert 'Pessoa de Teste' in review.get('text')
assert server('heroCreationJobs')[0]['name'] == operation
# Consentimento e falha legíveis com fonte ampliada, mantendo botões alcançáveis.
adb('shell','settings','put','system','font_scale','2.0'); time.sleep(2)
tap(resource='hero_generate'); wait('button1','Confirmar e gerar')
(OUT/'hero-generation-confirm-font200.png').write_bytes(adb('exec-out','screencap','-p').stdout)
tap(resource='button1'); wait('hero_creation_status','A geração não está disponível',timeout=90)
(OUT/'hero-generation-unavailable-font200.png').write_bytes(adb('exec-out','screencap','-p').stdout)
adb('shell','settings','put','system','font_scale','1.0')
assert not server('heroes')
# Leitura administrativa emulada confirma ausência de reserva/cobrança. Não imprime credenciais.
check = r"""
const req=require('node:module').createRequire(require.resolve('./backend/package.json'));
req('firebase-admin/app').initializeApp({projectId:'demo-marvel-local'});
req('firebase-admin/firestore').getFirestore().collection('heroGenerationUsage').get().then(snapshot=>{
 if(snapshot.size!==0) throw new Error('Reserva inesperada.');
 console.log('Sem reservas financeiras no fluxo desativado.');
}).catch(()=>{process.exitCode=1});
"""
subprocess.run(['node','-e',check],check=True)
(OUT/'hero-generation-android.json').write_text(json.dumps({'success':True,'nativeConsent':True,'cancellationDoesNotGenerate':True,
    'preparationConfirmedOnServer':True,'disabledGenerationShowsActualFailure':True,'sameOperationOnRetry':True,
    'processRestartRecoversServerJob':True,'fieldsPreserved':True,'font200':True,'generatedImage':False,
    'paidProvidersNotExercised':True},indent=2))
print('Android: confirmação/cancelamento, preparação real, bloqueio pago, identidade e recuperação de processo verificados.',flush=True)
