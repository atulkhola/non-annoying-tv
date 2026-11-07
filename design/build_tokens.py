#!/usr/bin/env python3
"""Generates the web and Android token files from tokens.json. Edit tokens.json, then run this."""
import json,math,os,re
R=os.path.dirname(os.path.abspath(__file__)); T=json.load(open(f'{R}/tokens.json'))
def kebab(s): return re.sub(r'([A-Z])',lambda m:'-'+m.group(1).lower(),s)
assert T['grid']['columns']*T['grid']['column']+(T['grid']['columns']-1)*T['grid']['gutter']==T['grid']['content']
assert T['safe']['left']+T['grid']['content']+T['safe']['right']==T['canvas']['width']
for n,w in T['tvosColumns'].items():
    assert abs(int(n)*w+(int(n)-1)*T['grid']['gutter']-T['grid']['content'])<=int(n), n
assert T['row']['step']-T['card']['height']>=T['space']['xxxl'], 'rows closer than Apple minimum'
assert T['panel']['columns']*T['grid']['column']+(T['panel']['columns']-1)*T['grid']['gutter']==T['panel']['width'], 'panel off the grid'
assert T['panel']['textWidth']<=T['panel']['width']-2*T['panel']['pad']

def spring(k,z):
    """Samples a unit spring into a CSS linear() easing and how long it takes to settle."""
    w=math.sqrt(k)
    def x(t):
        if z>=1: return 1-math.exp(-w*t)*(1+w*t)
        d=w*math.sqrt(1-z*z); return 1-math.exp(-z*w*t)*(math.cos(d*t)+z*w/d*math.sin(d*t))
    t=0
    while not all(abs(x(t+i/200)-1)<.002 for i in range(20)): t+=.005
    return ','.join(f'{x(t*i/32):.3f}' for i in range(33)),round(t*1000)
css=[':root {']
for g in ('canvas','safe','grid','space','card','pill','token','chip','panel','lens','ticker','form','row','home','browse','logo'):
    for k,v in T[g].items(): css.append(f'  --{g}-{kebab(k)}: {f"var(--color-{v})" if k=="fill" else v}{"" if k in ("columns","focusScale","fill") else "px"};')
for k,v in T['clamp'].items(): css.append(f'  --clamp-{kebab(k)}: {v};')
for k,v in T['glass'].items(): css.append(f'  --glass-{k}: {v}{"px" if k=="blur" else ""};')
for k,v in T['spring'].items():
    e,ms=spring(v['stiffness'],v['damping']); css+=[f'  --spring-{k}: linear({e});',f'  --spring-{k}-ms: {ms}ms;']
for k,v in T['motion'].items(): css.append(f'  --motion-{kebab(k)}: {v}ms;')
for k,v in T['color'].items(): css.append(f'  --color-{k}: {v};')
fam={'sans':'"Geist Sans"','serif':'"Geist Serif"','mono':'"Geist Mono"','pixel':'"Geist Pixel"','pixelCircle':'"Geist Pixel Circle"','pixelLine':'"Geist Pixel Line"'}
for k,v in T['type'].items():
    n=kebab(k); css+= [f'  --type-{n}-font: {fam[v["family"]]};',f'  --type-{n}-weight: {v["weight"]};',f'  --type-{n}-size: {v["size"]}px;',f'  --type-{n}-line: {v["line"]}px;',f'  --type-{n}-tracking: {v["tracking"]}em;',f'  --type-{n}-case: {"uppercase" if v.get("case")=="upper" else "none"};']
css.append('}')
j=['package tv.home.app;','','/** Generated from design/tokens.json. Do not edit. */','final class Tokens {']
for g in ('canvas','safe','grid','space','card','pill','token','chip','panel','lens','ticker','form','clamp','boot','row','home','browse','logo','motion'):
    for k,v in T[g].items():
        if isinstance(v,str): continue
        name=re.sub(r'([A-Z])',r'_\1',f'{g}_{k}').upper()
        j.append(f'  static final {"float" if isinstance(v,float) else "int"} {name} = {v}{"f" if isinstance(v,float) else ""};')
for k,v in T['type'].items():
    name=re.sub(r'([A-Z])',r'_\1',k).upper()
    if v.get('case')=='upper': j.append(f'  static final boolean {name}_UPPER = true;')
    j.append(f'  static final String {name}_FAMILY = "{v["family"]}";')
    j.append(f'  static final float {name}_SIZE = {v["size"]}f, {name}_LINE = {v["line"]}f, {name}_TRACKING = {v["tracking"]}f;')
for k,v in T['color'].items():
    c=v[1:]; c=(c[6:8] or 'ff')+c[:6]; j.append(f'  static final int COLOR_{k.upper()} = 0x{c};')
j.append(f'  static final boolean LISTEN_CENTERED = {"true" if T["listen"]["align"]=="center" else "false"};')
j.append(f'  static final String COPY_PROMPT = {json.dumps(T["copy"]["prompt"])};')
j.append(f'  static final int TOKEN_FILL = COLOR_{T["token"]["fill"].upper()}, TOKEN_TEXT = COLOR_ROOM;')
for k,v in T['glass'].items():
    name=f'GLASS_{k.upper()}'
    if isinstance(v,str): j.append(f'  static final int {name} = 0x{v[7:9]}{v[1:7]};')
    else: j.append(f'  static final {"float" if isinstance(v,float) else "int"} {name} = {v}{"f" if isinstance(v,float) else ""};')
for k,v in T['spring'].items(): j.append(f'  static final float SPRING_{k.upper()}_STIFFNESS = {v["stiffness"]}f, SPRING_{k.upper()}_DAMPING = {v["damping"]}f;')
S=T['sound']
j.append(f'  static final float SOUND_MASTER = {S["master"]}f, SOUND_VIDEO_DUCK = {S["videoDuck"]}f, SOUND_PAN = {S["pan"]}f, SOUND_ROW_PITCH = {S["rowPitch"]}f;')
j.append(f'  static final int SOUND_DUCK_MS = {S["duckMs"]}, SOUND_MIN_GAP_MS = {S["minGapMs"]};')
for k,v in S['events'].items(): j.append(f'  static final float SOUND_{re.sub(r"([A-Z])",r"_\1",k).upper()}_GAIN = {v["gain"]}f;')
j.append('}')
open(f'{R}/../tv-app/app/src/main/java/tv/home/app/Tokens.java','w').write('\n'.join(j)+'\n')
print('ok: grid checks passed; wrote Tokens.java')
