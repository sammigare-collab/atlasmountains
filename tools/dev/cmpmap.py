import numpy as np, sys
n=512
def L(d,q): return np.fromfile(f'/tmp/{d}/dfmap_{q}_-500_400_8192_16.f32',dtype='<f4').reshape(n,n)
a=L('M100','atlasmountains_overworld_peak_height')*128
v=128+128*L('MC','atlasmountains_overworld_vanilla_offset'); e=L('MC','minecraft_overworld_erosion'); c=L('MC','minecraft_overworld_continents')
country=(e<-0.375)&(c>-0.11); tall=v>120
for d in sys.argv[1:]:
    b=L(d,'atlasmountains_overworld_peak_height')*128; land=128+128*L(d,'atlasmountains_overworld_land')
    same=(np.abs(a-b)<1)&(a>5)
    print(f'{d}: mountain area {100*(b>5).mean():.2f}% of the square = {100*(b>5).sum()/(a>5).sum():.0f}% of 0.6.1; of 0.6.1\'s mountain ground unchanged {100*same.sum()/(a>5).sum():.0f}%; on vanilla land above y120: {100*(b[tall]>5).mean():.0f}% (0.6.1 {100*(a[tall]>5).mean():.0f}%); on vanilla mountain country {100*(b[country]>5).mean():.0f}% (0.6.1 {100*(a[country]>5).mean():.0f}%); top y{(land+b).max():.0f}')
