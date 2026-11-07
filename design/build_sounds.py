#!/usr/bin/env python3
"""Synthesises the UI sounds from tokens.json. Original sounds, standard library only, same output every run."""
import json,math,os,random,shutil,struct,wave
R=os.path.dirname(os.path.abspath(__file__)); T=json.load(open(f'{R}/tokens.json')); EV=T['sound']['events']; SR=48000; PEAK=10**(-12/20)
def buf(ms): return [0.0]*int(SR*ms/1000)
def tone(b,start,f0,f1,amp,tau,attack=.0015,harm=.12):
    """A sine that glides f0 to f1, with a fast attack and an exponential tail, plus one quiet octave."""
    i0=int(start*SR); ph=0.0; n=len(b)-i0
    for i in range(n):
        t=i/SR; p=i/max(n-1,1); f=f0+(f1-f0)*p; ph+=2*math.pi*f/SR
        b[i0+i]+=amp*min(t/attack,1)*math.exp(-t/tau)*(math.sin(ph)+harm*math.sin(2*ph))
def air(b,start,amp,lo,hi):
    """Filtered noise that swells and fades. Seeded, so it never changes between builds."""
    r=random.Random(7); i0=int(start*SR); n=len(b)-i0; y=0.0
    for i in range(n):
        p=i/n; a=2*math.pi*(lo+(hi-lo)*math.sin(math.pi*p))/SR; c=a/(a+1); y+=c*(r.uniform(-1,1)-y)
        b[i0+i]+=amp*math.sin(math.pi*p)**2*y*3
def fade(b,ms=6):
    n=int(SR*ms/1000)
    for i in range(n): b[-1-i]*=i/n
    return b
def make(k):
    b=buf(EV[k]['ms'])
    if k=='focus': tone(b,0,1320,1300,1,.009)
    if k=='row': tone(b,0,880,840,1,.016); tone(b,0,440,430,.5,.02)
    if k=='edge': tone(b,0,170,140,1,.024,attack=.003,harm=.05)
    if k=='select': tone(b,0,880,880,.8,.03); tone(b,.03,1320,1320,1,.04)
    if k=='back': tone(b,0,1320,1320,.8,.03); tone(b,.03,880,880,1,.04)
    if k=='home': tone(b,0,440,440,.7,.09); tone(b,.05,660,660,.7,.09); tone(b,.1,880,880,.7,.1)
    if k=='listenStart': tone(b,0,660,660,.8,.07,attack=.006); tone(b,.08,990,990,1,.08,attack=.006)
    if k=='listenEnd': tone(b,0,990,990,.8,.06,attack=.006); tone(b,.07,660,660,1,.07,attack=.006)
    if k=='picks':
        for s,f in ((0,660),(.07,880),(.14,990),(.21,1320)): tone(b,s,f,f,.7,.11,attack=.004,harm=.08)
    if k=='handoff': air(b,0,1,300,2600); tone(b,0,220,330,.35,.16,attack=.02)
    if k=='error': tone(b,0,330,330,.9,.05,attack=.004); tone(b,.09,262,262,1,.06,attack=.004)
    if k=='build':
        air(b,0,.6,500,2400); tone(b,.42,880,880,.5,.1,attack=.01)
    return fade(b)
