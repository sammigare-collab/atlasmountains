import numpy as np, sys
from PIL import Image, ImageDraw, ImageFont
n=512
def L(d,q): return np.fromfile(f'/tmp/{d}/dfmap_{q}_-500_400_8192_16.f32',dtype='<f4').reshape(n,n)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',20)
def draw(d):
    land=128+128*L(d,'atlasmountains_overworld_land'); ph=L(d,'atlasmountains_overworld_peak_height')*128
    h=land+ph
    img=np.zeros((n,n,3))
    sea=land<62
    t=np.clip((land-62)/90,0,1)[...,None]
    img[:]=np.array([96,140,72])*(1-t)+np.array([150,150,105])*t        # low land green, higher land dun
    img[sea&(ph<=5)]=np.array([58,96,160])
    m=ph>5
    gy,gx=np.gradient(h)
    shade=np.clip(1+0.045*(gx+gy),0.45,1.5)
    img[m]=np.array([120,122,128])
    snow=m&(ph>150)
    img[snow]=np.array([238,240,245])
    img*=shade[...,None]
    return Image.fromarray(np.clip(img,0,255).astype(np.uint8)), m.mean()
tiles=[]
for arg in sys.argv[2:]:
    d,label=arg.split('=',1)
    im,share=draw(d)
    im=im.resize((640,640),Image.LANCZOS)
    dr=ImageDraw.Draw(im); dr.rectangle((0,0,640,30),fill=(0,0,0)); dr.text((8,4),label,fill=(255,255,255),font=font)
    # where he stood
    px=(493-(-500-4096))/8192*640; pz=(774-(400-4096))/8192*640
    dr.ellipse((px-5,pz-5,px+5,pz+5),outline=(255,0,0),width=2)
    tiles.append(im)
W=len(tiles)*644-4
sheet=Image.new('RGB',(W,640),(255,255,255))
for k,t in enumerate(tiles): sheet.paste(t,(k*644,0))
sheet.save(sys.argv[1]); print(sheet.size)
