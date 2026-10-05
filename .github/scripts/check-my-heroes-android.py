"""Coleção real vazia no Android: conta, fonte ampliada, falha de rede e recuperação."""
from pathlib import Path
import subprocess
import time
import json
# Apenas helpers de UiAutomator existentes; não executa o roteiro de geração novamente.
helper = Path('.github/scripts/check-hero-drafts-local.py').read_text().split("subprocess.run(['python3'")[0]
exec(compile(helper, 'hero-ui-helpers', 'exec'))
subprocess.run(['python3','.github/scripts/check-firebase-local.py'],check=True)
subprocess.run(['node','tools/check-hero-profile-local.cjs'],check=True)
adb('shell','wm','size',f'{W}x{H}');adb('shell','wm','density','160')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity')
wait('submit');tap(resource='switch_form');wait('name')
enter('name','Conta Colecao');enter('email',f'collection-{int(time.time())}@example.test');enter('password','local-emulator-only-928374');enter('confirmation','local-emulator-only-928374')
tap(resource='submit');wait('user_name','Conta Colecao');tap(resource='profileFragment');tap(resource='account_my_heroes')
wait('collection_status','Você ainda não tem heróis concluídos',timeout=90)
(OUT/'my-heroes-empty.png').write_bytes(adb('exec-out','screencap','-p').stdout)
# Desconexão efetiva do Firestore; o estado vazio não é produzido pelo cache.
firewall=['sudo','iptables','-I','INPUT','-p','tcp','--dport','8080','-j','REJECT']
subprocess.run(firewall,check=True,capture_output=True)
try:
    tap(resource='collection_refresh');wait('collection_status','Não foi possível atualizar',timeout=90)
    (OUT/'my-heroes-network-error.png').write_bytes(adb('exec-out','screencap','-p').stdout)
finally:
    subprocess.run(['sudo','iptables','-D',*firewall[3:]],check=True,capture_output=True)
tap(resource='collection_refresh');wait('collection_status','Você ainda não tem heróis concluídos',timeout=90)
adb('shell','settings','put','system','font_scale','2.0');time.sleep(2)
wait('collection_status','Você ainda não tem heróis concluídos');tap(resource='collection_refresh');wait('collection_status','Você ainda não tem heróis concluídos',timeout=90)
top();(OUT/'my-heroes-empty-font200.png').write_bytes(adb('exec-out','screencap','-p').stdout)
adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
# Retorno conserva conta e sign-out remove acesso à coleção.
adb('shell','input','keyevent','4');tap(resource='action');wait('submit')
(OUT/'my-heroes-android.json').write_text(json.dumps({'success':True,'actualEmptyCollection':True,'realNetworkFailure':True,'retryRestoresServerRead':True,'font200':True,'signOutReturnsToLogin':True,'exampleHeroesCreated':False,'populatedCollectionAndEditorNotExercised':True},indent=2))
print('Android: coleção vazia, rede/retomada, fonte 200% e saída da conta verificados.',flush=True)
