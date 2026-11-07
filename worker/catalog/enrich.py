#!/usr/bin/env python3
"""Adds IMDb rating, runtime, logo and a trailer pick from Cinemeta. Resumable. Writes titles.jsonl."""
import json,os,re,urllib.request,concurrent.futures as cf,time
D=os.path.dirname(os.path.abspath(__file__)); I=f'{D}/shows.jsonl'; O=f'{D}/enriched.jsonl'
done=set()
if os.path.exists(O):
    done={json.loads(l)['id'] for l in open(O)}
seen=set(); rows=[]
for n,l in enumerate(open(I)):
    d=json.loads(l)
    if d.get('id') and d['id'] not in seen: seen.add(d['id']); d['pop']=n; rows.append(d)
todo=[d for d in rows if d['id'] not in done]
BAD=re.compile(r'song|lyric|music video|video|teaser|promo|clip|behind|making|interview|review|reaction',re.I)
def go(d):
    t='series' if d['type']=='series' else 'movie'
    m={}
    for a in range(2):
        try:
            m=json.load(urllib.request.urlopen(urllib.request.Request(f"https://v3-cinemeta.strem.io/meta/{t}/{d['id']}.json",headers={'User-Agent':'tv-home'}),timeout=15)).get('meta') or {}; break
        except Exception: time.sleep(1)
    tr=[(x.get('title') or '',x.get('ytId')) for x in (m.get('trailerStreams') or []) if x.get('ytId')]
    c=[y for n,y in tr if not BAD.search(n)]
    r=m.get('imdbRating'); rt=d.get('runtime')
    if not rt:
        g=re.match(r'(\d+)',str(m.get('runtime') or '')); rt=int(g.group(1)) if g else None
    a=sorted({x for o in d['options'] for x in o['audios']})
    d.update(imdb=float(r) if r and re.match(r'^[\d.]+$',str(r)) else None,runtime=rt,logo=m.get('logo'),trailer=(c or [None])[-1],langs=a,poster=d['images'].get('horizontalPoster'),backdrop=d['images'].get('horizontalBackdrop') or d['images'].get('horizontalPoster'))
    d.pop('images',None); d.pop('directors',None)
    return d
with open(O,'a') as f, cf.ThreadPoolExecutor(6) as ex:
    for n,d in enumerate(ex.map(go,todo)):
        f.write(json.dumps(d,ensure_ascii=False)+'\n')
        if n%300==0: f.flush(); print(n,'of',len(todo),flush=True)
print('done',len(rows),flush=True)
