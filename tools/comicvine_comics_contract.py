#!/usr/bin/env python3
"""Contrato real dirigido de séries; somente respostas sanitizadas."""
import json
from pathlib import Path
from comicvine import fetch, read_key
from comicvine_contract import resource_path

def main():
 key=read_key();report={'probes':{}}
 def probe(label,path,params,required=True):
  value=fetch(path,params,key,4_000_000);report['probes'][label]={'path':path,'query_without_key':params,**value}
  payload=value.get('response',{})
  if required:assert payload.get('status_code')==1,label
  return payload.get('results') if payload.get('status_code')==1 else None
 try:
  fields='id,name,api_detail_url,image,site_detail_url,publisher,start_year,count_of_episodes,date_last_updated'
  probe('publisher_identity','publisher/4010-31/',{'field_list':'id,name,api_detail_url,series'},False)
  baseline=probe('baseline','series_list/',{'limit':100,'sort':'name:asc','field_list':fields})
  probe('descending','series_list/',{'limit':12,'sort':'name:desc','field_list':fields})
  probe('publisher_filter','series_list/',{'limit':12,'filter':'publisher:31','sort':'name:asc','field_list':fields},False)
  probe('name_filter','series_list/',{'limit':12,'filter':'name:Agents of S.H.I.E.L.D.','field_list':fields},False)
  probe('page_two','series_list/',{'offset':100,'limit':100,'sort':'name:asc','field_list':fields})
  candidates=[r for r in baseline if isinstance(r.get('publisher'),dict) and r['publisher'].get('id')==31]
  report['marvel_baseline_ids']=[r['id'] for r in candidates]
  if candidates:
   path=resource_path(candidates[0].get('api_detail_url'),'series')
   if path:probe('marvel_detail',path,{},False)
  other=next((r for r in baseline if isinstance(r.get('publisher'),dict) and r['publisher'].get('id')==10),None)
  if other:
   path=resource_path(other.get('api_detail_url'),'series')
   if path:probe('foreign_detail',path,{},False)
 finally:Path('comicvine-series-contract.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
 print('Contrato real de séries registrado.')
if __name__=='__main__':main()
