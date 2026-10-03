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
    adb("shell", "uiautomator", "dump", "/sdcard/marv-window.xml")
    root = ET.fromstring(adb("exec-out", "cat", "/sdcard/marv-window.xml"))
    for node in root.iter("node"):
        if node.get("text", "").casefold() == label.casefold():
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            adb("shell", "input", "tap", str((x1+x2)//2), str((y1+y2)//2))
            time.sleep(1)
            return
    raise RuntimeError("Controle não encontrado: " + label)

def capture(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))

adb("shell", "am", "start", "-n", "com.example.app_marvel/.MainActivity")
time.sleep(3)
capture("login")
tap_label("Não tem conta? Cadastre-se")
capture("cadastro")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap_label("Explorar sem entrar")
capture("inicio")
adb("shell", "input", "swipe", "540", "1650", "540", "480", "600")
time.sleep(1)
capture("inicio-rolagem")
adb("shell", "settings", "put", "system", "font_scale", "2.0")
time.sleep(2)
capture("inicio-fonte-200")
adb("shell", "settings", "put", "system", "font_scale", "1.0")
