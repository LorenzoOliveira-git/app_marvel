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
            if node.get("text", "").casefold() == label.casefold() or node.get("content-desc", "").casefold().split(",")[0] == label.casefold():
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

# Tela com a mesma largura da referência; a splash é capturada durante início a frio.
adb("shell", "wm", "size", "430x932")
adb("shell", "wm", "density", "160")
adb("shell", "am", "force-stop", "com.example.app_marvel")
launch = subprocess.Popen(["adb", "shell", "am", "start", "-W", "-n", "com.example.app_marvel/.MainActivity"], stdout=subprocess.DEVNULL)
for index in range(8):
    (OUT / ("abertura-%02d.png" % index)).write_bytes(adb("exec-out", "screencap", "-p"))
    time.sleep(0.08)
launch.wait(timeout=30)
time.sleep(5)
capture("login")
tap_label("Entrar")
adb("shell", "input", "keyevent", "4")
capture("login-validacao")
tap_label("Não tem conta? Cadastre-se")
capture("cadastro")
adb("shell", "input", "swipe", "215", "750", "215", "350", "600")
capture("cadastro-rolagem")
adb("shell", "input", "keyevent", "4")
time.sleep(1)
tap_label("Explorar sem entrar")
capture("inicio")
size = list(map(int, re.findall(r"\d+", adb("shell", "wm", "size").decode())[:2]))
w, h = size
adb("shell", "input", "swipe", str(w//2), str(h*3//4), str(w//2), str(h//3), "600")
time.sleep(1)
capture("inicio-rolagem")
for label, filename in [("Personagens", "personagens"), ("Histórias", "historias"), ("Criar herói", "criar-heroi"), ("Perfil", "perfil")]:
    tap_label(label)
    capture(filename)
    adb("shell", "uiautomator", "dump", "/sdcard/marv-window.xml")
    text = adb("exec-out", "cat", "/sdcard/marv-window.xml").decode().casefold()
    for unwanted in ["ainda não está disponível", "em um próximo bloco", "não estão disponíveis"]:
        if unwanted in text:
            raise RuntimeError("Aviso de desenvolvimento exibido: " + unwanted)
tap_label("Início")
adb("shell", "wm", "size", "320x640")
time.sleep(2)
capture("inicio-tela-pequena")
adb("shell", "settings", "put", "system", "font_scale", "2.0")
time.sleep(2)
capture("inicio-fonte-200")
adb("shell", "settings", "put", "system", "font_scale", "1.0")
