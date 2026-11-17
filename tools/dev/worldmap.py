# A map of one world round spawn: land by climate, real mountains, the area being pregenerated, the highest summits.
import numpy as np, glob, sys
from scipy import ndimage
from PIL import Image, ImageDraw, ImageFont
D,tag,title,out,radius=sys.argv[1],sys.argv[2],sys.argv[3],sys.argv[4],float(sys.argv[5])
CLIM=[('Frozen','#2458b8'),('Cold','#58aee8'),('Temperate','#008300'),('Warm','#eda100'),('Hot','#cf3a2a')]
EDGES=[-0.45,-0.15,0.2,0.55]
SEA,DEEP,COAST,MOUNT,SNOW,INK=(244,246,248),(228,234,240),(58,63,69),(28,30,33),(255,255,255),(34,36,40)
rgb=lambda h: tuple(int(h[i:i+2],16) for i in (1,3,5))
F=lambda s: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',s)
FB=lambda s: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',s)
size=int(tag.split('_')[0])
def L(q):
    f=glob.glob(f'{D}/dfmap_{q}_0_0_{tag}.f32')
    if not f: return None
    a=np.fromfile(f[0],dtype='<f4'); n=int(round(len(a)**0.5)); return a.reshape(n,n)
c=L('minecraft_overworld_continents'); ld=128+128*L('atlasmountains_overworld_land'); t=L('minecraft_overworld_temperature'); st=L('atlasmountains_overworld_strength'); ph=L('atlasmountains_overworld_peak_height')
m=c.shape[0]; step=size/m; sea=c<=-0.16
real=ph is not None
mount=(ph*128>5) if real else (st>0.45)
cls=np.digitize(t,EDGES)
img=np.zeros((m,m,3),np.uint8); img[:]=SEA; img[sea&(c<-0.455)]=DEEP
for k,(_,h) in enumerate(CLIM): img[(~sea)&(cls==k)]=rgb(h)
img[mount&~sea]=MOUNT
if real: img[(ph*128>150)&~sea]=SNOW        # the tall parts of a mountain, so each one can be picked out
W=1000
im=Image.fromarray(img).resize((W,W),Image.LANCZOS)
mask=np.array(Image.fromarray((~sea).astype(np.uint8)*255).resize((W,W),Image.LANCZOS))>127
a=np.array(im); a[mask&~ndimage.binary_erosion(mask)]=COAST; im=Image.fromarray(a); d=ImageDraw.Draw(im)
px=lambda x: (x+size/2)/size*W
r=radius/size*W; cx=px(0)
d.ellipse((cx-r,cx-r,cx+r,cx+r),outline=(0,0,0),width=3)
d.ellipse((cx-r-2,cx-r-2,cx+r+2,cx+r+2),outline=(255,255,255),width=1)
d.ellipse((cx-7,cx-7,cx+7,cx+7),fill=(255,255,255),outline=(0,0,0),width=2)
xs=(np.arange(m)-m//2)*step; X,Z=np.meshgrid(xs,xs); inside=np.hypot(X,Z)<=radius
tops=[]
if real:
    h=ld+ph*128; hm=np.where(mount&inside&~sea,h,0)
    mx=ndimage.maximum_filter(hm,size=15)          # one summit per 480 blocks or so
    zi,xi=np.nonzero((hm==mx)&(hm>0)); order=np.argsort(-hm[zi,xi])[:6]
    for n_,k in enumerate(order):
        x,z,y=xs[xi[k]],xs[zi[k]],hm[zi[k],xi[k]]; tops.append((int(x),int(z),int(y)))
        X0,Z0=px(x),px(z); d.ellipse((X0-11,Z0-11,X0+11,Z0+11),outline=(0,0,0),width=2)
        d.rectangle((X0+12,Z0-11,X0+30,Z0+9),fill=(255,255,255)); d.text((X0+16,Z0-11),str(n_+1),fill=INK,font=FB(16))
d.rectangle((0,0,W,34),fill=(255,255,255)); d.text((10,7),title,fill=INK,font=FB(18))
bar=2000 if size<30000 else 10000; Lb=bar/size*W
d.rectangle((W-Lb-24,W-44,W-12,W-8),fill=(255,255,255)); d.rectangle((W-Lb-18,W-18,W-18,W-14),fill=INK); d.text((W-Lb-18,W-40),'{:,} blocks'.format(bar),fill=INK,font=F(15))
d.text((W-40,44),'N',fill=INK,font=FB(18)); d.line((W-33,92,W-33,68),fill=INK,width=2); d.polygon([(W-33,62),(W-38,72),(W-28,72)],fill=INK)
rows=CLIM+[('Mountains' if real else 'Mountain country','#1c1e21')]+([('Mountain, upper part','#ffffff')] if real else [])
x0,y0=12,W-12-(len(rows)+2)*26-14; d.rectangle((x0,y0,x0+238,W-12),fill=(255,255,255),outline=(200,204,208)); y=y0+8
for name,hx in rows:
    d.rectangle((x0+10,y+2,x0+34,y+20),fill=rgb(hx),outline=(150,150,150)); d.text((x0+44,y+1),name,fill=INK,font=F(16)); y+=26
d.ellipse((x0+14,y+3,x0+30,y+19),fill=(255,255,255),outline=(0,0,0),width=2); d.text((x0+44,y+1),'Spawn (0 0)',fill=INK,font=F(16)); y+=26
d.ellipse((x0+12,y+2,x0+32,y+20),outline=(0,0,0),width=3); d.text((x0+44,y+1),'Chunky, radius {:,}'.format(int(radius)),fill=INK,font=F(16))
im.save(out)
land_in=(~sea&inside).sum()/inside.sum()
shares=[100*((cls==i)&~sea&inside).sum()/max(1,(~sea&inside).sum()) for i in range(5)]
print('%s: inside the circle land %.0f%%, mountains on %.1f%% of that land; climate of the land: %s'%(title,100*land_in,100*(mount&~sea&inside).sum()/max(1,(~sea&inside).sum()),', '.join('%s %.0f%%'%(CLIM[i][0],shares[i]) for i in range(5) if shares[i]>=0.5)))
for n_,(x,z,y) in enumerate(tops): print('  summit %d: x %d, z %d, about y %d'%(n_+1,x,z,y))
