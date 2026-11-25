"""Ground-level views of a heightmap dumped by /atlasmountains heightmap.  python3 view_f32.py file.f32 step out_prefix [cam specs: x,z,yaw ...] (x, z relative to the centre)"""
import sys, numpy as np
from PIL import Image
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from prototype_horns import colour, ground_view
f, step, out = sys.argv[1], float(sys.argv[2]), sys.argv[3]
h = np.fromfile(f, dtype='<f4'); n = int(round(len(h) ** 0.5)); H = h.reshape(n, n).astype(float)
print('heights', H.min(), H.max(), 'columns above y 200:', int((H > 200).sum()))
C = colour(H, step, snow=165, tree=118)
Image.fromarray(C.astype(np.uint8)).save(out + '_top.png')
cams = [tuple(float(v) for v in a.split(',')) for a in sys.argv[4:]] or [(0, -n * step * 0.42, 0), (n * step * 0.42, 0, 270), (0, n * step * 0.42, 180), (-n * step * 0.42, 0, 90)]
ims = [Image.fromarray(ground_view(H, C, step, (cx, cz), yaw)) for cx, cz, yaw in cams]
w, hh = ims[0].size; cols = 2 if len(ims) > 1 else 1; rows = (len(ims) + cols - 1) // cols
o = Image.new('RGB', (w * cols, hh * rows)); [o.paste(im, ((k % cols) * w, (k // cols) * hh)) for k, im in enumerate(ims)]; o.save(out + '_views.png')
