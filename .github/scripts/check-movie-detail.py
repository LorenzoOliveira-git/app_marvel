"""Detalhes de filmes com respostas reais, cache offline e navegação via ADB."""
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
def run_once(name):
    adb('shell','am','force-stop',PACKAGE);adb('logcat','-c');adb('shell','run-as',PACKAGE,'rm','-f',f'files/diagnostics/{name}.json')
    adb('shell','am','start','-n',PACKAGE+'/.diagnostics.MovieDetailsCheckActivity','--es','check',name)
    deadline=time.monotonic()+300
    while time.monotonic()<deadline:
        result=adb('shell','run-as',PACKAGE,'cat',f'files/diagnostics/{name}.json',check=False)
        if result.returncode==0:
            report=json.loads(result.stdout);(OUT/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
            assert report['success'],report.get('failed_stage','')
            return report
        time.sleep(2)
    raise RuntimeError('Detalhes de filmes não terminaram.')
def run(name):
    for attempt in range(3 if name=='movie-detail' else 1):
        try:return run_once(name)
        except AssertionError:
            logs=adb('logcat','-d','-s','ComicVine:W').stdout.decode(errors='replace')
            failures=re.findall(r'Falha: ([A-Z_]+); HTTP=(\d+)',logs)
            if attempt==2 or not failures or any(kind!='NETWORK' or code!='0' for kind,code in failures):raise
            print('Falha NETWORK explícita: repetir o diagnóstico real após reconectar.',flush=True)
            time.sleep(5);network_ready()
def network_ready():
    deadline=time.monotonic()+60
    stable=0
    while time.monotonic()<deadline:
        state=adb('shell','dumpsys','connectivity').stdout.decode(errors='replace')
        stable=stable+1 if re.search(r'Capabilities:.*\bVALIDATED\b',state) else 0
        if stable>=3:return
        time.sleep(1)
    raise RuntimeError('Rede do emulador não validada após modo offline.')
network_ready()
online=run('movie-detail')
try:
    adb('shell','cmd','connectivity','airplane-mode','enable');adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
    offline=run('movie-detail-cache')
    assert offline['description_cached']
    assert {k:v for k,v in online.items() if k!='description_cached'}=={k:v for k,v in offline.items() if k!='description_cached'}
finally:
    adb('shell','cmd','connectivity','airplane-mode','disable',check=False);adb('shell','svc','wifi','enable',check=False);adb('shell','svc','data','enable',check=False)
network_ready()
def nodes():
    adb('shell','uiautomator','dump','/sdcard/issue-window.xml')
    data=adb('exec-out','cat','/sdcard/issue-window.xml').stdout
    (PREVIEW/'movie-detail-window.xml').write_bytes(data)
    return list(ET.fromstring(data).iter('node'))
def top(width=430,height=932):
    for _ in range(10):adb('shell','input','swipe',str(width//2),str(height//3),str(width//2),str(height*4//5),'300')
    time.sleep(.5)
def tap(resource=None,text=None,description=None):
    for _ in range(12):
        snapshot=nodes();nav=next((n for n in snapshot if n.get('resource-id','').endswith('/bottom_navigation')),None)
        nav_top=int(re.findall(r'\d+',nav.get('bounds'))[1]) if nav is not None else 10000
        for n in snapshot:
            if ((resource and n.get('resource-id','').endswith('/'+resource)) or (text and (n.get('text')==text or n.get('content-desc','').split(',')[0]==text))) and (description is None or n.get('content-desc')==description):
                x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds','')))
                if x2>x1 and y2>y1 and (text in ['Histórias','Personagens','Início'] or (y1+y2)//2<nav_top):
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2));time.sleep(.8);return
        adb('shell','input','swipe',str(W//2),str(H*4//5),str(W//2),str(H//3),'600')
    raise RuntimeError('Controle não encontrado: '+str(resource or text))
def heading(text=None,resource=None):
    tap(resource=resource,text=text)
    for n in nodes():
        if (text and n.get('text')==text) or (resource and n.get('resource-id','').endswith('/'+resource)):
            y1=int(re.findall(r'\d+',n.get('bounds',''))[1]);delta=min(550,max(0,y1-130))
            if delta>20:adb('shell','input','swipe','215','760','215',str(760-delta),'400');time.sleep(.5)
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
W,H=430,932
def reveal(resource,expected=None,width=None,height=None):
    width=width or W;height=height or H
    for _ in range(65):
        snapshot=nodes();nav=next((n for n in snapshot if n.get('resource-id','').endswith('/bottom_navigation')),None)
        navtop=int(re.findall(r'\d+',nav.get('bounds'))[1]) if nav is not None else height-100
        for node in snapshot:
            if node.get('resource-id','').endswith('/'+resource) and (expected is None or expected in node.get('text','')):
                x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
                if x2>x1 and 110<=y1 and y2<navtop:return node
        adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height*4//5-120),'1000')
    raise RuntimeError('Seção não encontrada: '+resource)
def seek_text(text):
    for _ in range(65):
        if any(n.get('text')==text for n in nodes()):return
        adb('shell','input','swipe',str(W//2),str(H*4//5),str(W//2),str(H//3),'1000')
    raise RuntimeError('Seção não encontrada: '+text)
if '--data-only' in __import__('sys').argv:
    print('Diagnósticos reais/cache aprovados; navegação editorial coberta por check-arquivo.py.', flush=True)
    raise SystemExit(0)

adb('shell','wm','size','430x932');adb('shell','wm','density','160');adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
tap(text='Explorar sem entrar');tap(text='Histórias');tap(resource='open_movies')
wait('movie_title','Iron Man');tap(resource='movie_more',description='Abrir detalhes de Iron Man')
wait('movie_details_heading','Iron Man');top();capture('movie-detail-top')
reveal('movie_details_rating','PG-13');capture('movie-detail-metadata')
reveal('movie_deck');wait('movie_deck',online['summary'][:30]);capture('movie-detail-summary')
reveal('related_more');wait('related_name',online['characters'][0]['name']);capture('movie-detail-characters');tap(resource='related_more')
wait('details_name',online['characters'][0]['name']);capture('movie-detail-profile');tap(resource='header_back')
reveal('related_more');capture('movie-detail-profile-return')
seek_text('Equipes Marvel vinculadas');reveal('related_name',online['teams'][0]['name']);capture('movie-detail-teams')
seek_text('Estúdios indicados na fonte');capture('movie-detail-studios')
seek_text('Autores vinculados na fonte');capture('movie-detail-writers')
reveal('movie_read_description');tap(resource='movie_read_description');wait('movie_description',online['description'][:30]);capture('movie-detail-description')
tap(resource='header_back');top();wait('movies_featured_heading');capture('movie-detail-catalog-return')
# Detalhes do filme selecionado no carrossel também abrem nativamente.
for _ in range(40):
    snapshot=nodes();matches=[n for n in snapshot if n.get('resource-id','').endswith('/movie_more')]
    if any(n.get('content-desc')=='Abrir detalhes de Ant-Man' and 110<=int(re.findall(r'\d+',n.get('bounds'))[1]) and int(re.findall(r'\d+',n.get('bounds'))[3])<H-160 for n in matches):break
    adb('shell','input','swipe','215','740','215','620','1000')
tap(resource='movie_more',description='Abrir detalhes de Ant-Man');wait('movie_details_heading','Ant-Man');capture('movie-detail-selected-film');tap(resource='header_back');top()
for width,height,font,label in [(320,640,'1.0','small'),(640,1000,'1.0','large'),(430,932,'2.0','font200')]:
    W,H=width,height
    adb('shell','wm','size',f'{width}x{height}');adb('shell','settings','put','system','font_scale',font);time.sleep(2);top(width,height)
    reveal('movie_more');tap(resource='movie_more',description='Abrir detalhes de Iron Man');top(width,height)
    reveal('movie_details_heading','Iron Man');capture('movie-detail-'+label+'-top')
    reveal('related_name',online['characters'][0]['name']);capture('movie-detail-'+label+'-characters')
    tap(resource='header_back');top(width,height)
adb('shell','wm','size','430x932');adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
tap(resource='header_back');tap(text='Início');wait('issue_title')
print('Detalhes reais de filmes, tradução/cache offline, vínculos Marvel, perfis, retorno e layouts conferidos.')
