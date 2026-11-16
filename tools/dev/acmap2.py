import numpy as np, glob, sys
from scipy import ndimage
from PIL import Image, ImageDraw, ImageFont
D,tag,title,out=sys.argv[1:5]
size=int(tag.split('_')[0])
def L(q):
    a=np.fromfile(glob.glob(f'{D}/dfmap_{q}_0_0_{tag}.f32')[0],dtype='<f4'); n=int(round(len(a)**0.5)); return a.reshape(n,n)
c=L('minecraft_overworld_continents'); ld=128+128*L('atlasmountains_overworld_land'); st=L('atlasmountains_overworld_strength'); t=L('minecraft_overworld_temperature')
m=c.shape[0]; step=size/m; shore=c>-0.16
lab,k=ndimage.label(shore); sizes=np.sort(ndimage.sum(shore,lab,range(1,k+1))*step*step)[::-1]
print('%s, %d blocks across: land inside the shore line %.1f%%; the three largest landmasses hold %.0f%%, %.0f%% and %.0f%% of the land (%.0f, %.0f and %.0f blocks across as squares); landmasses over 2 km: %d; mountain country %.1f%% of the land; continentalness at 0 0 %.2f'%(title,size,100*shore.mean(),*(100*sizes[:3]/sizes.sum()),*np.sqrt(sizes[:3]),(sizes>4e6).sum(),100*((st>0.45)&shore).sum()/max(1,shore.sum()),c[m//2,m//2]))
img=np.zeros((m,m,3)); sea=ld<62
tt=np.clip((ld-62)/100,0,1)[...,None]
img[:]=np.array([104,148,80])*(1-tt)+np.array([150,150,105])*tt
deep=np.clip((62-ld)/40,0,1)[...,None]
img[sea]=(np.array([70,120,190])*(1-deep)+np.array([36,70,140])*deep)[sea]
cold=np.clip((-t-0.15)/0.5,0,1)[...,None]; hot=np.clip((t-0.35)/0.5,0,1)[...,None]; landm=~sea
img[landm]=(img*(1-0.55*cold)+np.array([235,240,245])*0.55*cold)[landm]
img[landm]=(img*(1-0.45*hot)+np.array([214,186,110])*0.45*hot)[landm]
img[(st>0.45)&landm]=np.array([92,92,100])
gy,gx=np.gradient(ld); img*=np.clip(1+0.03*(gx+gy),0.6,1.4)[...,None]
W=760
im=Image.fromarray(np.clip(img,0,255).astype(np.uint8)).resize((W,W),Image.LANCZOS)
d=ImageDraw.Draw(im); font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',18)
d.rectangle((0,0,W,28),fill=(0,0,0)); d.text((8,4),title,fill=(255,255,255),font=font)
d.ellipse((W/2-7,W/2-7,W/2+7,W/2+7),outline=(255,0,0),width=3)
bar=10000 if size<100000 else 50000
L10=bar/size*W; d.rectangle((20,W-26,20+L10,W-20),fill=(255,255,255)); d.text((20,W-52),'{:,} blocks'.format(bar),fill=(255,255,255),font=font)
im.save(out)
