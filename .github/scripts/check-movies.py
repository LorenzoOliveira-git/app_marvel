"""Catálogo de arcos com respostas reais, cache offline e navegação via ADB."""
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
    adb('shell','am','start','-n',PACKAGE+'/.diagnostics.MoviesCheckActivity','--es','check',name)
    deadline=time.monotonic()+200
    while time.monotonic()<deadline:
        result=adb('shell','run-as',PACKAGE,'cat',f'files/diagnostics/{name}.json',check=False)
        if result.returncode==0:
            report=json.loads(result.stdout);(OUT/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
            assert report['success'],report.get('failed_stage','')
            return report
        time.sleep(2)
    raise RuntimeError('Filmes reais não terminaram.')
def run(name):
    for attempt in range(3 if name=='movies' else 1):
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
online=run('movies')
try:
    adb('shell','cmd','connectivity','airplane-mode','enable');adb('shell','svc','wifi','disable');adb('shell','svc','data','disable')
    offline=run('movies-cache');assert online==offline
finally:
    adb('shell','cmd','connectivity','airplane-mode','disable',check=False);adb('shell','svc','wifi','enable',check=False);adb('shell','svc','data','enable',check=False)
network_ready()
def nodes():
    adb('shell','uiautomator','dump','/sdcard/issue-window.xml')
    data=adb('exec-out','cat','/sdcard/issue-window.xml').stdout
    (PREVIEW/'movies-window.xml').write_bytes(data)
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
        adb('shell','input','swipe','215','740','215','260','350')
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
def show(resource,expected=None,width=430,height=932):
    top(width,height)
    for _ in range(35):
        for node in nodes():
            if node.get('resource-id','').endswith('/'+resource) and (expected is None or node.get('text')==expected):
                x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
                if x2>x1 and y2>y1 and 110<=y1<height-160:return node
        adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height*4//5-100),'1000')
    raise RuntimeError('Controle não visível: '+resource+' '+str(expected))
def search(value):
    show('movies_search');tap(resource='movies_search');tap(resource='movies_search_input')
    adb('shell','input','keycombination','113','29');adb('shell','input','keyevent','67')
    if value:adb('shell','input','text',value)
    tap(resource='button1')
    time.sleep(1)
if '--data-only' in __import__('sys').argv:
    print('Diagnósticos reais/cache aprovados; navegação em grade coberta por check-arquivo.py.', flush=True)
    raise SystemExit(0)

adb('shell','wm','size','430x932');adb('shell','wm','density','160');adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(2)
tap(text='Explorar sem entrar');tap(text='Histórias');tap(resource='open_movies')
wait('movie_title','Iron Man');capture('movies-top')
assert online['first'], 'Lote inicial sem filme para conferir visualmente.'
show('movie_title',online['first'][0]['name']);capture('movies-first')
show('load_more');tap(resource='load_more');time.sleep(2)
show('selection_count');wait('selection_count',str(len(online['first'])+len(online['next'])));capture('movies-next-page')
search('Iron%sMan');show('selection_count');wait('selection_count','3');wait('movie_title','Iron Man');capture('movies-search')
show('next_movie');tap(resource='next_movie');wait('movie_title','Iron Man 2');capture('movies-next')
tap(resource='header_back');tap(resource='open_movies');show('movie_title','Iron Man 2');capture('movies-restored')
search('Batman');wait('status_title','Nenhum filme neste lote');capture('movies-foreign-empty')
search('marv-no-such-film-918237');wait('status_title','Nenhum filme neste lote');capture('movies-empty')
search('');show('movies_za');tap(resource='movies_za');time.sleep(2)
assert online['descending'], 'Lote descendente sem filme para conferir visualmente.'
show('movie_title',online['descending'][0]['name']);capture('movies-descending')
for width,height,font,label in [(320,640,'1.0','small'),(640,1000,'1.0','large'),(430,932,'2.0','font200')]:
    adb('shell','wm','size',f'{width}x{height}');adb('shell','settings','put','system','font_scale',font);time.sleep(2);top(width,height)
    wait('movies_featured_heading');capture('movies-'+label+'-top')
    show('movie_title',online['descending'][0]['name'],width,height);capture('movies-'+label+'-poster')
adb('shell','wm','size','430x932');adb('shell','settings','put','system','font_scale','1.0');time.sleep(2)
tap(resource='header_back');tap(text='Início');wait('issue_title')
print('Filmes reais, exclusão de DC, paginação, busca, seleção, restauração, cache offline e layouts conferidos.')
