#!/usr/bin/env python3
"""Configuração local e inspeção REAL da ComicVine. Nunca imprime ou grava a chave em relatórios."""
import argparse
import datetime
import getpass
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BASE = "https://comicvine.gamespot.com/api/"
PACKAGE = "com.example.app_marvel"


def key_from_env_file(path):
    try:
        contents = path.read_text(encoding="utf-8-sig")
    except FileNotFoundError:
        return None
    except (OSError, UnicodeError):
        raise RuntimeError("Não foi possível ler o .env local.") from None
    key = None
    for line in contents.splitlines():
        line = line.strip()
        if line.startswith("export "):
            line = line[7:].lstrip()
        name, separator, value = line.partition("=")
        if separator and name.strip() == "COMICVINE_API_KEY":
            value = value.strip()
            if len(value) >= 2 and value[0] in {"'", '"'} and value[-1] == value[0]:
                value = value[1:-1]
            key = value
    # Apenas lê o valor literal; não executa comandos nem expande variáveis.
    return key


def read_key():
    key = os.environ.get("COMICVINE_API_KEY")
    if not key:
        key = key_from_env_file(Path(__file__).resolve().parents[1] / ".env")
    if not key:
        if not sys.stdin.isatty():
            raise RuntimeError("Configure COMICVINE_API_KEY no .env local, no ambiente privado ou no secret do GitHub.")
        key = getpass.getpass("Chave ComicVine (entrada oculta, não será salva no computador): ").strip()
    if not re.fullmatch(r"[A-Za-z0-9_-]{16,256}", key):
        raise RuntimeError("Formato de chave inválido.")
    return key


def sanitized(value, key):
    if isinstance(value, dict):
        return {name: sanitized(item, key) for name, item in value.items()
                if name.lower() not in {"api_key", "apikey", "authorization"}}
    if isinstance(value, list):
        return [sanitized(item, key) for item in value]
    if isinstance(value, str):
        value = value.replace(key, "[redacted]")
        if value.startswith(("https://", "http://")):
            try:
                parts = urllib.parse.urlsplit(value)
                query = [(name, item) for name, item in urllib.parse.parse_qsl(parts.query)
                         if name.lower() not in {"api_key", "apikey"}]
                value = urllib.parse.urlunsplit(parts._replace(query=urllib.parse.urlencode(query)))
            except ValueError:
                return "[invalid URL]"
    return value


def fetch(path, params, key, max_bytes=2_000_000):
    # Parâmetros e campos desta inspeção são sondagens; não afirmam suporte de filtros.
    url = BASE + path + "?" + urllib.parse.urlencode({**params, "format": "json", "api_key": key})
    request = urllib.request.Request(url, headers={
        "Accept": "application/json", "User-Agent": "SuaMarvel-Inspection/1.0 (personal non-commercial app)"})
    # Não seguir redirects evita encaminhar uma URL com credencial para outro destino.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, req, fp, code, msg, headers, newurl):
            return None
    opener = urllib.request.build_opener(NoRedirect)
    try:
        with opener.open(request, timeout=25) as response:
            body = response.read(max_bytes + 1)
            if len(body) > max_bytes:
                return {"failure": "response_too_large"}
            return {"http_status": response.status, "body_bytes": len(body), "response": sanitized(json.loads(body), key)}
    except urllib.error.HTTPError as error:
        return {"http_status": error.code, "failure": "http"}
    except (OSError, ValueError):
        # Mensagens do urllib podem conter a URL/chave: não serializar exceções.
        return {"failure": "network_or_json"}
    finally:
        time.sleep(1.5)


def inspect(key, output):
    report = {"schema_version": 1, "captured_at": datetime.datetime.now(datetime.timezone.utc).isoformat(),
              "purpose": "Respostas reais para verificar contrato. Amostras não provam um filtro universal.",
              "probes": {}}
    def probe(label, path, params):
        result = fetch(path, params, key)
        report["probes"][label] = {"resource_path": path, "query_without_key": params, **result}
        return result.get("response", {})
    publishers = probe("publishers_name_marvel", "publishers/", {"limit": 100, "filter": "name:Marvel"})
    records = publishers.get("results", []) if isinstance(publishers, dict) else []
    candidates = [item for item in records if isinstance(item, dict)
                  and str(item.get("name", "")).casefold() == "marvel"
                  and isinstance(item.get("id"), int)] if isinstance(records, list) else []
    report["marvel_candidates"] = [{"id": item["id"], "name": item["name"],
                                    "site_detail_url": item.get("site_detail_url")} for item in candidates]
    probe("characters_baseline", "characters/", {"limit": 20})
    probe("issues_baseline", "issues/", {"limit": 10})
    if len(candidates) == 1:
        publisher = candidates[0]
        detail_url = publisher.get("api_detail_url", "")
        if isinstance(detail_url, str):
            parts = urllib.parse.urlsplit(detail_url)
            if parts.hostname == "comicvine.gamespot.com" and re.fullmatch(
                    r"/api/publisher/[0-9]+-[0-9]+/", parts.path):
                probe("marvel_publisher_detail", parts.path.removeprefix("/api/"), {})
        probe("characters_publisher_id_probe", "characters/",
              {"limit": 20, "filter": "publisher:" + str(publisher["id"])})
        report["publisher_filter_verified"] = False
        report["next_review"] = "Comparar amostra filtrada, totais e relações antes de implementar o catálogo."
    else:
        report["next_review"] = "Resolver entidade Marvel; a sondagem não identificou candidato único."
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print("Relatório sanitizado salvo em", output)


def provision(key, serial):
    adb = ["adb"] + (["-s", serial] if serial else [])
    command = ("umask 077; mkdir -p no_backup; cat > no_backup/comicvine-api-key.tmp "
               "&& mv no_backup/comicvine-api-key.tmp no_backup/comicvine-api-key")
    result = subprocess.run(adb + ["shell", "run-as", PACKAGE, "sh", "-c", shlex.quote(command)],
                            input=key.encode("utf-8"), capture_output=True, check=False)
    if result.returncode:
        raise RuntimeError("Não foi possível configurar. Confira ADB, aparelho autorizado e APK de depuração instalada.")
    print("Chave configurada na área privada do aparelho, fora do APK e do backup.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest="mode", required=True)
    device = sub.add_parser("device", help="Configurar somente uma instalação de depuração via ADB")
    device.add_argument("--serial")
    inspection = sub.add_parser("inspect", help="Inspecionar respostas reais com entrada privada da chave")
    inspection.add_argument("--output", type=Path, default=Path("comicvine-inspection.json"))
    args = parser.parse_args()
    try:
        key = read_key()
        if args.mode == "device":
            provision(key, args.serial)
        else:
            inspect(key, args.output)
    except (RuntimeError, OSError) as error:
        # Apenas mensagens construídas pelo script. OSError externo não é impresso.
        print(str(error) if isinstance(error, RuntimeError) else "Falha ao executar a operação local.", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
