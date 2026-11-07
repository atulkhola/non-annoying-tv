#!/usr/bin/env python3
"""One-time catalog pull from Movie of the Night for your country. Resumable, capped so the free monthly quota is safe."""
import json,os,sys,time,urllib.request,urllib.parse,urllib.error
K=os.environ['MOTN_KEY']; C=os.environ.get('COUNTRY','in'); CATALOGS=os.environ.get('CATALOGS','netflix,prime.subscription,hotstar')
D=os.path.dirname(os.path.abspath(__file__)); S=f'{D}/pull_state.json'; O=f'{D}/shows.jsonl'
CAP=int(sys.argv[1]) if len(sys.argv)>1 else 420
st=json.load(open(S)) if os.path.exists(S) else {'cursor':None,'n':0,'seen':0,'done':False}
Q={'country':C,'catalogs':CATALOGS,'order_by':'popularity_1year','order_direction':'desc','rating_min':'40'}
run=0
while not st['done'] and run<CAP:
    q=dict(Q)
    if st['cursor']: q['cursor']=st['cursor']
    r=urllib.request.Request('https://api.movieofthenight.com/v4/shows/search/filters?'+urllib.parse.urlencode(q),headers={'X-API-Key':K,'User-Agent':'tv-home'})
    try: d=json.load(urllib.request.urlopen(r,timeout=30))
    except urllib.error.HTTPError as e:
        print('stop',e.code,e.read()[:160],flush=True); break
    with open(O,'a') as f:
        for s in d.get('shows',[]):
            o=[x for x in s.get('streamingOptions',{}).get(C,[]) if x['type'] in('subscription','free')]
            f.write(json.dumps(dict(id=s.get('imdbId'),title=s.get('title'),type=s.get('showType'),year=s.get('releaseYear') or s.get('firstAirYear'),rating=s.get('rating'),runtime=s.get('runtime'),seasons=s.get('seasonCount'),overview=s.get('overview'),genres=[g['id'] for g in s.get('genres',[])],cast=(s.get('cast') or [])[:4],directors=(s.get('directors') or s.get('creators') or [])[:2],
              options=[dict(service=x['service']['id'],type=x['type'],link=x.get('link'),audios=sorted({a.get('language') for a in x.get('audios',[]) if a.get('language')})) for x in o],images={k:(v.get('w720') or v.get('w480')) for k,v in (s.get('imageSet') or {}).items() if k in('horizontalPoster','horizontalBackdrop','verticalPoster')}),ensure_ascii=False)+'\n'); st['seen']+=1
    st['n']+=1; run+=1; st['cursor']=d.get('nextCursor'); st['done']=not d.get('hasMore')
    json.dump(st,open(S,'w'))
    if run%25==0: print('requests',st['n'],'titles',st['seen'],flush=True)
    time.sleep(.25)
print('finished',st,flush=True)
