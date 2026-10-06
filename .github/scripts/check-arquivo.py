"""Smoke visual: tela inicial, retorno de detalhe, erro offline e fonte ampliada."""
from pathlib import Path
import json
import re
import subprocess
import time
import traceback
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.app_marvel'
OUT = Path('app/build/arquivo-preview')
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
            return list(ET.fromstring(raw[start:] if start >= 0 else raw).iter('node'))
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
    raise AssertionError('Não apareceu: ' + resource)

def tap(resource):
    node = wait(resource)
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    assert x2 > x1 and y2 > y1, resource
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def capture(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

def point(node):
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
    return (x1+x2)//2, (y1+y2)//2

def show(resource, timeout=90):
    deadline = time.monotonic()+timeout
    while time.monotonic()<deadline:
        snapshot = nodes(); node = find(resource, snapshot)
        if node is not None:
            x, y = point(node)
            if 110 < y < 720:
                return node
        adb('shell', 'input', 'swipe', '195', '710', '195', '450', '400')
        time.sleep(1)
    raise AssertionError('Controle não ficou visível: '+resource)

def tap_node(node):
    x,y=point(node); adb('shell', 'input', 'tap', str(x), str(y)); time.sleep(1)

def top():
    for _ in range(5):
        adb('shell', 'input', 'swipe', '195', '320', '195', '710', '250')
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
    adb('shell', 'wm', 'size', '390x844')
    adb('shell', 'wm', 'density', '160')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE+'/.MainActivity')
    time.sleep(3)
    wait('email'); capture('arquivo-login')
    # Primeira abertura sem cache: erro recuperável mantém cabeçalho e navegação.
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    tap('browse'); wait('retry_button')
    assert find('user_name') is not None and find('bottom_navigation') is not None
    capture('arquivo-offline')
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
    adb('shell', 'svc', 'wifi', 'enable'); adb('shell', 'svc', 'data', 'enable')
    time.sleep(8)
    tap('retry_button'); wait('issue_title', 120)
    title = find('issue_title').get('text', '')
    if find('retry_button') is not None:
        tap('retry_button'); wait('featured_name', 120)
    capture('arquivo-inicio')
    adb('shell', 'input', 'swipe', '190', '650', '190', '300', '400')
    capture('arquivo-destaque')
    adb('shell', 'input', 'swipe', '190', '250', '190', '720', '400')
    tap('issue_more'); wait('issue_heading')
    assert find('bottom_navigation') is None, 'Navegação principal apareceu no detalhe'
    capture('arquivo-detalhe')
    tap('header_back'); assert wait('issue_title').get('text', '') == title
    assert find('bottom_navigation') is not None, 'Barra não voltou ao início'
    tap('charactersFragment'); wait('search_name')
    tap('search_name'); adb('shell', 'input', 'text', 'Spider-Man'); adb('shell', 'input', 'keyevent', '66')
    wait('cover_title',120); gallery(); capture('arquivo-personagens-grade')
    searched = find('search_name').get('text','')
    tap_node(show('cover_title')); wait('details_name',120)
    assert find('bottom_navigation') is None
    capture('arquivo-personagem-detalhe')
    tap('header_back'); top(); assert wait('search_name').get('text','') == searched
    # A draft filter can be cancelled without modifying the real query.
    tap('gender_filter'); wait('filter_apply'); capture('arquivo-filtros')
    adb('shell','input','keyevent','4'); assert wait('search_name').get('text','') == searched
    tap('gender_filter'); wait('filter_apply')
    choices=[n for n in nodes() if n.get('checkable')=='true']
    assert len(choices)>=2
    tap_node(choices[1]); tap('filter_apply'); wait('cover_title',120)
    assert find('clear_filters') is not None
    tap('clear_filters'); wait('cover_title',120)
    tap('homeFragment'); wait('user_name'); tap('storiesFragment'); show('open_comics')
    for route,heading,label in [('open_comics','issue_heading','quadrinhos'),
                                 ('open_movies','movie_details_heading','filmes'),
                                 ('open_series','series_details_heading','series')]:
        top(); tap_node(show(route)); show('cover_title',120); gallery(); capture('arquivo-'+label+'-grade')
        if route == 'open_comics':
            top(); tap_node(show('comics_volume')); wait('comics_volume_search',120)
            tap('comics_volume_search'); adb('shell','input','text','Marvel')
            time.sleep(2); capture('arquivo-filtro-teclado')
            action=wait('filter_apply'); _,y=point(action)
            assert y<570, 'Ação de filtro coberta pelo teclado'
            adb('shell','input','keyevent','4'); adb('shell','input','keyevent','4')
            show('cover_title',120)
        tap_node(show('cover_title')); wait(heading,120)
        assert find('bottom_navigation') is None, label
        capture('arquivo-'+label+'-detalhe'); tap('header_back'); wait('cover_title')
        tap('header_back'); show('open_comics')
    tap('profileFragment'); wait('heading'); capture('arquivo-perfil')
    tap('createHeroFragment'); wait('hero_name'); capture('arquivo-criar-heroi')
    tap('hero_name'); adb('shell','input','text','Arquivo'); capture('arquivo-formulario-teclado')
    adb('shell','input','keyevent','4')
    tap('hero_real_name'); adb('shell','input','text','Explorador')
    adb('shell','input','keyevent','4'); tap_node(show('hero_next')); wait('hero_origin')
    deadline=time.monotonic()+120
    while time.monotonic()<deadline:
        origin=find('hero_origin')
        if origin is not None and origin.get('enabled')=='true': break
        time.sleep(2)
    else: raise AssertionError('Origens não ficaram disponíveis')
    tap('hero_origin'); wait('filter_heading'); capture('arquivo-origem-painel')
    options=[n for n in nodes() if n.get('checkable')=='true']
    assert options, 'Painel de origem sem escolhas'
    tap_node(options[0]); wait('hero_origin')
    chosen=find('hero_origin').get('text')
    tap('hero_origin'); wait('filter_heading'); tap('filter_apply'); wait('hero_origin')
    assert find('hero_origin').get('text')==chosen, 'Cancelar alterou a origem'
    # Nenhuma ação de geração/salvamento é chamada neste smoke.

    tap('homeFragment'); wait('user_name')
    # Aumento real de fonte; verifica área útil e destino acessível sem depender de gesto.
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.6')
    wait('bottom_navigation'); capture('arquivo-fonte-160')
    tap('charactersFragment'); wait('expanded_screen_title'); wait('search_name'); capture('arquivo-personagens-fonte-160')
    tap('homeFragment'); wait('user_name')
    adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','wm','size','320x640'); time.sleep(2)
    tap('charactersFragment'); wait('search_name'); capture('arquivo-personagens-320')
    adb('shell','wm','size','390x844'); time.sleep(2)
    fatal = adb('logcat', '-d', '-s', 'AndroidRuntime:E').decode(errors='replace')
    assert 'FATAL EXCEPTION' not in fatal, 'Exceção Android durante o smoke visual'
    (OUT/'resultado.json').write_text(json.dumps({'success': True, 'checks': [
        'login', 'offline_recoverable', 'home_real_issue', 'detail_without_navigation',
        'return_preserves_issue', 'grid_characters_comics_movies_series', 'search_return_preserved',
        'filter_draft_cancel_apply_clear', 'profile_create_keyboard', 'origin_sheet_choose_cancel', 'font_scale_1.6_navigation', 'no_android_crash']}, indent=2))
    print('Tela inicial, erro offline, detalhe/retorno e navegação com fonte 160% conferidos.')
except Exception:
    (OUT/'failure.txt').write_text(traceback.format_exc())
    try:
        capture('arquivo-falha')
        (OUT/'android-runtime.txt').write_bytes(adb('logcat', '-d', '-s', 'AndroidRuntime:E'))
    except Exception:
        pass
    raise
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset'); adb('shell', 'wm', 'density', 'reset')
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
    adb('shell', 'svc', 'wifi', 'enable'); adb('shell', 'svc', 'data', 'enable')
    # Próximos diagnósticos iniciam sem preservar a navegação desta verificação.
    adb('shell', 'am', 'force-stop', PACKAGE)
