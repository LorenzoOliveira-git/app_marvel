"""Smoke visual: tela inicial, retorno de detalhe, erro offline e fonte ampliada."""
from pathlib import Path
import json
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET
from emulator_ui import dismiss_launcher_anr

PACKAGE = 'com.example.app_marvel.preview'
OUT = Path('app/build/design-preview')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    for attempt in range(3):
        result = subprocess.run(['adb', *args], capture_output=True, timeout=30)
        if result.returncode == 0:
            return result.stdout
        if attempt < 2:
            subprocess.run(['adb', 'wait-for-device'], capture_output=True, check=True, timeout=20)
            time.sleep(2)
    result.check_returncode()

def nodes():
    for attempt in range(3):
        adb('shell', 'uiautomator', 'dump', '/sdcard/arquivo-ui.xml')
        raw = adb('exec-out', 'cat', '/sdcard/arquivo-ui.xml')
        # ADB pode emitir mensagens de inicialização antes do documento.
        start = raw.find(b'<?xml')
        if start < 0:
            start = raw.find(b'<hierarchy')
        try:
            snapshot = list(ET.fromstring(raw[start:] if start >= 0 else raw).iter('node'))
            if dismiss_launcher_anr(snapshot, adb): continue
            return snapshot
        except ET.ParseError:
            (OUT/'ui-read-failure.txt').write_bytes(raw[:4096])
            if attempt == 2:
                raise
            time.sleep(2)

def find(resource, snapshot=None):
    return next((n for n in (nodes() if snapshot is None else snapshot)
                 if n.get('resource-id', '').endswith('/' + resource)), None)

def wait(resource, timeout=90):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        node = find(resource)
        if node is not None:
            return node
        time.sleep(2)
    snapshot=nodes()
    print('Tela ao esperar '+resource+':',[(n.get('resource-id',''),n.get('text',''),n.get('bounds','')) for n in snapshot if n.get('resource-id') or n.get('text')],flush=True)
    raise AssertionError('Não apareceu: ' + resource)

def tap(resource):
    node = wait(resource)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    assert x2 > x1 and y2 > y1, resource
    if resource=='search_name':print('Alvo da busca:',node.attrib,flush=True)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def capture(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))
    print('Tela validada: '+name, flush=True)

