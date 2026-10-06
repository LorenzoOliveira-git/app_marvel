"""Smoke visual: tela inicial, retorno de detalhe, erro offline e fonte ampliada."""
from pathlib import Path
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.app_marvel'
OUT = Path('app/build/arquivo-preview')
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.run(['adb', *args], capture_output=True, check=True, timeout=30).stdout

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/arquivo-ui.xml')
    return list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/arquivo-ui.xml')).iter('node'))

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
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', node['bounds']))
    assert x2 > x1 and y2 > y1, resource
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

def capture(name):
    (OUT/(name+'.png')).write_bytes(adb('exec-out', 'screencap', '-p'))

try:
    adb('shell', 'wm', 'size', '390x844')
    adb('shell', 'wm', 'density', '160')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-n', PACKAGE+'/.MainActivity')
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
    title = find('issue_title')['text']
    if find('retry_button') is not None:
        tap('retry_button'); wait('featured_name', 120)
    capture('arquivo-inicio')
    adb('shell', 'input', 'swipe', '190', '650', '190', '300', '400')
    capture('arquivo-destaque')
    adb('shell', 'input', 'swipe', '190', '250', '190', '720', '400')
    tap('issue_more'); wait('issue_heading')
    assert find('bottom_navigation') is None, 'Navegação principal apareceu no detalhe'
    capture('arquivo-detalhe')
    tap('header_back'); assert wait('issue_title')['text'] == title
    assert find('bottom_navigation') is not None, 'Barra não voltou ao início'
    # Aumento real de fonte; verifica área útil e destino acessível sem depender de gesto.
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.6')
    wait('bottom_navigation'); capture('arquivo-fonte-160')
    tap('charactersFragment'); wait('screen_title')
    tap('homeFragment'); wait('user_name')
    fatal = adb('logcat', '-d', '-s', 'AndroidRuntime:E').decode(errors='replace')
    assert 'FATAL EXCEPTION' not in fatal, 'Exceção Android durante o smoke visual'
    (OUT/'resultado.json').write_text(json.dumps({'success': True, 'checks': [
        'login', 'offline_recoverable', 'home_real_issue', 'detail_without_navigation',
        'return_preserves_issue', 'font_scale_1.6_navigation', 'no_android_crash']}, indent=2))
    print('Tela inicial, erro offline, detalhe/retorno e navegação com fonte 160% conferidos.')
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset'); adb('shell', 'wm', 'density', 'reset')
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable')
    adb('shell', 'svc', 'wifi', 'enable'); adb('shell', 'svc', 'data', 'enable')
    # Próximos diagnósticos iniciam sem preservar a navegação desta verificação.
    adb('shell', 'am', 'force-stop', PACKAGE)
