import sys, glob, numpy as np
sys.path.insert(0,'/home/claude/atlasmountains/tools')
import render_snapshot as R
from PIL import Image, ImageDraw
A,B,la,lb,out=sys.argv[1:6]
V=[('your snow-hole spot: 163 331 -523 looking east',(163,331,-523,270,28)),('your spot 165 139 -868 looking south',(165,150,-868,0,-5)),('the mountain at 123 -350 from the north-west',(-60,400,-560,315,32)),('from above: -263 440 -15 looking north-west',(-263,440,-15,135,35))]
sheet=Image.new('RGB',(1920,540*len(V)))
for k,(d,lab) in enumerate(((A,la),(B,lb))):
    x0,z0,h,ids,names,pal=R.load(glob.glob(d+'snapshot_*.bin')[0])
    nm=np.array([q.split(':')[1] for q in names]); b=nm[ids]
    rock=np.isin(b,['stone','tuff','andesite','deepslate','smooth_basalt','calcite','gravel']); snow=np.isin(b,['snow','snow_block','powder_snow']); water=np.isin(b,['water','ice'])
    tree=np.char.endswith(b,'_leaves')|np.char.endswith(b,'_log'); grass=np.isin(b,['grass_block','short_grass','tall_grass','fern','dirt','podzol','coarse_dirt'])
    a=np.fromfile(glob.glob(d+'dfmap_*peak_height*')[0],'<f4'); n=int(round(a.size**0.5)); pk=128*np.kron(a.reshape(n,n),np.ones((2,2)))
    m=pk>=2
    big=sum(np.roll(np.roll(snow,p,0),q,1) for p in range(-6,7,3) for q in range(-6,7,3))
    print(lab,'mountains %.0f%% of square; on them: rock %.0f%% snow %.0f%% open grass %.0f%% trees %.0f%% | tops %d %d | snow cols %d, bare inside caps %d (%.2f%%)'%(100*m.mean(),100*rock[m].mean(),100*snow[m].mean(),100*grass[m].mean(),100*tree[m].mean(),h[-350-z0-10:-350-z0+11,123-x0-10:123-x0+11].max(),h[-505-z0-10:-505-z0+11,-601-x0-10:-601-x0+11].max(),snow.sum(),((~snow)&(big>=20)).sum(),100*((~snow)&(big>=20)).sum()/max(1,snow.sum())))
    for r,(t,c) in enumerate(V):
        c=list(c); j,i=int(c[2])-z0,int(c[0])-x0
        if 0<=j<h.shape[0] and 0<=i<h.shape[1]: c[1]=max(c[1],h[j,i]+8)
        im=Image.fromarray(R.view(x0,z0,h,ids,pal,c[:3],c[3],c[4],72,w=960,h=540)); ImageDraw.Draw(im).text((8,5),lab+'  '+t,fill=(255,255,0)); sheet.paste(im,(960*k,540*r))
sheet.save(out); sheet.resize((960,270*len(V))).save(out.replace('.png','_small.png'))
