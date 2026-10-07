"""Uma passagem pelo backend real emulado e pelo fluxo nativo de salvar rascunho."""
import json
from pathlib import Path
import re
import subprocess
import time
import urllib.request
import xml.etree.ElementTree as ET
from emulator_ui import dismiss_launcher_anr

PACKAGE = 'com.example.app_marvel'
OUT = Path('firebase-local-check'); OUT.mkdir(exist_ok=True)
W, H = 430, 932

def adb(*args, check=True):
    result = subprocess.run(['adb', *args], capture_output=True, timeout=40)
    if check: result.check_returncode()
    return result

def nodes():
    for attempt in range(8):
        adb('shell','uiautomator','dump','/sdcard/draft-window.xml',check=False)
        raw=adb('shell','cat','/sdcard/draft-window.xml',check=False).stdout
        start=raw.find(b'<?xml')
        if start<0: start=raw.find(b'<hierarchy')
        if start>=0:
            try:
                snapshot=list(ET.fromstring(raw[start:]).iter('node'))
                if dismiss_launcher_anr(snapshot, adb): continue
                return snapshot
            except ET.ParseError: pass
        time.sleep(1)
    (OUT/'hero-draft-ui-failure.png').write_bytes(adb('exec-out','screencap','-p').stdout)
    failure=adb('logcat','-d','-s','AndroidRuntime:E',check=False).stdout.decode(errors='replace')
    print(failure[-4000:])
    raise RuntimeError('UiAutomator não encontrou uma árvore XML estável.')

def find(rows,resource=None,text=None):
    return next((n for n in rows if (resource and n.get('resource-id','').endswith('/'+resource)) or (text and n.get('text')==text)),None)

def bounds(node): return tuple(map(int,re.findall(r'\d+',node.get('bounds',''))))

