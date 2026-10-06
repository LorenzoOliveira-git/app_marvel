"""Verifica o catálogo real, reinício offline e a tela nativa; não usa mocks."""
import json
from pathlib import Path
import re
import subprocess
import struct
import zlib
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.app_marvel'
OUT = Path('app/build/catalog-check')
PREVIEW = Path('app/build/previews')
OUT.mkdir(parents=True,exist_ok=True)
PREVIEW.mkdir(parents=True,exist_ok=True)
def adb(*args,check=True):
    for attempt in range(3):
        result = subprocess.run(['adb',*args],capture_output=True)
        if not check or result.returncode == 0:return result
        if attempt < 2:
            subprocess.run(['adb','wait-for-device'],capture_output=True,timeout=20)
            time.sleep(1)
    result.check_returncode()
def run(name):
    adb('shell','am','force-stop',PACKAGE)
    adb('shell','run-as',PACKAGE,'rm','-f',f'files/diagnostics/{name}.json')
    adb('shell','am','start','-n',PACKAGE+'/.diagnostics.ComicsCheckActivity','--es','check',name)
    deadline = time.monotonic()+180
    while time.monotonic()<deadline:
        result = adb('shell','run-as',PACKAGE,'cat',f'files/diagnostics/{name}.json',check=False)
        if result.returncode == 0:
            report = json.loads(result.stdout)
            (OUT/(name+'.json')).write_text(json.dumps(report,ensure_ascii=False,indent=2))
            assert report['success'],report.get('failed_stage','')
            for first,next_page in [('first','next'),('scoped','scoped_next')]:
                assert set(x['id'] for x in report[first]).isdisjoint(x['id'] for x in report[next_page])
                assert report[next_page+'_offset'] > report[first+'_offset']
            assert report['foreign_volume_rejected']
            return report
        time.sleep(2)
    raise RuntimeError('O diagnóstico real de quadrinhos não terminou.')
online = run('comics')
try:
    adb('shell','cmd','connectivity','airplane-mode','enable'); adb('shell','svc','wifi','disable'); adb('shell','svc','data','disable')
    offline = run('comics-cache')
    assert offline == online,'O catálogo divergiu após reiniciar offline.'
finally:
    adb('shell','cmd','connectivity','airplane-mode','disable',check=False)
    adb('shell','svc','wifi','enable',check=False); adb('shell','svc','data','enable',check=False)
def nodes():
    adb('shell','uiautomator','dump','/sdcard/comics-window.xml')
    return list(ET.fromstring(adb('exec-out','cat','/sdcard/comics-window.xml').stdout).iter('node'))
def bounds(node):
    return list(map(int,re.findall(r'\d+',node.get('bounds',''))))
def tap(resource=None,text=None):
    for attempt in range(8):
        snapshot = nodes(); nav = next((n for n in snapshot if n.get('resource-id','').endswith('/bottom_navigation')),None)
        nav_top = bounds(nav)[1] if nav is not None else 10000
        for n in snapshot:
            if ((resource and n.get('resource-id','').endswith('/'+resource)) or (text and (n.get('text')==text or n.get('content-desc','').split(',')[0]==text))):
                x1,y1,x2,y2 = bounds(n)
                if x2>x1 and y2>y1 and (text in ['Histórias'] or (y1+y2)//2 < nav_top):
                    adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(1); return
        adb('shell','input','swipe','215','730','215','270','400')
    raise RuntimeError('Controle não encontrado: '+str(resource or text))
def wait(resource,contains=None):
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        for n in nodes():
            if n.get('resource-id','').endswith('/'+resource) and (contains is None or contains in n.get('text','')):return n
        time.sleep(1)
    raise RuntimeError('Conteúdo não apareceu: '+resource)
def top(width=430,height=932):
    for i in range(4):adb('shell','input','swipe',str(width//2),str(height//3),str(width//2),str(height*4//5),'300')
    time.sleep(1)
def capture(name):
    time.sleep(2)
    for attempt in range(4):
        data = adb('exec-out','screencap','-p').stdout
        if valid_png(data):
            (PREVIEW/(name+'.png')).write_bytes(data);return
        time.sleep(1)
    raise RuntimeError('Captura incompleta do emulador: '+name)
def valid_png(data):
    if not data.startswith(b'\x89PNG\r\n\x1a\n'):return False
    offset=8
    while offset+12<=len(data):
        length=struct.unpack('>I',data[offset:offset+4])[0]
        end=offset+12+length
        if end>len(data):return False
        chunk=data[offset+4:offset+8+length]
        if (zlib.crc32(chunk)&0xffffffff) != struct.unpack('>I',data[offset+8+length:end])[0]:return False
        if chunk[:4]==b'IEND':return end==len(data)
        offset=end
    return False
if '--data-only' in __import__('sys').argv:
    print('Diagnósticos reais/cache aprovados; navegação em grade coberta por check-arquivo.py.', flush=True)
    raise SystemExit(0)

adb('shell','wm','size','430x932');adb('shell','wm','density','160');adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop',PACKAGE);adb('shell','am','start','-n',PACKAGE+'/.MainActivity');time.sleep(3)
tap(text='Explorar sem entrar');tap(text='Histórias');tap(resource='open_comics');wait('comics_featured_heading');capture('comics-top')
tap(resource='comics_volume');wait('comics_volume_search');tap(resource='comics_volume_search')
adb('shell','input','text','Marvel%sRivals');time.sleep(1);adb('shell','input','keyevent','4');capture('comics-volume-search')
tap(text=online['sample_name']);tap(resource='comic_title');wait('comic_title',online['scoped'][0]['title']);capture('comics-selected')
tap(resource='next_comic');wait('comic_title',online['scoped'][1]['title']);capture('comics-next-cover')
tap(resource='load_more');time.sleep(2);capture('comics-more')
top();tap(resource='comics_oldest');tap(resource='comic_title');wait('comic_title',online['oldest'][0]['title']);capture('comics-oldest')
top();tap(resource='header_back');tap(resource='open_comics');tap(resource='comic_title');wait('comic_title',online['oldest'][0]['title']);capture('comics-restored')
for width,height,font,name in [(320,640,'1.0','small'),(640,1000,'1.0','large'),(430,932,'2.0','font200')]:
    adb('shell','wm','size',f'{width}x{height}');adb('shell','settings','put','system','font_scale',font);time.sleep(3)
    top(width,height);wait('comics_featured_heading');capture('comics-'+name+'-top')
    # Proporções variáveis exigem coordenadas da própria tela para a rolagem.
    for i in range(2):adb('shell','input','swipe',str(width//2),str(height*4//5),str(width//2),str(height//3),'400')
    capture('comics-'+name+'-content')
adb('shell','wm','size','430x932');adb('shell','settings','put','system','font_scale','1.0')
print('Quadrinhos reais, cache offline, busca, ordenação, paginação e navegação conferidos.')
