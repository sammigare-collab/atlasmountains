#!/usr/bin/env python3
"""Side-by-side pictures of preview snapshots: for each, a sun-lit view (for the shape) and a view lit as the game lights it.
    python3 panel.py <out.png> <x y z yaw pitch> label=stem [label=stem ...]"""
import glob, os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import render_snapshot as rs
out = sys.argv[1]; cam = [float(v) for v in sys.argv[2:7]]
W, H = 640, 360
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 18)
cols = []
for a in sys.argv[7:]:
    label, stem = a.split('=', 1)
    path = stem if stem.endswith('.bin') else glob.glob(stem + '_*_*.bin')[0]
    x0, z0, height, ids, names, pal = rs.load(path)
    lit = Image.fromarray(rs.view(x0, z0, height, ids, pal, cam[0:3], cam[3], cam[4], 70, w=W, h=H, sun=(250, 32)))
    game = Image.fromarray(rs.view(x0, z0, height, ids, pal, cam[0:3], cam[3], cam[4], 70, w=W, h=H))
    ImageDraw.Draw(lit).text((6, 4), label + '  (sun-lit: shape only)', fill=(0, 0, 0), font=font)
    ImageDraw.Draw(game).text((6, 4), label + '  (as the game lights it)', fill=(0, 0, 0), font=font)
    cols.append((lit, game))
sheet = Image.new('RGB', (len(cols) * (W + 4) - 4, 2 * H + 4), (255, 255, 255))
for k, (lit, game) in enumerate(cols):
    sheet.paste(lit, (k * (W + 4), 0)); sheet.paste(game, (k * (W + 4), H + 4))
sheet.save(out); print('wrote', out)
