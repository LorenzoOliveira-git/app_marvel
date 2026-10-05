#!/usr/bin/env python3
"""Sondagem real dirigida do catálogo de arcos. Não publica credenciais."""
import json
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path

def main():
    key=read_key();report={'probes':{}}
    def probe(label,path,params,max_bytes=2_000_000):
        value=fetch(path,params,key,max_bytes)
        report['probes'][label]={'path':path,'query_without_key':params,**value}
        assert value.get('response',{}).get('status_code')==1,label
        return value['response']['results']
    try:
        pubs=probe('publisher','publishers/',{'filter':'name:Marvel','limit':100,'field_list':'id,name,api_detail_url'})
        candidates=[p for p in pubs if p['name']=='Marvel'];assert len(candidates)==1
        pub=candidates[0]
        index=probe('index',resource_path(pub['api_detail_url'],'publisher'),{'field_list':'id,name,story_arcs'},8_000_000)
        refs=sorted(index['story_arcs'],key=lambda r:(r['name'].casefold(),r['id']))
        canonical={r['id']:r for r in refs};assert refs
        report['canonical_count']=len(canonical)
        baseline=probe('baseline','story_arcs/',{'limit':12,'sort':'id:asc'})
        filtered=probe('publisher_filter','story_arcs/',{'filter':'publisher:'+str(pub['id']),'limit':12,'sort':'id:asc'})
        report['publisher_filter_sample_verified']=all((r.get('publisher') or {}).get('id')==pub['id'] for r in filtered)
        report['foreign_samples']=[r['id'] for r in baseline if (r.get('publisher') or {}).get('id')!=pub['id']]
        fields='id,name,api_detail_url,publisher,image,site_detail_url,count_of_issue_appearances'
        for page in range(2):
            selected=refs[page*12:(page+1)*12];requested={r['id'] for r in selected}
            rows=probe('batch_'+str(page),'story_arcs/',{'filter':'id:'+'|'.join(map(str,sorted(requested))),'limit':100,'field_list':fields})
            assert {r['id'] for r in rows}==requested
            assert all(resource_path(r['api_detail_url'],'story_arc')==resource_path(canonical[r['id']]['api_detail_url'],'story_arc') for r in rows)
            report['batch_'+str(page)+'_marvel_count']=sum((r.get('publisher') or {}).get('id')==pub['id'] for r in rows)
        sample=next(r for r in refs if 'Civil War' in r['name'])
        probe('named_arc',resource_path(sample['api_detail_url'],'story_arc'),{})
    finally:
        Path('comicvine-arcs-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Índice, filtro e lotes reais de arcos consultados.')
if __name__=='__main__':main()
