#!/usr/bin/env python3
"""Verifica o vínculo de equipes recebido de um personagem Marvel real."""
import json
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path

key = read_key()
report = {'probes': {}}


def probe(name, path, fields, params=None):
    query = {'field_list': fields, **(params or {})}
    result = fetch(path, query, key)
    report['probes'][name] = {'resource_path': path, 'query_without_key': query, **result}
    return result.get('response', {}).get('results')


publishers = probe('marvel', 'publishers/', 'id,name,api_detail_url', {'filter': 'name:Marvel', 'limit': 100}) or []
marvel = [p for p in publishers if p.get('name') == 'Marvel']
if len(marvel) != 1:
    raise RuntimeError('Editora não resolvida.')
publisher = marvel[0]
probe('publisher_teams', resource_path(publisher.get('api_detail_url'), 'publisher'), 'id,name,teams')
records = probe('character', 'characters/', 'id,name,publisher,api_detail_url', {'filter': 'name:Spider-Man', 'limit': 20}) or []
characters = [c for c in records if c.get('name') == 'Spider-Man' and (c.get('publisher') or {}).get('id') == publisher['id']]
if len(characters) != 1:
    raise RuntimeError('Personagem não resolvido.')
detail = probe('character_teams', resource_path(characters[0].get('api_detail_url'), 'character'), 'id,name,publisher,teams') or {}
refs = [t for t in detail.get('teams', []) if t.get('name') in {'Avengers', 'X-Men'}]
for index, ref in enumerate(refs):
    path = resource_path(ref.get('api_detail_url'), 'team')
    if path:
        probe('team_' + str(index), path, 'id,name,publisher,characters')
probe('teams_list', 'teams/', 'id,name,publisher', {'limit': 20, 'filter': 'name:Avengers'})
Path('comicvine-filters.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
print('Relações de equipes consultadas e sanitizadas.')
