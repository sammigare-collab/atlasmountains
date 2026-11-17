import numpy as np, sys
sys.path.insert(0,'/home/claude/atlasmountains/tools')
import render_snapshot as rs
from PIL import Image
# usage: topdown.py out.png snapshot.bin [snapshot2.bin ...]  : top-down maps side by side
tiles=[]
for f in sys.argv[2:]:
    x0,z0,h,ids,names,pal=rs.load(f)
    img=pal[ids].copy()
    gy,gx=np.gradient(h)
    img*=np.clip(1+0.07*(gx+gy),0.4,1.6)[...,None]
    wat=np.array([nm.endswith(':water') for nm in names])[ids]
    print(f, 'height', h.min(), h.max(), 'water share %.3f'%wat.mean(), 'magenta:', [nm for i,nm in enumerate(names) if tuple(pal[i])==(200,0,200) and (ids==i).mean()>0.002])
    tiles.append(Image.fromarray(np.clip(img,0,255).astype(np.uint8)).resize((768,768),Image.LANCZOS))
sheet=Image.new('RGB',(len(tiles)*772-4,768),(255,255,255))
for k,t in enumerate(tiles): sheet.paste(t,(k*772,0))
sheet.save(sys.argv[1])
