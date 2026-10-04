import subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
source=Path('.github/scripts/capture-preview.py').read_text()
scope={}
exec(source.split('# Tela com')[0],scope)
adb=scope['adb'];tap=scope['tap_label'];capture=scope['capture']
adb('shell','wm','size','430x932');adb('shell','wm','density','160')
adb('logcat','-c')
adb('shell','am','start','-n','com.example.app_marvel/.MainActivity');time.sleep(3)
tap('Explorar sem entrar');tap('Personagens')
for attempt in range(30):
 adb('shell','uiautomator','dump','/sdcard/window.xml')
 xml=adb('exec-out','cat','/sdcard/window.xml')
 if b'character_name' in xml: break
 time.sleep(2)
else: raise RuntimeError('Catálogo não carregou')
capture('before')
adb('shell','wm','size','640x1000');time.sleep(2)
adb('shell','wm','size','320x640');time.sleep(2)
adb('shell','settings','put','system','font_scale','2.0');time.sleep(2)
capture('large-font')
adb('shell','settings','put','system','font_scale','1.0')
adb('shell','wm','size','430x932')
time.sleep(5)
capture('restored-before-tap')
adb('shell','uiautomator','dump','/sdcard/window.xml')
xml=adb('exec-out','cat','/sdcard/window.xml');Path('app/build/previews/restored.xml').write_bytes(xml)
runtime=adb('logcat','-d','-s','AndroidRuntime:E')
Path('app/build/previews/runtime.txt').write_bytes(runtime)
if b'FATAL EXCEPTION' in runtime: raise RuntimeError('Exceção durante recriação')
tap('Início');capture('home-after-recreation')
print('APK original permaneceu ativo após recriar a tela.')
