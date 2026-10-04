#!/usr/bin/env python3
"""Contrato dirigido de detalhes de edições; credencial somente no ambiente privado."""
import json
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path

def main():
    key=read_key(); report={'probes':{}}
    def probe(label,path,params,max_bytes=2_000_000):
        value=fetch(path,params,key,max_bytes)
        report['probes'][label]={'path':path,'query_without_key':params,**value}
        assert value.get('response',{}).get('status_code')==1,label
        return value['response']['results']
    try:
        pubs=probe('publisher','publishers/',{'filter':'name:Marvel','limit':100,'field_list':'id,name,api_detail_url'})
        pub=next(p for p in pubs if p['name']=='Marvel')
        index=probe('volumes',resource_path(pub['api_detail_url'],'publisher'),{'field_list':'id,name,volumes'},8_000_000)
        canonical={r['id']:r for r in index['volumes']}
        fields='id,api_detail_url,name,issue_number,volume,image,store_date,cover_date,site_detail_url,deck,description,character_credits,team_credits,person_credits,story_arc_credits,location_credits,object_credits,concept_credits'
        for issue_id in [1195913,105342]:
            rows=probe('reference_'+str(issue_id),'issues/',{'filter':'id:'+str(issue_id),'field_list':'id,api_detail_url','limit':100})
            assert len(rows)==1 and rows[0]['id']==issue_id
            detail=probe('detail_'+str(issue_id),resource_path(rows[0]['api_detail_url'],'issue'),{'field_list':fields})
            assert detail['id']==issue_id and detail['volume']['id'] in canonical
            ref=canonical[detail['volume']['id']]
            owner=probe('volume_'+str(issue_id),resource_path(ref['api_detail_url'],'volume'),{'field_list':'id,name,publisher'})
            assert owner['publisher']['id']==pub['id']
            for kind,field in [('character','character_credits'),('team','team_credits'),('person','person_credits'),('story_arc','story_arc_credits')]:
                refs=detail.get(field) or []
                if refs:
                    probe(kind+'_'+str(issue_id),resource_path(refs[0]['api_detail_url'],kind),{'field_list':'id,name,publisher,site_detail_url,image,issue_credits'})
        dc=probe('foreign','issues/',{'filter':'id:6','limit':100,'field_list':'id,volume,api_detail_url'})
        assert all(i['volume']['id'] not in canonical for i in dc)
    finally:
        Path('comicvine-issue-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Detalhes e vínculos reais consultados sem credencial no relatório.')
if __name__=='__main__':main()
