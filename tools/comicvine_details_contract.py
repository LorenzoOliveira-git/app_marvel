import json
from comicvine import fetch, read_key
key=read_key()
report={}
def probe(name,path,params):
    r=fetch(path,params,key)
    report[name]=r
    p=r.get('response',{})
    if p.get('status_code')!=1: raise RuntimeError(name)
    return p['results']
rows=probe('search','characters/',{'filter':'name:Spider-Man','limit':100,'field_list':'id,name,publisher,api_detail_url'})
hero=next(x for x in rows if x['name']=='Spider-Man' and x['publisher']['name']=='Marvel')
path=hero['api_detail_url'].split('/api/')[1]
details=probe('details',path,{'field_list':'id,name,real_name,publisher,origin,gender,image,deck,site_detail_url,powers,teams,character_friends,character_enemies,first_appeared_in_issue,count_of_issue_appearances'})
for kind,resource in [('teams','teams/'),('character_friends','characters/'),('character_enemies','characters/')]:
    ids=[str(x['id']) for x in details.get(kind,[])][:4]
    if ids: probe(kind,resource,{'filter':'id:'+'|'.join(ids),'limit':100,'field_list':'id,name,publisher,image,site_detail_url'})
first=details.get('first_appeared_in_issue')
if first:
    rows=probe('first_issue','issues/',{'filter':'id:'+str(first['id']),'limit':100,'field_list':'id,name,issue_number,volume,image,cover_date,site_detail_url'})
    if rows: probe('first_volume',rows[0]['volume']['api_detail_url'].split('/api/')[1],{'field_list':'id,name,publisher'})
open('details-contract.json','w').write(json.dumps(report,ensure_ascii=False,indent=2))
print('Contrato público sanitizado de detalhes salvo.')
