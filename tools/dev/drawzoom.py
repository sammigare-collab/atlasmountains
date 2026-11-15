import numpy as np, sys
from PIL import Image, ImageDraw, ImageFont
n=512
def L(d,q): return np.fromfile(f'/tmp/{d}/dfmap_{q}_-500_400_8192_16.f32',dtype='<f4').reshape(n,n)
font=ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',20)
X0,Z0=-500-4096,400-4096
def draw(d,box):
    land=128+128*L(d,'atlasmountains_overworld_land'); ph=L(d,'atlasmountains_overworld_peak_height')*128
    h=land+ph
    img=np.zeros((n,n,3))
    t=np.clip((land-62)/90,0,1)[...,None]
    img[:]=np.array([96,140,72])*(1-t)+np.array([150,150,105])*t
    img[(land<62)&(ph<=5)]=np.array([58,96,160])
    m=ph>5
    gy,gx=np.gradient(h)
    shade=np.clip(1+0.045*(gx+gy),0.45,1.5)
    img[m]=np.array([120,122,128]); img[m&(ph>150)]=np.array([238,240,245])
    img*=shade[...,None]
    x1,z1,x2,z2=box
    i1,j1,i2,j2=(x1-X0)//16,(z1-Z0)//16,(x2-X0)//16,(z2-Z0)//16
    crop=np.clip(img[j1:j2,i1:i2],0,255).astype(np.uint8)
    return Image.fromarray(crop), m[j1:j2,i1:i2].mean()
box=tuple(int(v) for v in sys.argv[2].split(','))
tiles=[]
for arg in sys.argv[3:]:
    d,label=arg.split('=',1)
    im,share=draw(d,box)
    w=760; hgt=int(im.height*w/im.width)
    im=im.resize((w,hgt),Image.LANCZOS)
    dr=ImageDraw.Draw(im); dr.rectangle((0,0,w,30),fill=(0,0,0)); dr.text((8,4),label+f' (mountain on {share*100:.0f}% of this view)',fill=(255,255,255),font=font)
    px=(493-box[0])/(box[2]-box[0])*w; pz=(774-box[1])/(box[3]-box[1])*hgt
    dr.ellipse((px-6,pz-6,px+6,pz+6),outline=(255,0,0),width=3)
    tiles.append(im)
sheet=Image.new('RGB',(tiles[0].width,sum(t.height+4 for t in tiles)-4),(255,255,255)); y=0
for t in tiles: sheet.paste(t,(0,y)); y+=t.height+4
sheet.save(sys.argv[1]); print(sheet.size)
