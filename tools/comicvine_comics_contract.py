#!/usr/bin/env python3
"""Consulta dirigida do catálogo; relatório público sem credenciais."""
import datetime
import json
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
        assert payload.get('status_code') == 1
        return payload['results']
    try:
        publishers = probe('publisher', 'publishers/', {'filter':'name:Marvel','limit':100,'field_list':'id,name,api_detail_url'})
        pubs = [p for p in publishers if p.get('name') == 'Marvel']
        assert len(pubs) == 1
        pub = pubs[0]
        index = probe('volumes', resource_path(pub['api_detail_url'], 'publisher'), {'field_list':'id,name,volumes'}, 8_000_000)
        refs = {r['id']:r for r in index['volumes']}
        today = datetime.datetime.now(datetime.timezone.utc).date().isoformat()
        fields = 'id,api_detail_url,issue_number,volume,image,store_date,cover_date,site_detail_url'
        rows = probe('recent', 'issues/', {'sort':'store_date:desc','filter':'store_date:1900-01-01|'+today,'limit':100,'field_list':fields})
        ids = list(dict.fromkeys(i['volume']['id'] for i in rows if i['volume']['id'] in refs))[:3]
        assert ids
        report['selected_volumes'] = [refs[i] for i in ids]
        for order in ['desc','asc']:
            for offset in [0,12]:
                probe(order+str(offset), 'issues/', {'sort':'store_date:'+order,'filter':'volume:'+'|'.join(map(str,ids))+',store_date:1900-01-01|'+today,'limit':12,'offset':offset,'field_list':fields})
        first = next(i for i in rows if i['volume']['id'] in refs)
        probe('detail', resource_path(first['api_detail_url'],'issue'), {'field_list':'id,name,volume,store_date,cover_date,story_arc_credits,deck,description'})
    finally:
        Path('comicvine-comics-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Contrato público de quadrinhos consultado.')

if __name__ == '__main__':
    main()
