#!/usr/bin/env python3
"""Draws a snapshot written by /atlasmountains snapshot <size> (real generated chunks).

    python3 tools/render_snapshot.py <snapshot .bin> map out.png
    python3 tools/render_snapshot.py <snapshot .bin> view out.png <x> <y> <z> <yaw> <pitch> [fov]

"map" is a top-down map: each column's top block colour, lit from the north-west only to show
the shape. "view" is a perspective view from (x, y, z), yaw 0 looking south (+z), 90 west, like the
game's F3 facing. Faces are lit as Minecraft lights them with smooth lighting off: tops at full
brightness, north and south faces 0.8, east and west faces 0.6. There is no sun direction in the
game, so none here either. Block colours are rough averages of the textures; grass and leaves are
given a plains-like tint. A heightfield only: overhangs and caves are not drawn.
"""
import os
import sys

import numpy as np
from PIL import Image

COLOURS = {
    'snow_block': (240, 246, 246), 'snow': (240, 246, 246), 'powder_snow': (240, 246, 246),
    'stone': (125, 125, 125), 'andesite': (136, 136, 137), 'tuff': (108, 109, 103),
    'deepslate': (80, 80, 83), 'smooth_basalt': (72, 72, 78), 'calcite': (223, 224, 220),
    'gravel': (131, 127, 126), 'dirt': (134, 96, 67), 'coarse_dirt': (119, 85, 59),
    'grass_block': (110, 155, 75), 'podzol': (91, 63, 24), 'moss_block': (89, 109, 45),
    'water': (60, 90, 200), 'ice': (145, 183, 253), 'packed_ice': (141, 180, 250), 'blue_ice': (116, 167, 253),
    'sand': (219, 207, 163), 'clay': (160, 166, 179), 'mud': (60, 57, 61),
    'short_grass': (110, 155, 75), 'tall_grass': (110, 155, 75), 'fern': (100, 140, 70),
    'spruce_leaves': (60, 90, 60), 'oak_leaves': (70, 120, 45), 'birch_leaves': (110, 140, 70),
    'spruce_log': (58, 37, 16), 'oak_log': (109, 85, 50), 'birch_log': (216, 215, 210),
    'diorite': (188, 188, 189), 'granite': (149, 103, 85), 'coal_ore': (105, 105, 105), 'iron_ore': (136, 129, 122),
    'copper_ore': (124, 125, 120), 'emerald_ore': (117, 136, 124), 'deepslate_iron_ore': (106, 99, 94),
    'deepslate_coal_ore': (74, 74, 76), 'deepslate_emerald_ore': (78, 104, 87), 'deepslate_copper_ore': (92, 93, 89),
    'gold_ore': (145, 133, 106), 'lapis_ore': (107, 117, 141), 'redstone_ore': (140, 109, 109),
    'jungle_leaves': (60, 140, 40), 'dark_oak_leaves': (60, 100, 40), 'acacia_leaves': (90, 120, 40),
    'vine': (70, 110, 40), 'wildflowers': (120, 160, 80), 'bush': (90, 140, 60), 'leaf_litter': (130, 100, 60),
    'dandelion': (110, 155, 75), 'poppy': (110, 155, 75), 'brown_mushroom': (110, 155, 75), 'red_mushroom': (110, 155, 75),
    'lava': (207, 92, 20), 'magma_block': (142, 63, 31), 'netherrack': (97, 38, 38), 'obsidian': (15, 10, 24),
    'seagrass': (60, 90, 200), 'tall_seagrass': (60, 90, 200), 'mossy_cobblestone': (110, 118, 94),
    'cobblestone': (127, 127, 127), 'stone_bricks': (122, 121, 122),
}
DEFAULT = (200, 0, 200)   # anything not in the table shows magenta, so it is noticed


def load(path):
    raw = np.fromfile(path, dtype='<i2').reshape(-1, 2)
    n = int(round(np.sqrt(len(raw))))
    names = open(path[:-4] + '.txt').read().split('\n')
    parts = os.path.basename(path)[:-4].split('_')
    x0, z0 = int(parts[1]), int(parts[2])
    height = raw[:, 0].reshape(n, n).astype(np.float64)   # [z, x]
    ids = raw[:, 1].reshape(n, n)
    pal = np.array([COLOURS.get(nm.split(':')[-1], DEFAULT) for nm in names], dtype=np.float64)
    return x0, z0, height, ids, names, pal


def top_map(height, ids, pal):
    col = pal[ids]
    gz, gx = np.gradient(height)
    light = np.clip(1.0 + 0.35 * (-gx - gz) / np.sqrt(2), 0.55, 1.25)
    return np.clip(col * light[..., None], 0, 255).astype(np.uint8)


