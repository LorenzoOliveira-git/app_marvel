"""Verificação Android real: ML Kit, persistência após matar processo e recuperação do cache offline."""
import json
from pathlib import Path
import subprocess
import time

PACKAGE = "com.example.app_marvel"
OUTPUT = Path("app/build/data-check")
OUTPUT.mkdir(parents=True, exist_ok=True)


def adb(*arguments, check=True):
    return subprocess.run(["adb", *arguments], check=check, capture_output=True, text=True)


def run_check(name):
    adb("shell", "am", "force-stop", PACKAGE)
    adb("shell", "run-as", PACKAGE, "rm", "-f", f"files/diagnostics/{name}.json")
    adb("shell", "am", "start", "-n", PACKAGE + "/.diagnostics.DataCheckActivity", "--es", "check", name)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        data = adb("shell", "run-as", PACKAGE, "cat", f"files/diagnostics/{name}.json", check=False)
        if data.returncode == 0:
            report = json.loads(data.stdout)
            (OUTPUT / f"{name}.json").write_text(json.dumps(report, ensure_ascii=False, indent=2))
            if not report["success"] or not report.get("translated"):
                raise RuntimeError("Falha real no SDK/cache: " + str(report.get("failure")))
            return report
        time.sleep(2)
    raise RuntimeError("O SDK não concluiu a verificação; não declarar tradução verificada.")


first = run_check("translation")
if first["from_cache"]:
    raise RuntimeError("A verificação inicial precisa executar o tradutor real.")
try:
    adb("shell", "cmd", "connectivity", "airplane-mode", "enable")
    adb("shell", "svc", "wifi", "disable")
    adb("shell", "svc", "data", "disable")
    saved = run_check("cache")
    if not saved["from_cache"] or saved["translated"] != first["translated"]:
        raise RuntimeError("O resultado não veio do cache persistente após reiniciar o processo.")
finally:
    adb("shell", "cmd", "connectivity", "airplane-mode", "disable", check=False)
    adb("shell", "svc", "wifi", "enable", check=False)
    adb("shell", "svc", "data", "enable", check=False)
print("ML Kit executado; tradução e cache após reiniciar o processo conferidos.")
