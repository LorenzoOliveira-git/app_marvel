"""Uma passagem de integração real e fluxo nativo do rascunho. Sem testes unitários."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = 'com.example.app_marvel'
OUT = Path('app/build/catalog-check'); PREVIEW = Path('app/build/previews')
OUT.mkdir(parents=True, exist_ok=True); PREVIEW.mkdir(parents=True, exist_ok=True)
W, H = 430, 932

def adb(*args, check=True):
    result = subprocess.run(['adb', *args], capture_output=True, timeout=40)
    if check: result.check_returncode()
    return result

def integration(name):
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'run-as', PACKAGE, 'rm', '-f', f'files/diagnostics/{name}.json')
    adb('shell', 'am', 'start', '-n', PACKAGE + '/.diagnostics.HeroFormCheckActivity', '--es', 'check', name)
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        result = adb('shell', 'run-as', PACKAGE, 'cat', f'files/diagnostics/{name}.json', check=False)
        if result.returncode == 0:
            report = json.loads(result.stdout)
            (OUT / (name + '.json')).write_text(json.dumps(report, ensure_ascii=False, indent=2))
            assert report['success'], name
            return report
        time.sleep(2)
    raise RuntimeError('Catálogos/tradução não terminaram: ' + name)

def network_ready():
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        if re.search(r'Capabilities:.*\bVALIDATED\b', adb('shell', 'dumpsys', 'connectivity').stdout.decode(errors='replace')): return
        time.sleep(2)
    raise RuntimeError('Rede não validada.')

network_ready()
print('Conferir origens e dois lotes de poderes com traduções reais.', flush=True)
online = integration('hero-form')
try:
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'enable')
    adb('shell', 'svc', 'wifi', 'disable'); adb('shell', 'svc', 'data', 'disable')
    offline = integration('hero-form-cache')
    assert online == offline, 'Catálogos e tradução divergiram no cache offline.'
finally:
    adb('shell', 'cmd', 'connectivity', 'airplane-mode', 'disable', check=False)
    adb('shell', 'svc', 'wifi', 'enable', check=False); adb('shell', 'svc', 'data', 'enable', check=False)
network_ready()

def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/hero-window.xml')
    return list(ET.fromstring(adb('exec-out', 'cat', '/sdcard/hero-window.xml').stdout).iter('node'))

def bounds(node): return tuple(map(int, re.findall(r'\d+', node.get('bounds', ''))))

def find(snapshot, resource=None, text=None):
    return next((n for n in snapshot if (resource and n.get('resource-id', '').endswith('/' + resource)) or (text and n.get('text') == text)), None)

def top():
    for _ in range(5): adb('shell', 'input', 'swipe', str(W//2), str(H//3), str(W//2), str(H*4//5), '200')

def tap(resource=None, text=None):
    for attempt in range(14):
        snapshot = nodes(); node = find(snapshot, resource, text)
        nav = find(snapshot, 'bottom_navigation'); bottom = bounds(nav)[1] if nav is not None else H
        if node is not None:
            x1, y1, x2, y2 = bounds(node)
            if x2 > x1 and y2 > y1 and (resource in ['createHeroFragment', 'homeFragment'] or (y1+y2)//2 < bottom):
                adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2)); time.sleep(.4); return
        adb('shell', 'input', 'swipe', str(W//2), str(H*3//4), str(W//2), str(H//3), '250')
        if attempt == 7: top()
    raise RuntimeError('Controle não encontrado: ' + str(resource or text))

def wait(resource, text=None):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        node = find(nodes(), resource)
        if node is not None and (text is None or text in node.get('text', '')): return node
        if resource == 'hero_power_status' and node is None:
            adb('shell', 'input', 'swipe', str(W//2), str(H*3//4), str(W//2), str(H//3), '250')
        time.sleep(1)
    raise RuntimeError('Conteúdo não apareceu: ' + resource)

def capture(name):
    data = adb('exec-out', 'screencap', '-p').stdout
    assert data.startswith(b'\x89PNG\r\n\x1a\n')
    (PREVIEW / (name + '.png')).write_bytes(data)
    adb('shell', 'uiautomator', 'dump', '/sdcard/hero-window.xml')
    (PREVIEW / (name + '.xml')).write_bytes(adb('exec-out', 'cat', '/sdcard/hero-window.xml').stdout)

def enter(resource, value):
    tap(resource=resource); adb('shell', 'input', 'text', value.replace(' ', '%s'))
    adb('shell', 'input', 'keyevent', '4'); time.sleep(.3)

adb('shell', 'wm', 'size', f'{W}x{H}'); adb('shell', 'wm', 'density', '160')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
adb('shell', 'am', 'force-stop', PACKAGE); adb('shell', 'am', 'start', '-n', PACKAGE + '/.MainActivity'); time.sleep(2)
tap(text='Explorar sem entrar'); tap(resource='createHeroFragment'); wait('hero_heading', 'Quem é'); capture('hero-identity')
tap(resource='hero_next'); wait('hero_name'); assert any(n.get('text') == 'Preencha este campo.' for n in nodes()); capture('hero-required')
enter('hero_name', 'Guardiao Aurora'); enter('hero_real_name', 'Alex Sol')
tap(resource='hero_next'); wait('hero_heading', 'De onde'); top()
tap(resource='hero_next'); top(); wait('hero_origin_error'); capture('hero-abilities-required')
# Opções vêm do diagnóstico real, não de uma lista simulada.
tap(resource='hero_origin'); tap(text=online['origins'][0]['label']); top()
tap(text=online['powers'][0]['label']); wait('hero_selected_count', '1'); capture('hero-selected')
tap(resource='hero_power_more'); tap(resource='hero_power_status'); wait('hero_power_status', '40'); top()
enter('hero_description', 'Protege sua cidade com coragem e usa seus poderes para ajudar as pessoas.')
tap(resource='hero_next'); top(); review = wait('hero_review_data', 'Guardiao Aurora')
assert 'Alex Sol' in review.get('text') and online['powers'][0]['label'] in review.get('text') and 'Não informado' in review.get('text')
capture('hero-review')
tap(resource='homeFragment'); tap(resource='createHeroFragment'); top(); wait('hero_review_data', 'Guardiao Aurora')
# Mudanças de configuração recriam a tela e exercitam a preservação do rascunho.
adb('shell', 'settings', 'put', 'system', 'font_scale', '2.0'); time.sleep(2); top(); capture('hero-review-font200')
tap(resource='hero_previous'); top(); wait('hero_name', 'Guardiao Aurora')
adb('shell', 'wm', 'size', '320x640'); W, H = 320, 640
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0'); time.sleep(2); top(); capture('hero-identity-small')
tap(resource='hero_next'); top(); wait('hero_selected_count', '1'); assert wait('hero_origin').get('text') == online['origins'][0]['label']
tap(resource='hero_next'); top(); wait('hero_review_data', 'Guardiao Aurora')
(OUT / 'hero-form-ui.json').write_text(json.dumps({'success': True, 'required_fields': True, 'selected_source_ids': {'origin': online['origins'][0]['id'], 'power': online['powers'][0]['id']}, 'paging_preserves_selection': True, 'navigation_and_configuration_preserve_draft': True, 'synthetic_user_input_only': True, 'paid_generation_called': False, 'captures': 7}, indent=2))
adb('shell', 'wm', 'size', '430x932'); adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
print('Catálogos reais, cache offline, validação, seleção, revisão e preservação do rascunho conferidos.', flush=True)