def boot():
    """The screen building itself, timed to the blueprint: field 0.0, map 0.5, scan 1.3, resolve 2.2. Stereo, so it has room."""
    n=int(SR*EV['boot']['ms']/1000); L=[0.0]*n; Q=[0.0]*n; r=random.Random(11)
    def put(i,v,pan):
        if 0<=i<n: L[i]+=v*(1-max(pan,0)); Q[i]+=v*(1+min(pan,0))
    def blip(t,f,ms,amp,pan):
        m=int(SR*ms/1000); i0=int(t*SR)
        for i in range(m):
            e=math.sin(math.pi*i/m)**.5; ph=2*math.pi*f*i/SR
            put(i0+i,amp*e*(math.sin(ph)+.33*math.sin(3*ph)+.2*math.sin(5*ph)),pan)
    def bell(t,f,amp,tau,pan):
        m=int(SR*tau*5); i0=int(t*SR)
        for i in range(m):
            x=i/SR; e=min(x/.002,1)*math.exp(-x/tau); ph=2*math.pi*f*x
            put(i0+i,amp*e*math.sin(ph+2.4*math.exp(-x/(tau*.4))*math.sin(ph*1.41)),pan)
    # Power coming up underneath everything.
    ph=0.0; end=int(2.2*SR)
    for i in range(end):
        p=i/end; f=36+30*p*p; ph+=2*math.pi*f/SR; a=.34*p**1.3
        v=sum(math.sin(ph*h)/h*min(1,max(0,p*5-h*.55)) for h in range(1,7)); put(i,a*v,0)
    # Data pouring in, faster and higher as the field spreads.
    t=.04; scale=[1760,2093,2349,2637,3136,3520,4186,4699,5274]
    while t<1.32:
        p=t/1.32; blip(t,scale[min(8,int(r.random()*(3+p*6)))],r.uniform(12,26),.1+.1*p,r.uniform(-.9,.9)); t+=.075-.058*p
    # The target locking on, part by part.
    for j,(s0,f) in enumerate(((.5,1318),(.66,1568),(.82,1760),(.98,2093),(1.14,2637))):
        bell(s0,f,.2,.09,(-1)**j*.55); bell(s0+.05,f*1.5,.13,.07,(-1)**j*.55)
    # The scan sweeping down: noise opening up over a rising tone.
    i0=int(1.3*SR); m=int(.9*SR); y=0.0; ph=0.0
    for i in range(m):
        p=i/m; c=1-math.exp(-2*math.pi*(280+9000*p**2.2)/SR); y+=c*(r.uniform(-1,1)-y); f=200+820*p**2; ph+=2*math.pi*f/SR
        pan=math.sin(p*math.pi*5)*.7; put(i0+i,.32*p**1.6*y+.13*p*(math.sin(ph)+.5*math.sin(2*ph)+.3*math.sin(3*ph)),pan)
    # Resolve: a thump, then a wide open chord with sparks on top.
    i0=int(2.2*SR); m=n-i0; ph=0.0
    for i in range(int(.6*SR)):
        x=i/SR; f=38+62*math.exp(-x/.05); ph+=2*math.pi*f/SR; put(i0+i,.95*math.exp(-x/.2)*math.sin(ph),0)
    for i in range(int(.012*SR)): put(i0+i,.5*r.uniform(-1,1)*(1-i/(.012*SR)),0)
    for f,a in ((110,.2),(164.8,.17),(220,.15),(277.2,.13),(329.6,.12),(415.3,.09),(493.9,.08)):
        for d,pan in ((.997,-.75),(1.003,.75)):
            for i in range(m):
                x=i/SR; put(i0+i,a*min(x/.012,1)*math.exp(-x/.62)*math.sin(2*math.pi*f*d*x),pan)
    for j,f in enumerate((1760,2217,2637,3520,4435)): bell(2.24+j*.055,f,.15,.24,(-1)**j*.6)
    # A little space around it all.
    for d,g in ((.093,.26),(.137,.2),(.211,.13)):
        k=int(d*SR)
        for i in range(k,n): L[i]+=g*Q[i-k]; Q[i]+=g*L[i-k]
    m=int(.05*SR)
    for i in range(m): L[-1-i]*=i/m; Q[-1-i]*=i/m
    return L,Q
out=f'{R}/sounds'; os.makedirs(out,exist_ok=True)
for k in EV:
    if k=='boot':
        l,q=boot(); s=10**(-6/20)/max(max(abs(x) for x in l),max(abs(x) for x in q))
        with wave.open(f'{out}/{k}.wav','wb') as w:
            w.setnchannels(2); w.setsampwidth(2); w.setframerate(SR); w.writeframes(b''.join(struct.pack('<hh',int(a*s*32767),int(c*s*32767)) for a,c in zip(l,q)))
        continue
    b=make(k); s=PEAK/max(abs(x) for x in b)
    with wave.open(f'{out}/{k}.wav','wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes(b''.join(struct.pack('<h',int(x*s*32767)) for x in b))
for d in (f'{R}/../tv-app/app/src/main/assets/sound',):
    os.makedirs(d,exist_ok=True)
    for k in EV: shutil.copy(f'{out}/{k}.wav',d)
print('ok:',len(EV),'sounds,',sum(os.path.getsize(f'{out}/{k}.wav') for k in EV)//1024,'KB')