def point(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    return (x1+x2)//2, (y1+y2)//2

def viewport(snapshot):
    root=next((n for n in snapshot if n.get('class','').endswith('ScrollView')),None)
    if root is None:root=find('main',snapshot)
    assert root is not None, 'A tela do app não está em primeiro plano'
    left,top_edge,right,bottom=map(int,re.findall(r'\d+',root.get('bounds','')))
    header=find('header',snapshot); nav=find('bottom_navigation',snapshot)
    if header is not None:top_edge=max(top_edge,int(re.findall(r'\d+',header.get('bounds',''))[3]))
    bottom=min(bottom,int(re.findall(r'\d+',nav.get('bounds',''))[1])) if nav is not None else bottom-24
    return left+4,top_edge,bottom

def show(resource, timeout=90):
    deadline=time.monotonic()+timeout
    while time.monotonic()<deadline:
        snapshot=nodes(); node=find(resource,snapshot)
        x,top_edge,bottom=viewport(snapshot)
        if node is not None:
            _,y=point(node)
            if top_edge+15 < y < bottom-15:return node
        region=bottom-top_edge
        adb('shell','input','swipe',str(x),str(top_edge+int(region*.8)),str(x),str(top_edge+int(region*.35)),'400')
        time.sleep(1)
    raise AssertionError('Controle não ficou visível: '+resource)

def tap_node(node):
    x,y=point(node); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(1)

def top():
    x,top_edge,bottom=viewport(nodes()); region=bottom-top_edge
    # Use the padding: a swipe inside a tall clickable portrait can open its detail.
    for _ in range(5):
        adb('shell','input','swipe',str(x),str(top_edge+int(region*.3)),str(x),str(top_edge+int(region*.85)),'250')
    time.sleep(1)

def gallery():
    wait('cover_title', 120)
    # Two actual tiles in the same row, rather than the old resizing carousel.
    tiles=[n for n in nodes() if n.get('resource-id','').endswith('/cover_image')]
    if not tiles: # Non-accessible images are intentionally omitted from UIAutomator.
        tiles=[n for n in nodes() if n.get('resource-id','').endswith('/cover_title')]
    assert len(tiles)>=2, 'Grade sem dois itens visíveis'
    a,b=tiles[:2]; ax,ay=point(a); bx,by=point(b)
    assert abs(ay-by)<55 and abs(ax-bx)>90, 'Galeria não está em duas colunas'

try:
    adb('shell','wm','size','390x844'); adb('shell','wm','density','160')
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','cmd','connectivity','airplane-mode','enable')
    adb('shell','svc','wifi','disable'); adb('shell','svc','data','disable')
    adb('logcat','-c')
    # No cache, no credentials, no network: the first launch must be populated.
    adb('shell','am','start','-n',PACKAGE+'/com.example.app_marvel.MainActivity')
    assert wait('user_name').get('text') == 'Prévia visual'
    assert 'horizonte' in wait('issue_title',30).get('text','').lower()
    capture('01-inicio'); show('featured_name',30); capture('02-destaque')
    top(); tap('issue_more'); wait('issue_heading',30); capture('03-quadrinho-detalhe')
    tap('header_back'); wait('issue_title'); tap('charactersFragment')
    wait('cover_title',30); gallery(); capture('04-personagens')
    for resource in ['header_back','origin_filter','gender_filter','team_filter','search_name']:
        target=wait(resource); x1,y1,x2,y2=map(int,re.findall(r'\d+',target.get('bounds','')))
        assert x2-x1>=44 and y2-y1>=44, 'Área de toque pequena: '+resource
    top(); tap('search_name'); adb('shell','input','text','Estrela')
    adb('shell','input','keyevent','66'); time.sleep(1); tap_node(show('cover_title'))
    assert 'Não informado' in wait('details_real_name').get('text','')
    assert 'Não informado' in wait('details_origin').get('text','')
    assert wait('details_description').get('text','')=='Descrição ainda não disponível.'
    capture('16-dados-ausentes'); tap('header_back'); top()
    # Clear the focused input with bounded keys, then submit to close the IME.
    tap('search_name')
    field=wait('search_name')
    print('Busca antes de limpar:',{key:field.get(key,'') for key in ('text','focused','bounds')},flush=True)
    assert field.get('focused')=='true', 'Busca sem foco'
    adb('shell','input','keyevent','123')  # MOVE_END
    adb('shell','input','keyevent',*(['67']*(len(field.get('text',''))+1)))
    assert wait('search_name').get('text','') in ('','Buscar personagens')
    adb('shell','input','keyevent','66'); wait('bottom_navigation'); wait('cover_title')
    tap('homeFragment'); wait('user_name')
    adb('shell','settings','put','system','font_scale','1.6')
    top(); show('issue_title'); capture('17-fonte-ampliada')
    tap('charactersFragment'); top(); show('cover_title'); capture('18-grade-fonte-ampliada')
    adb('shell','wm','size','320x720'); top(); show('cover_title'); capture('19-tela-estreita')
    # Narrow screens / large fonts show one column, without losing the main navigation.
    titles=[n for n in nodes() if n.get('resource-id','').endswith('/cover_title')]
    assert titles and all(point(n)[0]==point(titles[0])[0] for n in titles)
    x1,_,x2,_=map(int,re.findall(r'\d+',titles[0].get('bounds','')))
    assert x2-x1>240, 'Grade não passou a uma coluna em tela estreita'
    assert find('bottom_navigation') is not None
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','wm','size','700x1000'); top()
    # The centered content column must never stretch to the full tablet width.
    field=wait('search_name'); x1,_,x2,_=map(int,re.findall(r'\d+',field.get('bounds','')))
    assert x2-x1<=430, 'Conteúdo excedeu 430dp em tela larga'
    show('cover_title'); capture('20-tela-larga')
    adb('shell','wm','size','390x844')
    top(); wait('search_name'); wait('cover_title'); gallery()
    tap_node(show('cover_title')); wait('details_name',30); capture('05-personagem-detalhe')
    tap('header_back'); wait('cover_title'); tap('gender_filter'); wait('filter_apply'); capture('06-filtros')
    adb('shell','input','keyevent','4'); tap('storiesFragment'); wait('identity_name',30)
    capture('07-historias'); tap_node(show('identity_open')); wait('identity_name')
    show('appearance_title',30); capture('08-aparicoes'); tap_node(show('appearance_open'))
    wait('issue_heading'); tap('header_back'); top(); tap('header_back'); wait('identity_name')
    for route,heading,label in [('open_comics','issue_heading','quadrinhos'),
                               ('open_movies','movie_details_heading','filmes'),
                               ('open_series','series_details_heading','series')]:
        top(); tap_node(show(route)); show('cover_title',30); gallery(); capture('09-'+label)
        tap_node(show('cover_title')); wait(heading,30); capture('10-'+label+'-detalhe')
        tap('header_back'); wait('cover_title'); tap('header_back'); top(); wait('identity_name')
    top(); tap_node(show('open_arcs')); wait('arc_name',30); capture('11-arcos')
    tap_node(show('arc_source')); wait('arc_details_heading',30); capture('12-arco-detalhe')
    show('appearance_title',30); tap_node(show('appearance_open')); wait('issue_heading')
    tap('header_back'); top(); tap('header_back'); wait('arc_name'); top()
    tap('arc_search'); adb('shell','input','text','zzpreviewzz'); tap_node(show('arcs_search_button'))
    assert 'zzpreviewzz' in wait('status_title',30).get('text','').lower()
    capture('13-arcos-vazio'); tap('retry_button'); wait('arc_name',30)
    tap('header_back'); tap('createHeroFragment'); wait('hero_name'); capture('14-formulario')
    tap('hero_name'); adb('shell','input','text','Aurora'); adb('shell','input','keyevent','4')
    tap('hero_real_name'); adb('shell','input','text','Lia'); adb('shell','input','keyevent','4')
    tap_node(show('hero_next')); wait('hero_origin'); tap('hero_origin'); wait('filter_heading')
    capture('15-origens'); adb('shell','input','keyevent','4')
    logs=adb('logcat','-d','-s','AndroidRuntime:E').decode(errors='replace')
    assert 'FATAL EXCEPTION' not in logs, logs
    (OUT/'verification.txt').write_text('Prévia sem credenciais e offline: início, personagens, quadrinhos, filmes, séries, arcos, detalhes, filtros, estado vazio, formulário e fonte 160% verificados.\n')
    print('Validação visual offline aprovada.')
except Exception:
    capture('failure'); (OUT/'failure.xml').write_bytes(adb('exec-out','cat','/sdcard/arquivo-ui.xml'))
    (OUT/'failure.txt').write_text(traceback.format_exc())
    raise
