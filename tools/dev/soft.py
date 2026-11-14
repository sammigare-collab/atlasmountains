import sys, re, glob, numpy as np
sys.path.insert(0,'/home/claude/atlasmountains/tools')
import render_snapshot as R
d=sys.argv[1]
x0,z0,h,ids,names,pal=R.load(glob.glob(d+'snapshot_*.bin')[0]); N=h.shape[0]; hh=h.astype(float)
nm=np.array([q.split(':')[1] for q in names]); b=nm[ids]; snow=np.isin(b,['snow','snow_block','powder_snow'])
log=open(d+'peaks.txt').read() if glob.glob(d+'peaks.txt') else open('/tmp/chk/run50.log').read()
open(d+'peaks.txt','w').write(log)
P=[(m[0][0],int(m[1]),int(m[2]),int(m[3]),int(m[4])) for m in re.findall(r'((?:S very short|M short|L medium) crest):\s+/tp @s (-?\d+) (-?\d+) (-?\d+)\s+\(\d+ blocks away, (\d+) tall\)',log)]
lap=np.abs(np.roll(hh,4,0)+np.roll(hh,-4,0)+np.roll(hh,4,1)+np.roll(hh,-4,1)-4*hh)
gz,gx=np.gradient(hh); asp=np.arctan2(gz,gx)
yy,xx=np.mgrid[0:N,0:N]
rows=[]
for look,x,y,z,t in P:
    i,j=x-x0,z-z0
    if not (60<i<N-60 and 60<j<N-60) or t<90: continue
    r=np.hypot(xx-i,yy-j); m=(r>8)&(r<0.75*t)&(hh>hh[j,i]-0.75*t)
    # how much the downhill direction departs from "straight away from the summit": 0 on a cone, large where ribs and gullies turn the slope
    radial=np.arctan2(yy-j,xx-i); dev=np.abs(np.angle(np.exp(1j*(asp+np.pi-radial))))
    top=hh[max(0,j-10):j+11,max(0,i-10):i+11].max()
    rows.append((look,x,z,t,lap[m].mean(),np.degrees(dev[m]).mean(),top))
print('look   x     z    tall  roughness  turn(deg)  top y')
for r_ in sorted(rows,key=lambda r:r[5]): print('%s  %6d %6d  %4d   %5.2f     %5.1f     %d'%r_)
for L in 'SML':
    q=[r for r in rows if r[0]==L]
    if q: print(L,'n=%d  roughness mean %.2f  turn mean %.1f'%(len(q),np.mean([r[4] for r in q]),np.mean([r[5] for r in q])))
# holes in snow: non-snow columns with at least 7 of 8 neighbours snow
nb=sum(np.roll(np.roll(snow,a,0),c,1) for a in (-1,0,1) for c in (-1,0,1) if (a,c)!=(0,0))
big=sum(np.roll(np.roll(snow,a,0),c,1) for a in range(-6,7,3) for c in range(-6,7,3))
print('snow columns %d; bare columns inside the caps (snow on 20+ of 25 points around): %d'%(snow.sum(),((~snow)&(big>=20)).sum()))
