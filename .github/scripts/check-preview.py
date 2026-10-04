"""Verificação ADB da variante preview com rede desligada; não cria contas externas."""
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = "com.example.app_marvel.preview"
OUT = Path("app/build/preview-check")
OUT.mkdir(parents=True, exist_ok=True)


def adb(*args, check=True):
    return subprocess.run(["adb", *args], check=check, capture_output=True, timeout=30).stdout


def window():
    adb("shell", "uiautomator", "dump", "/sdcard/preview-window.xml")
    xml = adb("exec-out", "cat", "/sdcard/preview-window.xml")
    (OUT / "last-window.xml").write_bytes(xml)
    return ET.fromstring(xml)


def tap(label=None, field=None):
    for attempt in range(8):
        for node in window().iter("node"):
            matches = node.get("resource-id", "").endswith(":id/" + field) if field else (
                node.get("text", "").casefold() == label.casefold()
                or node.get("content-desc", "").casefold().split(",")[0] == label.casefold())
            if not matches:
                continue
            x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds", "")))
            if x2 > x1 and y2 > y1:
                adb("shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1)
                return
        adb("shell", "input", "swipe", "215", "740", "215", "350", "400")
    raise RuntimeError("Controle não encontrado: " + (field or label))


def fill(field, value):
    tap(field=field)
    adb("shell", "input", "text", value)
    adb("shell", "input", "keyevent", "4")
    time.sleep(0.5)


def expect(text):
    if not any(node.get("text") == text for node in window().iter("node")):
        raise RuntimeError("Conteúdo esperado não encontrado: " + text)


def capture(name):
    (OUT / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))


def start():
    adb("shell", "am", "start", "-W", "-n", PACKAGE + "/com.example.app_marvel.MainActivity")
    time.sleep(2)


# Verifica o isolamento pelo conteúdo compilado, não apenas pelas pastas do projeto.
with zipfile.ZipFile("app/build/outputs/apk/debug/app-debug.apk") as archive:
    if any(b"PreviewAuthRepository" in archive.read(name) for name in archive.namelist()
           if re.fullmatch(r"classes\d*\.dex", name)):
        raise RuntimeError("Repositório de demonstração encontrado no APK normal.")

adb("shell", "wm", "size", "430x932")
adb("shell", "wm", "density", "160")
adb("shell", "settings", "put", "system", "font_scale", "1.0")
adb("shell", "pm", "clear", PACKAGE)
try:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    start()
    capture("login")
    fill("email", "teste@example.com")
    fill("password", "preview123")
    tap("Entrar")
    tap("Perfil")
    expect("teste@example.com")
    capture("perfil-login")
    adb("shell", "am", "force-stop", PACKAGE)
    start()
    tap("Perfil")
    expect("teste@example.com")
    capture("perfil-apos-reiniciar")
    tap("Sair da conta")
    tap("Não tem conta? Cadastre-se")
    fill("name", "Lorenzo")
    fill("email", "cadastro@example.com")
    fill("password", "preview123")
    fill("confirmation", "preview123")
    tap("Criar conta")
    tap("Perfil")
    expect("Lorenzo")
    expect("cadastro@example.com")
    capture("perfil-cadastro")
    tap("Sair da conta")
    adb("shell", "am", "force-stop", PACKAGE)
    start()
    tap("Explorar sem entrar")
    tap("Perfil")
    expect("Seu perfil")
    capture("perfil-apos-sair")
    (OUT / "result.json").write_text(json.dumps({
        "package": PACKAGE, "offline_login": True, "offline_register": True,
        "session_after_restart": True, "sign_out_after_restart": True,
        "preview_repository_absent_in_debug_apk": True
    }, indent=2))
finally:
    adb("shell", "cmd", "connectivity", "airplane-mode", "disable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "svc", "data", "enable", check=False)
print("Preview: login, cadastro, persistência e saída verificados offline; APK normal sem mock.")
