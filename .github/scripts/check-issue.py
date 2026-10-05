"""Detalhes nativos com ComicVine e tradução reais; cache e navegação via ADB."""
import json
from pathlib import Path
import re
import subprocess
import struct
import zlib
import time
import xml.etree.ElementTree as ET

PACKAGE='com.example.app_marvel'
OUT=Path('app/build/catalog-check'); PREVIEW=Path('app/build/previews')
OUT.mkdir(parents=True,exist_ok=True); PREVIEW.mkdir(parents=True,exist_ok=True)
def adb(*args,check=True):
    for attempt in range(3):
        result=subprocess.run(['adb',*args],capture_output=True,timeout=30)
        if not check or result.returncode==0:return result
        if attempt<2:
            subprocess.run(['adb','wait-for-device'],capture_output=True,check=True,timeout=20);time.sleep(1)
    result.check_returncode()
def run(name):
    adb('shell','am','force-stop',PACKAGE);adb('shell','run-as',PACKAGE,'rm','-f',f'files/diagnostics/{name}.json')
    adb('shell','am','start','-n',PACKAGE+'/.diagnostics.IssueCheckActivity','--es','check',name)
    deadline=time.monotonic()+200
    while time.monotonic()<deadline:
        result=adb('shell','run-as',PACKAGE,'cat',f'files/diagnostics/{name}.json',check=False)
        if result.returncode==0:
            report=json.loads(result.stdout);(OUT/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
            assert report['success'],report.get('failed_stage','')
            return report
        time.sleep(2)
    raise RuntimeError('Detalhes reais não terminaram.')
online=run('issue')
try:
    adb('shell','cmd','connectivity','airplane-mode','enable');adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
    offline=run('issue-cache');assert offline['translation_from_cache']
    assert {k:v for k,v in online.items() if k!='translation_from_cache'}=={k:v for k,v in offline.items() if k!='translation_from_cache'}
finally:
    adb('shell','cmd','connectivity','airplane-mode','disable',check=False);adb('shell','svc','wifi','enable',check=False);adb('shell','svc','data','enable',check=False)
def nodes():
    adb('shell','uiautomator','dump','/sdcard/issue-window.xml')
    data=adb('exec-out','cat','/sdcard/issue-window.xml').stdout
    (PREVIEW/'issue-window.xml').write_bytes(data)
    return list(ET.fromstring(data).iter('node'))
def top(width=430,height=932):
    for _ in range(10):adb('shell','input','swipe',str(width//2),str(height//3),str(width//2),str(height*4//5),'300')
    time.sleep(.5)
def tap(resource=None,text=None):
    for _ in range(12):
        snapshot=nodes();nav=next((n for n in snapshot if n.get('resource-id','').endswith('/bottom_navigation')),None)
        nav_top=int(re.findall(r'\d+',nav.get('bounds'))[1]) if nav is not None else 10000
        for n in snapshot:
            if (resource and n.get('resource-id','').endswith('/'+resource)) or (text and (n.get('text')==text or n.get('content-desc','').split(',')[0]==text)):
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
                if x2>x1 and y2>y1 and (text in ['Histórias','Personagens','Início'] or (y1+y2)//2<nav_top):
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.8);return
        adb('shell','input','swipe','215','740','215','260','1000')
    raise RuntimeError('Controle não encontrado: '+str(resource or text))
def heading(text=None,resource=None):
    tap(resource=resource,text=text)
    for n in nodes():
        if (text and n.get('text')==text) or (resource and n.get('resource-id','').endswith('/'+resource)):
            y1=int(re.findall(r'\d+',n.get('bounds',''))[1]);delta=min(550,max(0,y1-130))
            if delta>20:adb('shell','input','swipe','215','760','215',str(760-delta),'1000');time.sleep(.5)
            return
def wait(resource,text=None):
    deadline=time.monotonic()+70
    while time.monotonic()<deadline:
        for n in nodes():
            if n.get('resource-id','').endswith('/'+resource) and (text is None or text in n.get('text','')):return n
        time.sleep(1)
    raise RuntimeError('Conteúdo não apareceu: '+resource)
def valid_png(data):
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):return False
    offset=8
    while offset+12<=len(data):
        length=struct.unpack('>I',data[offset:offset+4])[0];end=offset+12+length
        if end>len(data):return False
        chunk=data[offset+4:offset+8+length]
        if zlib.crc32(chunk)&0xffffffff!=struct.unpack('>I',data[offset+8+length:end])[0]:return False
        if chunk[:4]==b'IEND':return end==len(data)
        offset=end
    return False
def capture(name):
    for _ in range(4):
        data=adb('exec-out','screencap','-p').stdout
        if valid_png(data):(PREVIEW/(name+'.png')).write_bytes(data);return
        time.sleep(1)
    raise RuntimeError('PNG incompleto: '+name)
def show_first(expected):
    top()
    for _ in range(40):
        for node in nodes():
            if node.get('resource-id','').endswith('/first_title') and node.get('text')==expected:
                y1=int(re.findall(r'\d+',node.get('bounds'))[1])
                if 110<=y1<=650:return
        adb('shell','input','swipe','215','740','215','620','1000')
    capture('issue-first-missing')
    raise RuntimeError('Primeira aparição não encontrada: '+expected)
# Métadados ausentes: a edição recente não cria uma descrição nem um carregamento eterno.
adb('shell','wm','size','430x932');adb('shell','wm','density','160');adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
tap(text='Explorar sem entrar');home_title=wait('issue_title').get('text');tap(resource='issue_more');wait('issue_heading',home_title);capture('issue-recent')
if home_title==online['recent']['title'] and not online['recent']['original_description'] and not online['recent']['original_deck']:
    assert not any(n.get('resource-id','').endswith('/issue_description_heading') or n.get('resource-id','').endswith('/issue_deck_heading') for n in nodes())
tap(resource='header_back');tap(text='Histórias');tap(resource='open_comics');tap(resource='comic_title');wait('comic_title');tap(resource='comic_more');wait('issue_heading');capture('issue-from-catalog')
tap(resource='header_back');tap(resource='comic_title');wait('comic_title');tap(resource='header_back');tap(text='Personagens');wait('character_name','Spider-Man')
tap(resource='character_more');wait('details_name','Spider-Man');show_first(online['historical']['title']);capture('issue-character-first');tap(resource='first_more');wait('issue_heading',online['historical']['title']);capture('issue-historical-top')
heading(resource='issue_description_heading');wait('issue_description');capture('issue-description')
# A lista de personagens fica após a descrição longa: rolar pelo próprio controle.
heading(text='Personagens nesta edição');wait('related_name',online['characters'][0]['name']);capture('issue-characters')
tap(resource='related_more');wait('details_name',online['characters'][0]['name']);capture('issue-related-character')
tap(resource='header_back');top();wait('issue_heading',online['historical']['title'])
heading(text='Créditos de criação');capture('issue-creators')
for width,height,font,label in [(320,640,'1.0','small'),(640,1000,'1.0','large'),(430,932,'2.0','font200')]:
    adb('shell','wm','size',f'{width}x{height}');adb('shell','settings','put','system','font_scale',font);time.sleep(2);top(width,height)
    wait('issue_heading',online['historical']['title']);capture('issue-'+label+'-top')
    for _ in range(3):adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height//3),'350')
    capture('issue-'+label+'-content')
adb('shell','wm','size','430x932');adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
tap(resource='header_back');top();wait('details_name','Spider-Man');tap(resource='details_history');wait('identity_name','Spider-Man')
heading(text='Onde tudo começou');tap(resource='issue_more');wait('issue_heading',online['historical']['title']);capture('issue-from-appearances')
tap(resource='header_back');top();wait('identity_name','Spider-Man')
print('Detalhes reais, tradução/cache offline, créditos, perfis relacionados e retorno às origens conferidos.')
