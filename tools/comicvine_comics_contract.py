#!/usr/bin/env python3
"""Contrato real dirigido de filmes; somente metadados sanitizados."""
import json
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path

def main():
    key=read_key();report={'probes':{}}
    def probe(label,path,params,max_bytes=2_000_000,required=True):
        value=fetch(path,params,key,max_bytes)
        report['probes'][label]={'path':path,'query_without_key':params,**value}
        payload=value.get('response',{})
        if required:assert payload.get('status_code')==1,label
        return payload.get('results') if payload.get('status_code')==1 else None
    try:
        pubs=probe('publisher','publishers/',{'filter':'name:Marvel','limit':100,'field_list':'id,name,api_detail_url'})
        pub=next(p for p in pubs if p.get('name')=='Marvel')
        probe('publisher_movies',resource_path(pub['api_detail_url'],'publisher'),{'field_list':'id,name,movies'},8_000_000)
        probe('baseline','movies/',{'limit':12,'sort':'id:asc'})
        fields='id,name,api_detail_url,image,release_date,runtime,rating,site_detail_url,deck,publisher,characters,studios'
        probe('requested_relations','movies/',{'limit':12,'sort':'id:asc','field_list':fields})
        probe('publisher_filter','movies/',{'limit':12,'sort':'id:asc','filter':'publisher:'+str(pub['id']),'field_list':fields},required=False)
        marvel_index=probe('marvel_characters',resource_path(pub['api_detail_url'],'publisher'),{'field_list':'id,name,characters'},8_000_000)
        canonical={r['id']:r for r in marvel_index.get('characters',[])}
        character=next(r for r in canonical.values() if r.get('name')=='Spider-Man')
        probe('character_movies',resource_path(character['api_detail_url'],'character'),{'field_list':'id,name,publisher,movies'})
        probe('characters_movies_batch','characters/',{'filter':'id:'+str(character['id']),'limit':100,'field_list':'id,name,publisher,movies'})
        samples=[]
        for label,name in [('marvel','Iron Man'),('foreign','Batman')]:
            rows=probe(label+'_names','movies/',{'filter':'name:'+name,'limit':12,'field_list':fields})
            if rows:
                ref=next((r for r in rows if r.get('name')==name),rows[0]);samples.append(ref)
                detail=probe(label+'_detail',resource_path(ref['api_detail_url'],'movie'),{})
                if detail:
                    refs=detail.get('characters') or []
                    report[label+'_characters_in_marvel_index']=[r['id'] for r in refs if r.get('id') in canonical]
                    report[label+'_characters_not_in_marvel_index']=[r['id'] for r in refs if r.get('id') not in canonical]
        if samples:probe('movie_id_batch','movies/',{'filter':'id:'+'|'.join(str(r['id']) for r in samples),'limit':100,'field_list':fields})
    finally:
        Path('comicvine-movies-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Contrato real de filmes registrado.')
if __name__=='__main__':main()
