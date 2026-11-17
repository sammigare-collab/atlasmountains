#!/usr/bin/env python3
"""Before/after sheet from real-chunk snapshots, lit as the game lights them (and, with --sun, by a sun as well).
    python3 sheet.py <out.png> [--sun] <before.bin> <after.bin> <label before> <label after> "x y z yaw pitch" ["x y z yaw pitch" ...]"""
import os, sys
import numpy as np
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..'))
import render_snapshot as rs
a = sys.argv[1:]
out = a.pop(0)
sun = None
if a[0] == '--sun': a.pop(0); sun = (250, 32)
before, after, lb, la = a[:4]; cams = [[float(v) for v in c.split()] for c in a[4:]]
W, H = 960, 540
font = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 26)
sheet = Image.new('RGB', (2 * W + 8, len(cams) * (H + 8) + 44), (255, 255, 255))
d = ImageDraw.Draw(sheet)
for c, (path, label) in enumerate(((before, lb), (after, la))):
    x0, z0, height, ids, names, pal = rs.load(path)
    d.text((c * (W + 8) + 10, 8), label, fill=(0, 0, 0), font=font)
    for r, cam in enumerate(cams):
        img = rs.view(x0, z0, height, ids, pal, cam[0:3], cam[3], cam[4], 75, w=W, h=H, sun=sun)
        sheet.paste(Image.fromarray(img), (c * (W + 8), 44 + r * (H + 8)))
sheet.save(out); print('wrote', out)
