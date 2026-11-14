#!/usr/bin/env python3
"""Top-down picture of snapshots lit by a low sun, so gullies and ribs show.
    python3 shade.py <out.png> <x0> <z0> <size> [label=]file.bin ...   (x0 z0: world corner of the crop)"""
import sys, re, numpy as np
from PIL import Image, ImageDraw, ImageFont
out,cx,cz,size=sys.argv[1],int(sys.argv[2]),int(sys.argv[3]),int(sys.argv[4])
tiles=[]
for a in sys.argv[5:]:
    label,f=a.split('=',1) if '=' in a else (a,a)
    x0,z0=[int(v) for v in re.findall(r'_(-?\d+)_(-?\d+)(?:_\d+)?\.bin',f)[0]]
    raw=np.fromfile(f,dtype='<i2').reshape(-1,2); n=int(len(raw)**.5); h=raw[:,0].reshape(n,n).astype(float)
    h=h[cz-z0:cz-z0+size,cx-x0:cx-x0+size]
    from scipy.ndimage import gaussian_filter
    h=gaussian_filter(h,1.2)          # heights are whole blocks: without this every block step is a stripe
    gz,gx=np.gradient(h)
    az,alt=np.radians(315),np.radians(55)
    lx,lz,ly=np.sin(az)*np.cos(alt),-np.cos(az)*np.cos(alt),np.sin(alt)
    nrm=np.sqrt(gx*gx+gz*gz+1)
    sh=np.clip(((-gx*lx-gz*lz+ly)/nrm-0.15)/0.85,0,1)**0.8
    img=Image.fromarray(np.uint8(40+215*sh)).resize((640,640),Image.NEAREST).convert('RGB')
    d=ImageDraw.Draw(img); f2=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',22)
    d.rectangle((0,0,640,30),fill='white'); d.text((6,3),label,fill='black',font=f2); tiles.append(img)
s=Image.new('RGB',(646*len(tiles)-6,640),'white')
for k,t in enumerate(tiles): s.paste(t,(k*646,0))
s.save(out); print('wrote',out)
