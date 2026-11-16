import numpy as np, glob, sys
from scipy import ndimage
from PIL import Image, ImageDraw, ImageFont
# five climate colours, cold to hot; all checks of the palette validator pass (adjacent pairs, colour-blind and normal vision)
CLIM=[('Frozen','#2458b8'),('Cold','#58aee8'),('Temperate','#008300'),('Warm','#eda100'),('Hot','#cf3a2a')]
EDGES=[-0.45,-0.15,0.2,0.55]
SEA,DEEP,COAST,MOUNT,INK=(244,246,248),(228,234,240),(58,63,69),(28,30,33),(34,36,40)
def rgb(h): return tuple(int(h[i:i+2],16) for i in (1,3,5))
F=lambda s: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',s)
FB=lambda s: ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',s)
def panel(D,tag,title,W=900,legend=False):
    size=int(tag.split('_')[0])
    def L(q):
        a=np.fromfile(glob.glob(f'{D}/dfmap_{q}_0_0_{tag}.f32')[0],dtype='<f4'); n=int(round(len(a)**0.5)); return a.reshape(n,n)
    c=L('minecraft_overworld_continents'); ld=128+128*L('atlasmountains_overworld_land'); st=L('atlasmountains_overworld_strength'); t=L('minecraft_overworld_temperature')
    m=c.shape[0]; sea=c<=-0.16      # inside the shore line counts as land: rivers and lakes inland are not drawn
    cls=np.digitize(t,EDGES)
    img=np.zeros((m,m,3),np.uint8); img[:]=SEA; img[sea&(c<-0.455)]=DEEP
    for k,(_,h) in enumerate(CLIM): img[(~sea)&(cls==k)]=rgb(h)
    img[(~sea)&(st>0.45)]=MOUNT
    im=Image.fromarray(img).resize((W,W),Image.LANCZOS)
    mask=np.array(Image.fromarray((~sea).astype(np.uint8)*255).resize((W,W),Image.LANCZOS))>127
    edge=mask&~ndimage.binary_erosion(mask)
    a=np.array(im); a[edge]=COAST; im=Image.fromarray(a)
    d=ImageDraw.Draw(im)
    d.rectangle((0,0,W,34),fill=(255,255,255)); d.text((10,7),title,fill=INK,font=FB(18))
    cx=W/2; d.ellipse((cx-9,cx-9,cx+9,cx+9),outline=(255,255,255),width=5); d.ellipse((cx-9,cx-9,cx+9,cx+9),outline=(0,0,0),width=2)
    bar=10000 if size<100000 else 50000; Lb=bar/size*W
    d.rectangle((W-Lb-24,W-44,W-12,W-8),fill=(255,255,255)); d.rectangle((W-Lb-18,W-18,W-18,W-14),fill=INK); d.text((W-Lb-18,W-40),'{:,} blocks'.format(bar),fill=INK,font=F(15))
    d.text((W-40,44),'N',fill=INK,font=FB(18)); d.line((W-33,92,W-33,68),fill=INK,width=2); d.polygon([(W-33,62),(W-38,72),(W-28,72)],fill=INK)
    if legend:
        x0,y0=12,W-12-7*26-14; d.rectangle((x0,y0,x0+168,W-12),fill=(255,255,255),outline=(200,204,208))
        y=y0+8
        for name,h in CLIM+[('Mountains','#1c1e21')]:
            d.rectangle((x0+10,y+2,x0+34,y+20),fill=rgb(h)); d.text((x0+44,y+1),name,fill=INK,font=F(16)); y+=26
        d.ellipse((x0+13,y+2,x0+31,y+20),outline=(0,0,0),width=2); d.text((x0+44,y+1),'Spawn (0 0)',fill=INK,font=F(16))
    shore=c>-0.16; step=size/m
    lab,k=ndimage.label(shore); sizes=np.sort(ndimage.sum(shore,lab,range(1,k+1))*step*step)[::-1]
    shares=[100*((cls==i)&~sea).sum()/max(1,(~sea).sum()) for i in range(5)]
    print('%s | %d across: land %.1f%%; three largest landmasses %.0f, %.0f, %.0f blocks across (as squares), holding %.0f%%, %.0f%%, %.0f%% of the land; mountain country %.1f%% of land; climate of the land: %s'%(title,size,100*shore.mean(),*np.sqrt(sizes[:3]),*(100*sizes[:3]/sizes.sum()),100*((st>0.45)&shore).sum()/max(1,shore.sum()),', '.join('%s %.0f%%'%(CLIM[i][0],shares[i]) for i in range(5))))
    return im
out=sys.argv[1]; panels=[]
for k,arg in enumerate(sys.argv[2:]):
    D,tag,title=arg.split('|'); panels.append(panel(D,tag,title,legend=(k==0)))
s=Image.new('RGB',(sum(p.width for p in panels)+8*(len(panels)-1),panels[0].height),(255,255,255)); x=0
for p in panels: s.paste(p,(x,0)); x+=p.width+8
s.save(out)
