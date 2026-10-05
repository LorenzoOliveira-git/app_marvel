#!/usr/bin/env python3
"""Contrato dos catálogos auxiliares do formulário, sem credenciais."""
import json
from pathlib import Path
from comicvine import fetch, read_key

def main():
    key = read_key()
    report = {'probes': {}}
    try:
        for resource in ['origins', 'powers']:
            response = fetch(resource + '/', {'limit': 100, 'offset': 0, 'field_list': 'id,name,api_detail_url', 'sort': 'name:asc'}, key, 4_000_000)
            report['probes'][resource] = response
            payload = response.get('response', {})
            assert payload.get('status_code') == 1, resource
            rows = payload.get('results', [])
            assert rows and all(row.get('id', 0) > 0 and row.get('name') for row in rows), resource
            if payload.get('number_of_total_results', 0) > 100:
                report['probes'][resource + '_page2'] = fetch(resource + '/', {'limit': 100, 'offset': 100, 'field_list': 'id,name,api_detail_url', 'sort': 'name:asc'}, key, 4_000_000)
    finally:
        Path('comicvine-hero-form-contract.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print('Origens e poderes reais registrados.')

if __name__ == '__main__':
    main()
