#!/usr/bin/env python3
"""Sends enriched titles to the Worker in small batches. Resumable. The token comes from .env and is never printed."""
import json,os,sys,time,urllib.request,urllib.error
D=os.path.dirname(os.path.abspath(__file__)); URL=sys.argv[1].rstrip('/'); S=f'{D}/ingest_state.json'
T=os.environ['APP_TOKEN']
SRC=sys.argv[2] if len(sys.argv)>2 else 'enriched.jsonl'; S=f'{D}/ingest_state_{SRC}.json'
rows=[json.loads(l) for l in open(f'{D}/{SRC}')]
rows=[r for r in rows if r.get('id') and r.get('overview') and r.get('poster')]
start=json.load(open(S))['n'] if os.path.exists(S) else 0
B=40
for i in range(start,len(rows),B):
    b=[{k:r.get(k) for k in('id','title','type','year','rating','imdb','runtime','seasons','overview','genres','cast','options','langs','poster','backdrop','logo','trailer','pop','orig')} for r in rows[i:i+B]]
    q=urllib.request.Request(URL+'/admin/ingest',data=json.dumps(b).encode(),headers={'authorization':'Bearer '+T,'content-type':'application/json','user-agent':'non-annoying-tv'})
    for a in range(4):
        try: urllib.request.urlopen(q,timeout=60).read(); break
        except urllib.error.HTTPError as e:
            print('retry',e.code,e.read()[:140],flush=True); time.sleep(3*(a+1))
    else: sys.exit('stopped at %d'%i)
    json.dump({'n':i+B},open(S,'w'))
    if (i//B)%25==0: print(i+B,'of',len(rows),flush=True)
print('done',len(rows),flush=True)
