"""Prototype of the horn-peak shape, for fast looks before anything goes in the game.
   python3 horns.py out_prefix [seed]"""
import sys, numpy as np
from PIL import Image

# ---- hashing and noise (vectorised) ----
def h2(ix, iz, seed):
    n = (ix.astype(np.int64) * 374761393 + iz.astype(np.int64) * 668265263 + int(seed) * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    n = ((n ^ (n >> 16)) * 2246822519) & 0xFFFFFFFF
    n = n ^ (n >> 15)
    return (n & 0xFFFFFF) / float(0x1000000)
def vnoise(x, z, seed):
    ix, iz = np.floor(x), np.floor(z); fx, fz = x - ix, z - iz
    ux, uz = fx*fx*(3-2*fx), fz*fz*(3-2*fz)
    a, b = h2(ix, iz, seed), h2(ix+1, iz, seed); c, d = h2(ix, iz+1, seed), h2(ix+1, iz+1, seed)
    return (a + (b-a)*ux + (c-a)*uz + (a-b-c+d)*ux*uz) * 2 - 1
def fbm(x, z, seed, oct=3):
    s, a, f, tot = 0, 1.0, 1.0, 0
    for o in range(oct): s = s + a * vnoise(x*f, z*f, seed + 17*o); tot += a; a *= 0.5; f *= 2
    return s / tot

P = dict(
    cell=230.0,        # one peak site per square this size
    jitter=0.7,
    slope_top=1.1,     # extra slope right at the summit (on top of slope_far): 1.1 + 0.8 = 62 degrees
    slope_far=0.8,     # slope far down the skirt (39 degrees)
    r0=45.0,           # how quickly the summit steepness eases off, blocks
    aretes=(3, 5),     # ridges per peak
    arete_depth=0.38,  # how much slower the ridges fall than the faces
    arete_reach=130.0, # the ridges fade over about this distance from the summit
    warp=30.0, warp_len=120.0,
    gully=0.12, gully_cycles=3.0,
    h_main=178.0, h_var=0.42,
)

def horns(x, z, seed, base, p=P):
    """Height above the base surface. base: array, the land the peaks stand on (blocks)."""
    wx = x + p['warp'] * fbm(x / p['warp_len'], z / p['warp_len'], seed + 1)
    wz = z + p['warp'] * fbm(x / p['warp_len'], z / p['warp_len'], seed + 2)
    cx, cz = np.floor(wx / p['cell']), np.floor(wz / p['cell'])
    best = np.full(x.shape, -1e9)
    for dj in (-2, -1, 0, 1, 2):
        for di in (-2, -1, 0, 1, 2):
            ix, iz = cx + di, cz + dj
            sx = (ix + 0.5 + (h2(ix, iz, seed + 3) - 0.5) * p['jitter']) * p['cell']
            sz = (iz + 0.5 + (h2(ix, iz, seed + 4) - 0.5) * p['jitter']) * p['cell']
            hgt = p['h_main'] * (1 - p['h_var'] * h2(ix, iz, seed + 5) ** 1.5)
            k = np.floor(p['aretes'][0] + h2(ix, iz, seed + 6) * (p['aretes'][1] - p['aretes'][0] + 1))
            phi = h2(ix, iz, seed + 7) * 2 * np.pi
            dx, dz = wx - sx, wz - sz
            r = np.hypot(dx, dz); th = np.arctan2(dz, dx)
            # aretes: the fall is slowest along k directions, with a sharp crease there
            # the ridges are strongest near the summit and fade down the skirt, so they do not run
            # on for hundreds of blocks as dead straight walls
            ad = p['arete_depth'] * (0.35 + 0.65 * np.exp(-(r / p['arete_reach']) ** 2))
            g = 1 - ad + 2 * ad * np.abs(np.sin(k * (th - phi) / 2)) ** 0.8
            fall = (p['slope_top'] * p['r0'] * (1 - np.exp(-r / p['r0'])) + p['slope_far'] * r) * g
            # gullies running straight down the faces: noise that varies round the peak, hardly along the radius
            F = p['gully_cycles']
            gn = 1 - 2 * np.abs(fbm(F * np.cos(th) + ix * 7.1, F * np.sin(th) + iz * 3.7 + r / 260.0, seed + 8, 2))
            fall = fall * (1 + p['gully'] * (gn - 0.6) * np.clip(r / 40.0, 0, 1))
            best = np.maximum(best, hgt - fall)
    return np.maximum(best, 0)

def colour(H, step, snow=150, tree=100, sea=62):
    gx = np.gradient(H, axis=1) / step; gz = np.gradient(H, axis=0) / step
    sl = np.degrees(np.arctan(np.hypot(gx, gz)))
    from scipy.ndimage import uniform_filter
    sls = uniform_filter(sl, 5)
    c = np.zeros(H.shape + (3,)); c[:] = (92, 142, 62)
    c[H < tree] = (58, 104, 48)
    t = np.clip((H - 90) / 110.0, 0, 1)[..., None]
    rockc = (150, 150, 150) * (1 - t) + (88, 88, 96) * t
    rock = (sl > 38) | (H > tree + 35)
    c[rock] = rockc[rock]
    # snow: everywhere gentle enough above the line, and reaching lower in the hollows (the smoothed slope is lower there)
    line = snow + np.clip((sls - 45) * 1.6, -25, 40)
    c[(H > line) & (sls < 60)] = (244, 246, 250)
    c[H < sea] = (52, 92, 200)
    sh = np.clip((-gx * -0.55 - gz * -0.45 + 0.9) / np.sqrt(gx*gx + gz*gz + 1), 0.28, 1.25)
    return np.clip(c * sh[..., None], 0, 255)

def ground_view(H, C, step, cam, yaw, W=1100, Hh=560, fov=62, dist=1700, eye=None):
    """Voxel-space render from a point on the ground looking along yaw (degrees, 0 = +z)."""
    n = H.shape[0]
    img = np.zeros((Hh, W, 3), np.uint8)
    for y in range(Hh): img[y] = (150 + 60 * y / Hh, 185 + 40 * y / Hh, 228)
    ci, cj = int(cam[0] / step + n / 2), int(cam[1] / step + n / 2)
    ez = (H[cj, ci] + 12) if eye is None else eye
    horizon = Hh * 0.72; scale = (W / 2) / np.tan(np.radians(fov / 2))
    ybuf = np.full(W, Hh)
    cols = np.arange(W); ang = np.radians(yaw) + np.arctan((cols - W / 2) / scale)
    dxs, dzs = np.sin(ang), np.cos(ang); cosfix = np.cos(ang - np.radians(yaw))
    d = 4.0
    while d < dist:
        px = cam[0] + dxs * d / cosfix; pz = cam[1] + dzs * d / cosfix
        fi = (px / step + n / 2).astype(int); fj = (pz / step + n / 2).astype(int)
        inside = (fi >= 0) & (fi < n) & (fj >= 0) & (fj < n)
        ii = np.clip(fi, 0, n - 1); jj = np.clip(fj, 0, n - 1)
        h = np.where(inside, H[jj, ii], -1e6); col = C[jj, ii]   # nothing is drawn beyond the edge of the sampled area
        fog = min(1.0, d / dist) ** 1.6 * 0.75
        col = col * (1 - fog) + np.array((178, 200, 228)) * fog
        sy = (horizon - (h - ez) * scale / d).astype(int)
        for x in np.where(sy < ybuf)[0]:
            y0 = max(sy[x], 0)
            if y0 < ybuf[x]: img[y0:ybuf[x], x] = col[x]; ybuf[x] = y0
        d += max(1.0, d * 0.006)
    return img

if __name__ == '__main__':
    out = sys.argv[1]; seed = int(sys.argv[2]) if len(sys.argv) > 2 else 5
    n, step = 1300, 2.0
    jj, ii = np.mgrid[0:n, 0:n]; x = (ii - n / 2) * step; z = (jj - n / 2) * step
    # stand-in for vanilla's land: rolling ground at about y 85-100, with the mountain zone as a broad swell in the middle
    zone = np.clip(1.25 - np.hypot(x / 620.0, z / 430.0), 0, 1); zone = zone * zone * (3 - 2 * zone)
    base = 84 + 9 * fbm(x / 300.0, z / 300.0, seed + 50) + 22 * zone
    H = np.floor(base + zone * horns(x, z, seed, base))
    np.save(out + '.npy', H)
    C = colour(H, step)
    Image.fromarray(C.astype(np.uint8)).resize((600, 600)).save(out + '_top.png')
    for name, cam, yaw in (('south', (0, -760), 0), ('east', (900, 100), 270), ('sw', (-640, -600), 47), ('north', (150, 700), 185)):
        Image.fromarray(ground_view(H, C, step, cam, yaw)).save(f'{out}_{name}.png')
    print('max y', H.max())
