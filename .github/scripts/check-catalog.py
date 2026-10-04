"""Repositórios Android com ComicVine real; reinício offline; navegação e capturas ADB."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.app_marvel'
OUT = Path('app/build/catalog-check')
PREVIEW = Path('app/build/previews')
OUT.mkdir(parents=True, exist_ok=True)
PREVIEW.mkdir(parents=True, exist_ok=True)

def adb(*args, check=True):
    return subprocess.run(['adb', *args], check=check, capture_output=True)

def run(name):
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'run-as', PACKAGE, 'rm', '-f', f'files/diagnostics/{name}.json')
    adb('shell', 'am', 'start', '-n', PACKAGE+'/.diagnostics.CatalogCheckActivity', '--es', 'check', name)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        result = adb('shell', 'run-as', PACKAGE, 'cat', f'files/diagnostics/{name}.json', check=False)
        if result.returncode == 0:
            report = json.loads(result.stdout)
            (OUT/(name+'.json')).write_text(json.dumps(report, ensure_ascii=False, indent=2))
            assert report['success'], 'Falha no catálogo real: ' + report.get('failed_stage', '')
            owner = report['publisher_id']
            assert all(row['publisher_id'] == owner for group in ['first','next','search','origin_gender','team'] for row in report[group])
            assert set(x['id'] for x in report['first']).isdisjoint(x['id'] for x in report['next']), 'Paginação duplicada'
            assert all('Spider-Man' in x['name'] for x in report['search']), 'Busca incoerente'
            assert [x['store_date'] for x in report['issues']] == sorted([x['store_date'] for x in report['issues']], reverse=True)
            return report
        time.sleep(2)
    raise RuntimeError('O catálogo não terminou; verificar falha real, não presumir sucesso.')

online = run('catalog')
try:
    adb('shell','cmd','connectivity','airplane-mode','enable')
    adb('shell','svc','wifi','disable'); adb('shell','svc','data','disable')
    offline = run('catalog-cache')
    assert offline['translation_from_cache'], 'Tradução não recuperada do cache'
    for field in ['featured','issues','first','next','search','origin_gender','team','translated_deck']:
        assert offline[field] == online[field], 'Cache divergiu: '+field
finally:
    adb('shell','cmd','connectivity','airplane-mode','disable',check=False)
    adb('shell','svc','wifi','enable',check=False); adb('shell','svc','data','enable',check=False)

def nodes():
    adb('shell','uiautomator','dump','/sdcard/catalog-window.xml')
    result = adb('exec-out','cat','/sdcard/catalog-window.xml').stdout
    (PREVIEW/'catalog-window.xml').write_bytes(result)
    return list(ET.fromstring(result).iter('node'))

def tap(text=None, resource=None):
    for node in nodes():
        if ((text is not None and (node.get('text') == text or node.get('content-desc','').split(',')[0] == text))
                or (resource is not None and node.get('resource-id','').endswith('/'+resource))):
            x1,y1,x2,y2 = map(int,re.findall(r'\d+',node.get('bounds','')))
            if x2>x1 and y2>y1:
                adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2)); time.sleep(1); return
    raise RuntimeError('Controle não encontrado: '+str(text or resource))

def wait(resource, contains=None):
    deadline = time.monotonic()+90
    while time.monotonic()<deadline:
        for node in nodes():
            if node.get('resource-id','').endswith('/'+resource) and (contains is None or contains in node.get('text','')): return node
        time.sleep(2)
    raise RuntimeError('Conteúdo não apareceu: '+resource)

def capture(name):
    time.sleep(3)
    (PREVIEW/(name+'.png')).write_bytes(adb('exec-out','screencap','-p').stdout)

adb('shell','wm','size','430x932'); adb('shell','wm','density','160')
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','am','force-stop',PACKAGE); adb('shell','am','start','-n',PACKAGE+'/.MainActivity'); time.sleep(3)
tap('Explorar sem entrar'); wait('issue_title'); capture('catalog-home')
adb('shell','input','swipe','215','730','215','250','600'); wait('featured_name','Spider-Man'); capture('catalog-home-featured')
adb('shell','input','swipe','215','730','215','250','600'); wait('fact_text'); capture('catalog-home-fact')
tap('Personagens'); wait('character_name','Spider-Man'); capture('catalog-characters')
tap(resource='next_character'); time.sleep(3); capture('catalog-characters-next')
tap(resource='search_name'); adb('shell','input','text','Spider-Man'); adb('shell','input','keyevent','66'); wait('character_name','Spider-Man'); capture('catalog-search')
tap(resource='gender_filter'); tap('Feminino'); time.sleep(4); capture('catalog-gender')
tap(resource='clear_filters'); wait('character_name','Spider-Man')
adb('shell','wm','size','320x640'); time.sleep(2); capture('catalog-small')
adb('shell','settings','put','system','font_scale','2.0'); time.sleep(2); capture('catalog-font-200')
adb('shell','settings','put','system','font_scale','1.0'); adb('shell','wm','size','430x932')
tap('Início'); time.sleep(2)
# Ausência de chave em instalação nova: falha comum com Marv, sem aviso técnico.
adb('shell','run-as',PACKAGE,'rm','-f','no_backup/comicvine-api-key')
adb('shell','run-as',PACKAGE,'rm','-f','databases/comicvine-cache.db','databases/comicvine-cache.db-wal','databases/comicvine-cache.db-shm')
adb('shell','am','force-stop',PACKAGE); adb('shell','am','start','-n',PACKAGE+'/.MainActivity'); time.sleep(2); tap('Explorar sem entrar')
time.sleep(4); capture('catalog-error')
print('Catálogo real, filtros, páginas, tradução/cache offline e navegação conferidos no Android.')