def view(x0, z0, height, ids, pal, cam, yaw, pitch, fov=70, w=1280, h=720):
    n = height.shape[0]
    yr, pr = np.radians(yaw), np.radians(pitch)
    fwd = np.array([-np.sin(yr) * np.cos(pr), -np.sin(pr), np.cos(yr) * np.cos(pr)])
    right = np.array([-np.cos(yr), 0.0, -np.sin(yr)])
    up = np.cross(right, fwd)
    t = np.tan(np.radians(fov) / 2)
    u = (np.arange(w) + 0.5) / w * 2 - 1
    v = 1 - (np.arange(h) + 0.5) / h * 2
    U, V = np.meshgrid(u * t * w / h, v * t)
    d = fwd[None, None, :] + U[..., None] * right + V[..., None] * up
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    d = d.reshape(-1, 3)
    m = len(d)
    ox, oy, oz = cam[0] - x0, cam[1], cam[2] - z0
    # 2-D DDA over the columns; the top of column (i, j) is at height[j, i] + 1
    cx = np.full(m, int(np.floor(ox))); cz = np.full(m, int(np.floor(oz)))
    sx = np.where(d[:, 0] > 0, 1, -1); sz = np.where(d[:, 2] > 0, 1, -1)
    with np.errstate(divide='ignore'):
        tdx = np.abs(1 / d[:, 0]); tdz = np.abs(1 / d[:, 2])
    tmx = np.where(d[:, 0] > 0, (cx + 1 - ox), (ox - cx)) * tdx
    tmz = np.where(d[:, 2] > 0, (cz + 1 - oz), (oz - cz)) * tdz
    tcur = np.zeros(m)
    out = np.zeros((m, 3)); done = np.zeros(m, bool); face = np.zeros(m, int)  # 0 sky
    sky = np.array([150, 180, 255.0])
    face_axis = np.zeros(m, int)                        # the axis of the step that entered the current column
    for _ in range(int(2.2 * n)):
        act = ~done
        if not act.any():
            break
        inside = (cx >= 0) & (cx < n) & (cz >= 0) & (cz < n)
        lost = act & ~inside & (((cx < 0) & (sx < 0)) | ((cx >= n) & (sx > 0)) | ((cz < 0) & (sz < 0)) | ((cz >= n) & (sz > 0)))
        done |= lost
        act &= ~lost
        idx = np.where(act & inside)[0]
        if len(idx):
            top = height[cz[idx], cx[idx]] + 1
            ty = oy + d[idx, 1] * tcur[idx]                 # height where the ray enters this column
            texit = np.minimum(tmx[idx], tmz[idx])
            yexit = oy + d[idx, 1] * texit
            side_hit = ty <= top                            # entered below the top: hit a side face
            top_hit = ~side_hit & (yexit <= top)            # came down onto the top inside the column
            hit = side_hit | top_hit
            hi = idx[hit]
            done[hi] = True
            face[idx[top_hit]] = 1
            sh = idx[side_hit]
            face[sh] = np.where(face_axis[sh] == 0, 3, 2)   # 3: east/west face, 2: north/south face
            out[hi] = pal[ids[cz[hi], cx[hi]]]
        step_x = act & (tmx < tmz)
        step_z = act & ~step_x
        face_axis = np.where(step_x, 0, np.where(step_z, 1, face_axis))
        tcur = np.where(step_x, tmx, np.where(step_z, tmz, tcur))
        cx = np.where(step_x, cx + sx, cx); tmx = np.where(step_x, tmx + tdx, tmx)
        cz = np.where(step_z, cz + sz, cz); tmz = np.where(step_z, tmz + tdz, tmz)
    shade = np.array([1.0, 1.0, 0.8, 0.6])[face]
    img = np.where((face > 0)[:, None], out * shade[:, None], sky)
    fog = np.clip((tcur - 0.45 * n) / (1.3 * n), 0, 0.6)[:, None] * (face > 0)[:, None]
    img = img * (1 - fog) + sky * fog
    return np.clip(img.reshape(h, w, 3), 0, 255).astype(np.uint8)


if __name__ == '__main__':
    path, mode, out = sys.argv[1], sys.argv[2], sys.argv[3]
    x0, z0, height, ids, names, pal = load(path)
    if mode == 'map':
        Image.fromarray(top_map(height, ids, pal)).save(out)
    else:
        cam = [float(a) for a in sys.argv[4:7]]
        yaw, pitch = float(sys.argv[7]), float(sys.argv[8])
        fov = float(sys.argv[9]) if len(sys.argv) > 9 else 70
        Image.fromarray(view(x0, z0, height, ids, pal, cam, yaw, pitch, fov)).save(out)
    print('wrote', out)
