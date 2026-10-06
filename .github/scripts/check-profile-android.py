"""Perfil real emulado: gravação/releitura, validação, cancelamento, rede e fonte ampliada."""
from pathlib import Path
import subprocess
import time
import json
import urllib.request
helper=Path('.github/scripts/check-hero-drafts-local.py').read_text().split("subprocess.run(['python3'")[0]
helper=helper.replace("resource=='createHeroFragment'", "resource in ('createHeroFragment','profileFragment')")
exec(compile(helper,'profile-ui-helpers','exec'))
original_wait=wait
def wait(resource,text=None,timeout=75):
    try:return original_wait(resource,text,timeout)
    except RuntimeError:
        (OUT/'profile-ui-failure.png').write_bytes(adb('exec-out','screencap','-p').stdout)
        print('Tela no erro:',[(n.get('resource-id','').split('/')[-1],n.get('text','')) for n in nodes() if n.get('text')],flush=True)
        raise

def replace_name(value):
    tap(resource='profile_name')
    # Selecionar o conteúdo inteiro evita autocorreção durante centenas de DELs.
    adb('shell','input','keycombination','113','29')  # CTRL_LEFT + A
    adb('shell','input','keyevent','67')  # DEL
    if wait('profile_name').get('text','') != '':
        raise RuntimeError('Não foi possível esvaziar o campo de nome.')
    if value:adb('shell','input','text',value.replace(' ','%s'))
    adb('shell','input','keyevent','4');time.sleep(.3)
    if wait('profile_name').get('text','') != value:
        raise RuntimeError('O campo de nome não corresponde ao texto solicitado pelo roteiro.')

email=f'profile-{int(time.time())}@example.test';password='local-emulator-only-928374'
def server_name():
    # Token usado somente em memória para verificar a conta real; não imprime credenciais.
    root='http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/'
    def post(method,data):
        request=urllib.request.Request(root+'accounts:'+method+'?key=demo-local',data=json.dumps(data).encode(),headers={'Content-Type':'application/json'})
        return json.load(urllib.request.urlopen(request,timeout=20))
    identity=post('signInWithPassword',{'email':email,'password':password,'returnSecureToken':True})
    account=post('lookup',{'idToken':identity['idToken']})['users'][0]
    assert account['email']==email
    return account.get('displayName','')

subprocess.run(['python3','.github/scripts/check-firebase-local.py'],check=True)
adb('shell','wm','size',f'{W}x{H}');adb('shell','wm','density','160')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-f','0x10008000','-n',PACKAGE+'/.MainActivity')
wait('submit');tap(resource='switch_form');wait('name')
enter('name','Conta Perfil');enter('email',email);enter('password',password);enter('confirmation',password)
tap(resource='submit');wait('user_name','Conta Perfil');tap(resource='profileFragment')
tap(resource='profile_edit_name');replace_name('Nome Atualizado');tap(resource='profile_save_name');wait('profile_status','Nome confirmado',timeout=90)
assert server_name()=='Nome Atualizado'
# Cancelar alterações não grava; campos inválidos também não alteram a conta.
tap(resource='profile_edit_name');replace_name('Nao Salvar');tap(resource='profile_cancel_name');tap(resource='button1');wait('profile_edit_name')
assert server_name()=='Nome Atualizado'
tap(resource='profile_edit_name');replace_name('');tap(resource='profile_save_name');wait('profile_status','Informe um nome')
assert server_name()=='Nome Atualizado'
replace_name('Nome Retomado')
firewall=['sudo','iptables','-I','INPUT','-p','tcp','--dport','9099','-j','REJECT']
subprocess.run(firewall,check=True,capture_output=True)
try:
    tap(resource='profile_save_name');wait('profile_status','Seus campos foram mantidos',timeout=90)
    assert wait('profile_name').get('text')=='Nome Retomado'
    (OUT/'profile-network-error.png').write_bytes(adb('exec-out','screencap','-p').stdout)
finally:subprocess.run(['sudo','iptables','-D',*firewall[3:]],check=True,capture_output=True)
tap(resource='profile_save_name');wait('profile_status','Nome confirmado',timeout=90)
assert server_name()=='Nome Retomado'
# Fonte ampliada conserva o editor e seus botões; cancelamento permanece disponível.
tap(resource='profile_edit_name');replace_name('Nome Retomado')
adb('shell','settings','put','system','font_scale','2.0');time.sleep(2)
assert wait('profile_name').get('text')=='Nome Retomado'
tap(resource='profile_save_name');wait('profile_status','Nome confirmado',timeout=90)
top();(OUT/'profile-saved-font200.png').write_bytes(adb('exec-out','screencap','-p').stdout)
assert server_name()=='Nome Retomado'
tap(resource='account_my_heroes');wait('collection_status','Você ainda não tem heróis concluídos',timeout=90)
adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
adb('shell','input','keyevent','4');tap(resource='action');wait('submit')
(OUT/'profile-android.json').write_text(json.dumps({'success':True,'actualAuthNameConfirmed':True,'emailUnchanged':True,'cancelDoesNotWrite':True,'emptyNameRejected':True,'actualNetworkFailure':True,'fieldsPreserved':True,'explicitRetryConfirmed':True,'font200':True,'collectionStillEmpty':True,'signOut':True,'completedHeroAccessNotExercised':True,'paidProvidersNotExercised':True},indent=2))
print('Perfil: nome confirmado no Auth, cancelamento/validação, rede/retomada e fonte 200% verificados.',flush=True)
