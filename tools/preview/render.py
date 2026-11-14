#!/usr/bin/env python3
"""Renders preview snapshots side by side.

    python3 tools/preview/render.py <out.png> <map|view> [x y z yaw pitch fov] -- <stem> [<stem> ...]

Each stem is a path written by Preview without the _x0_z0.bin ending, e.g. out/joined.
"""
import glob, os, sys
import numpy as np
from PIL import Image, ImageDraw
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import render_snapshot as rs

args = sys.argv[1:]
out, mode = args[0], args[1]
cut = args.index('--')
cam = [float(v) for v in args[2:cut]]
stems = args[cut + 1:]
tiles = []
for s in stems:
    path = glob.glob(s + '_*_*.bin')[0]
    x0, z0, height, ids, names, pal = rs.load(path)
    if mode == 'map':
        img = rs.top_map(height, ids, pal)
        if img.shape[0] > 700:
            img = np.array(Image.fromarray(img).resize((700, 700), Image.LANCZOS))
    else:
        img = rs.view(x0, z0, height, ids, pal, cam[0:3], cam[3], cam[4], cam[5] if len(cam) > 5 else 70, w=960, h=540)
    im = Image.fromarray(img)
    ImageDraw.Draw(im).text((8, 6), os.path.basename(s), fill=(0, 0, 0))
    tiles.append(im)
W = sum(t.width for t in tiles) + 6 * (len(tiles) - 1)
sheet = Image.new('RGB', (W, max(t.height for t in tiles)), (255, 255, 255))
x = 0
for t in tiles:
    sheet.paste(t, (x, 0)); x += t.width + 6
sheet.save(out)
print('wrote', out)
