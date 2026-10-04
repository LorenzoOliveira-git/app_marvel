#!/usr/bin/env python3
"""Sondagens reais dirigidas pelos vínculos recebidos; não incorpora credenciais ao relatório."""
import datetime
import json
from pathlib import Path
import re
import sys
from urllib.parse import urlsplit
from comicvine import fetch, read_key


def resource_path(url, resource):
    if not isinstance(url, str):
        return None
    parts = urlsplit(url)
    if parts.scheme == 'https' and parts.hostname == 'comicvine.gamespot.com' and re.fullmatch(
            '/api/' + resource + r'/[0-9]+-[0-9]+/', parts.path):
        return parts.path.removeprefix('/api/')
    return None


def main():
    key = read_key()
    today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()
    report = {'captured_at': datetime.datetime.now(datetime.timezone.utc).isoformat(), 'probes': {}}

    def probe(name, path, params, max_bytes=2_000_000):
        result = fetch(path, params, key, max_bytes=max_bytes)
        report['probes'][name] = {'resource_path': path, 'query_without_key': params, **result}
        payload = result.get('response', {})
        return payload.get('results') if isinstance(payload, dict) and payload.get('status_code') == 1 else None

    publishers = probe('publishers', 'publishers/', {'filter': 'name:Marvel', 'limit': 100,
        'field_list': 'id,name,api_detail_url,site_detail_url'}) or []
    candidates = [p for p in publishers if p.get('name') == 'Marvel' and isinstance(p.get('id'), int)]
    if len(candidates) != 1:
        raise RuntimeError('Editora Marvel não resolvida de forma única.')
    publisher = candidates[0]
    path = resource_path(publisher.get('api_detail_url'), 'publisher')
    if not path:
        raise RuntimeError('URL de editora recebida inválida.')
    report['marvel'] = publisher
    probe('publisher_identity', path, {'field_list': 'id,name'})
    volume_index = probe('publisher_volumes', path, {'field_list': 'id,name,volumes'}, max_bytes=8_000_000)
    characters = probe('characters_popular', 'characters/', {'limit': 50,
        'sort': 'count_of_issue_appearances:desc',
        'field_list': 'id,name,real_name,publisher,origin,gender,image,deck,site_detail_url,api_detail_url,count_of_issue_appearances'}) or []
    heroes = probe('featured_name', 'characters/', {'limit': 20, 'filter': 'name:Spider-Man',
        'field_list': 'id,name,real_name,publisher,origin,gender,image,deck,site_detail_url,api_detail_url,count_of_issue_appearances'}) or []
    featured = [p for p in heroes if p.get('name') == 'Spider-Man' and (p.get('publisher') or {}).get('id') == publisher['id']]
    if len(featured) == 1:
        character = featured[0]
        detail = resource_path(character.get('api_detail_url'), 'character')
        if detail:
            probe('featured_detail', detail, {'field_list': 'id,name,real_name,publisher,origin,image,deck,description,teams,powers,first_appeared_in_issue,site_detail_url'})
    if characters:
        ids = [str(c['id']) for c in characters if (c.get('publisher') or {}).get('id') == publisher['id']][:2]
        if ids:
            probe('characters_id_filter', 'characters/', {'limit': 10, 'filter': 'id:' + '|'.join(ids),
                'field_list': 'id,name,publisher'})
    issues = probe('issues_published', 'issues/', {'limit': 30, 'sort': 'store_date:desc',
        'filter': 'store_date:1900-01-01|' + today,
        'field_list': 'id,name,issue_number,volume,image,store_date,cover_date,site_detail_url,api_detail_url'}) or []
    volume_ids = list(dict.fromkeys((i.get('volume') or {}).get('id') for i in issues))
    volume_ids = [n for n in volume_ids if isinstance(n, int)]
    if volume_ids:
        probe('volumes_id_filter', 'volumes/', {'limit': 100, 'filter': 'id:' + '|'.join(map(str, volume_ids)),
            'field_list': 'id,name,publisher'})
    probe('volumes_publisher_filter', 'volumes/', {'limit': 20, 'filter': 'publisher:' + str(publisher['id']),
        'field_list': 'id,name,publisher'})
    if isinstance(volume_index, dict):
        refs = volume_index.get('volumes')
        if isinstance(refs, list):
            ids = {v.get('id') for v in refs}
            verified_issues = [i for i in issues if (i.get('volume') or {}).get('id') in ids]
            report['recent_marvel_issue_ids'] = [i.get('id') for i in verified_issues]
            samples = list(dict.fromkeys((i.get('volume') or {}).get('api_detail_url') for i in verified_issues))[:2]
            for index, url in enumerate(samples):
                path = resource_path(url, 'volume')
                if path:
                    probe('recent_volume_' + str(index), path, {'field_list': 'id,name,publisher'})
    probe('origins', 'origins/', {'limit': 100, 'field_list': 'id,name,api_detail_url'})
    output = Path('comicvine-contract.json')
    output.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Relatório de contrato sanitizado salvo.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError):
        print('Inspeção não concluída; verificar configuração e respostas sem expor credenciais.', file=sys.stderr)
        sys.exit(1)
