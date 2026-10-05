#!/usr/bin/env python3
"""Contrato dirigido de detalhes de séries e episódios, sem credenciais."""
import json
from pathlib import Path
from comicvine import fetch,read_key
from comicvine_contract import resource_path

def main():
 key=read_key();report={'probes':{}}
 def probe(label,path,params={}):
  r=fetch(path,params,key,4_000_000);report['probes'][label]={'path':path,'query_without_key':params,**r}
  p=r.get('response',{});return p.get('results') if p.get('status_code')==1 else None
 try:
  for label,id in [('featured',1),('agatha',1315),('foreign',331)]:
   s=probe(label,'series/4075-'+str(id)+'/')
   if label=='featured' and isinstance(s,dict):
    refs=s.get('episodes') or [];report['episode_reference_count']=len(refs)
    for i,ref in enumerate(refs[:2]):
     path=resource_path(ref.get('api_detail_url'),'episode')
     if path:probe('episode_detail_'+str(i),path)
    ids=[str(x['id']) for x in refs[:3] if isinstance(x.get('id'),int)]
    if ids:probe('episode_id_batch','episodes/',{'filter':'id:'+'|'.join(ids),'limit':100})
    probe('episodes_series_filter','episodes/',{'filter':'series:1','sort':'episode_number:asc','limit':10})
    probe('episodes_series_desc','episodes/',{'filter':'series:1','sort':'episode_number:desc','limit':10})
 finally:Path('comicvine-series-details-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print('Contrato de detalhes registrado.')
if __name__=='__main__':main()
