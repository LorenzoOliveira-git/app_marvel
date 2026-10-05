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
        fields='id,name,api_detail_url,image,release_date,date_last_updated,runtime,rating,site_detail_url,studios'
        probe('studio_identity','studio/4010-31/',{'field_list':'id,name,api_detail_url'},required=False)
        probe('publisher_identity','publisher/4010-31/',{'field_list':'id,name,api_detail_url'})
        for label,filter_value in [('studios_filter','studios:31'),('studio_filter','studio:31')]:
            probe(label,'movies/',{'filter':filter_value,'sort':'id:asc','limit':12,'field_list':fields},required=False)
        for order in ['asc','desc']:
            probe('name_'+order,'movies/',{'sort':'name:'+order,'limit':12,'field_list':fields})
        probe('search_case','movies/',{'filter':'name:iRoN mAn','limit':12,'field_list':fields})
        probe('studios_page_two','movies/',{'filter':'studios:31','sort':'name:asc','offset':12,'limit':12,'field_list':fields},required=False)
        rows=probe('first_hundred','movies/',{'sort':'id:asc','limit':100,'field_list':fields})
        report['marvel_studio_ids']=[r['id'] for r in rows if any(t.get('id')==31 for t in r.get('studios') or [])]
        report['release_equals_update_count']=sum(r.get('release_date')==r.get('date_last_updated') for r in rows)
        report['sample_count']=len(rows)
    finally:
        Path('comicvine-movies-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print('Contrato real de filmes registrado.')
if __name__=='__main__':main()