def top():
    for _ in range(5): adb('shell','input','swipe',str(W//2),str(H//3),str(W//2),str(H*4//5),'150')

def tap(resource=None,text=None):
    for attempt in range(28):
        rows = nodes(); node = find(rows,resource,text)
        nav = find(rows,'bottom_navigation'); bottom = bounds(nav)[1] if nav is not None else H
        if node is not None:
            x1,y1,x2,y2 = bounds(node)
            if x2>x1 and y2>y1 and (resource=='createHeroFragment' or (y1+y2)//2<bottom):
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(.3); return
        adb('shell','input','swipe',str(W//2),str(H*3//4),str(W//2),str(H//3),'200')
        if attempt==18: top()
    raise RuntimeError('Controle indisponível: '+str(resource or text))

def wait(resource,text=None,timeout=75):
    deadline = time.monotonic()+timeout
    while time.monotonic()<deadline:
        node=find(nodes(),resource)
        if node is not None and (text is None or text in node.get('text','')): return node
        if node is None: adb('shell','input','swipe',str(W//2),str(H*3//4),str(W//2),str(H//3),'200')
        time.sleep(.5)
    raise RuntimeError('Conteúdo indisponível: '+resource)

def enter(resource,value):
    tap(resource=resource); adb('shell','input','text',value.replace(' ','%s'))
    adb('shell','input','keyevent','4'); time.sleep(.2)

subprocess.run(['python3','.github/scripts/check-firebase-local.py'],check=True)
subprocess.run(['node','tools/check-hero-drafts-local.cjs'],check=True)
# Apenas o lote necessário de catálogos/traduções reais para selecionar no formulário.
adb('shell','am','force-stop',PACKAGE)
adb('shell','am','start','-n',PACKAGE+'/.diagnostics.HeroFormCheckActivity','--es','check','hero-form-draft')
for attempt in range(150):
    report=adb('shell','run-as',PACKAGE,'cat','files/diagnostics/hero-form-draft.json',check=False)
    if report.returncode==0:
        source=json.loads(report.stdout); assert source['success']; break
    time.sleep(1)
else: raise RuntimeError('Catálogos reais não ficaram disponíveis.')

adb('shell','wm','size',f'{W}x{H}'); adb('shell','wm','density','160')
adb('shell','am','force-stop',PACKAGE); adb('shell','am','start','-n',PACKAGE+'/.MainActivity'); time.sleep(2)
tap(text='Explorar sem entrar'); tap(resource='createHeroFragment'); wait('hero_heading','Quem é')
enter('hero_name','Guardiao Local'); enter('hero_real_name','Pessoa de Teste')
tap(resource='hero_next'); wait('hero_heading','De onde'); top()
tap(resource='hero_origin'); tap(text=source['origins'][0]['label']); top()
tap(text=source['powers'][0]['label'])
enter('hero_description','Rascunho de integracao local sem imagem gerada.')
tap(resource='hero_next'); top(); wait('hero_review_data','Guardiao Local')
tap(resource='hero_save_draft'); wait('submit'); tap(resource='switch_form'); wait('name')
email=f'android-draft-{int(time.time())}@example.test'; password='local-emulator-only-928374'
enter('name','Conta de Teste'); enter('email',email); enter('password',password); enter('confirmation',password)
tap(resource='submit'); top(); review=wait('hero_review_data','Guardiao Local')
assert 'Pessoa de Teste' in review.get('text') and source['powers'][0]['label'] in review.get('text')
# Interrupção real da conexão da função local, restaurada antes da nova tentativa.
firewall=['sudo','iptables','-I','INPUT','-p','tcp','--dport','5001','-j','REJECT']
subprocess.run(firewall,check=True,capture_output=True)
try:
    tap(resource='hero_save_draft'); wait('hero_draft_status','Não foi possível confirmar o salvamento no backend local',timeout=90)
    top(); assert 'Guardiao Local' in wait('hero_review_data').get('text')
finally:
    subprocess.run([*firewall[:2],'-D',*firewall[3:]],check=True,capture_output=True)
tap(resource='hero_save_draft'); wait('hero_draft_status','Rascunho salvo',timeout=90)
# Confirma documento do app no servidor, sem depender apenas da mensagem da tela.
request=urllib.request.Request('http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=demo-local',data=json.dumps({'email':email,'password':password,'returnSecureToken':True}).encode(),headers={'Content-Type':'application/json'})
identity=json.load(urllib.request.urlopen(request,timeout=20))
url=f"http://127.0.0.1:8080/v1/projects/demo-marvel-local/databases/(default)/documents/users/{identity['localId']}/heroDrafts"
request=urllib.request.Request(url,headers={'Authorization':'Bearer '+identity['idToken']})
documents=json.load(urllib.request.urlopen(request,timeout=20))['documents']
assert len(documents)==1
fields=documents[0]['fields']
assert fields['heroName']['stringValue']=='Guardiao Local'
assert int(fields['originId']['integerValue'])==source['origins'][0]['id']
assert int(fields['powerIds']['arrayValue']['values'][0]['integerValue'])==source['powers'][0]['id']
assert fields['uid']['stringValue']==identity['localId'] and fields['state']['stringValue']=='draft'
assert fields['generatedImage']['booleanValue'] is False
# Um único registro mesmo após um novo toque, e campos mantidos ao editar.
tap(resource='hero_save_draft'); wait('hero_draft_status','Rascunho salvo')
tap(resource='hero_previous'); top(); wait('hero_name','Guardiao Local')
tap(resource='hero_next'); tap(resource='hero_next'); wait('hero_draft_status','Rascunho salvo')
adb('shell','settings','put','system','font_scale','2.0'); time.sleep(2)
wait('hero_save_draft'); wait('hero_draft_status','Rascunho salvo')
image=adb('exec-out','screencap','-p').stdout; assert image.startswith(b'\x89PNG')
(OUT/'hero-draft-saved-font200.png').write_bytes(image)
adb('shell','settings','put','system','font_scale','1.0')
(OUT/'hero-drafts-android.json').write_text(json.dumps({'success':True,'registerReturnsToReview':True,'fieldsPreserved':True,'networkFailureAndRetry':True,'serverDocumentConfirmed':True,'originId':source['origins'][0]['id'],'powerId':source['powers'][0]['id'],'generatedImage':False,'syntheticUserInputOnly':True,'font200':True},indent=2))
print('Formulário, cadastro/retorno, falha real, nova tentativa e documento confirmado no servidor.',flush=True)
