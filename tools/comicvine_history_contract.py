#!/usr/bin/env python3
"""Sondagem pública real de aparições, sem segredo no relatório."""
import json
import sys
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path


def main():
    key = read_key()
    report = {'probes': {}}

    def probe(label, path, params, max_bytes=2_000_000):
        result = fetch(path, params, key, max_bytes)
        report['probes'][label] = {'path': path, 'query_without_key': params, **result}
        payload = result.get('response', {})
        if payload.get('status_code') != 1:
            raise RuntimeError('Consulta não concluída.')
        return payload

    try:
        pubs = probe('publisher', 'publishers/', {'filter': 'name:Marvel', 'field_list': 'id,name,api_detail_url', 'limit': 100})['results']
        marvel = [p for p in pubs if p.get('name') == 'Marvel']
        assert len(marvel) == 1
        publisher = marvel[0]
        heroes = probe('featured', 'characters/', {'filter': 'name:Spider-Man', 'field_list': 'id,name,publisher,api_detail_url', 'limit': 100})['results']
        heroes = [c for c in heroes if c.get('name') == 'Spider-Man' and (c.get('publisher') or {}).get('id') == publisher['id']]
        assert len(heroes) == 1
        hero = heroes[0]
        probe('identity', resource_path(hero['api_detail_url'], 'character'), {'field_list': 'id,name,publisher,first_appeared_in_issue,count_of_issue_appearances'})
        # Não pressupõe que um filtro não documentado funcione: relações são conferidas nas respostas.
        for offset in [0, 12]:
            probe('appearances_' + str(offset), 'issues/', {'filter': 'characters:' + str(hero['id']),
                'sort': 'cover_date:asc', 'offset': offset, 'limit': 12,
                'field_list': 'id,name,issue_number,volume,image,cover_date,store_date,character_credits,site_detail_url,api_detail_url'})
        sample = report['probes']['appearances_0']['response']['results']
        if sample:
            probe('issue_detail', resource_path(sample[0]['api_detail_url'], 'issue'), {'field_list': 'id,name,volume,character_credits,first_appearance_characters,cover_date,store_date,story_arc_credits'})
        # Alternativa canônica caso o filtro não seja sustentado pela resposta.
        probe('character_issue_index', resource_path(hero['api_detail_url'], 'character'), {'field_list': 'id,name,publisher,issue_credits'}, max_bytes=8_000_000)
        report['character_id'] = hero['id']
    finally:
        Path('comicvine-history-contract.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    print('Contrato de aparições consultado; relatório sanitizado salvo.')


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, AssertionError, KeyError):
        print('Consulta incompleta; conferir relatório sanitizado.', file=sys.stderr)
        sys.exit(1)
