"""Capturas de diagnóstico via ADB; não cria contas nem executa testes unitários."""
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

OUT = Path("app/build/previews")
OUT.mkdir(parents=True, exist_ok=True)

def adb(*args):
    return subprocess.check_output(["adb", *args], timeout=30)

def tap_label(label):
    size = list(map(int, re.findall(r"\d+", adb("shell", "wm", "size").decode())[:2]))
    width, height = size
    for attempt in range(7):
        adb("shell", "uiautomator", "dump", "/sdcard/marv-window.xml")
        xml = adb("exec-out", "cat", "/sdcard/marv-window.xml")
        (OUT / "last-window.xml").write_bytes(xml)
        root = ET.fromstring(xml)
        for node in root.iter("node"):
            if node.get("text", "").casefold() == label.casefold():
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
                if x2 > x1 and y2 > y1:
                    adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
                    time.sleep(2)
                    return
        adb("shell", "input", "swipe", str(width//2), str(height*3//4),
            str(width//2), str(height//3), "500")
        time.sleep(1)
    raise RuntimeError("Controle não encontrado: " + label)

def capture(name):
    time.sleep(2)
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))

adb("shell", "am", "start", "-W", "-n", "com.example.app_marvel/.MainActivity")
time.sleep(8)
capture("login")
tap_label("Não tem conta? Cadastre-se")
capture("cadastro")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap_label("Explorar sem entrar")
capture("inicio")
size = list(map(int, re.findall(r"\d+", adb("shell", "wm", "size").decode())[:2]))
w, h = size
adb("shell", "input", "swipe", str(w//2), str(h*3//4), str(w//2), str(h//3), "600")
time.sleep(1)
capture("inicio-rolagem")
adb("shell", "settings", "put", "system", "font_scale", "2.0")
time.sleep(2)
capture("inicio-fonte-200")
adb("shell", "settings", "put", "system", "font_scale", "1.0")
