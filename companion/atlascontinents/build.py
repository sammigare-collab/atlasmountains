#!/usr/bin/env python3
"""
Atlas Continents: a companion to Atlas Mountains, kept as a SEPARATE mod file.

What Sammi asked for (15 Nov 2026): "this continents mod shouldnt effect the mountains at all, just make land masses
30% of world and biomes half way between vanilla and larger biomes similar to the minecraft mod by star labs
continents". Written from scratch; nothing is copied from that mod.

What it changes, and nothing else:
  minecraft:overworld/continents    land in separate landmasses over LAND_SHARE of the world
  minecraft:overworld/temperature   } the two climate maps that decide which biome a place gets, BIOME_SCALE times
  minecraft:overworld/vegetation    } vanilla's size (vanilla 1, Large Biomes 4)
It replaces none of the files Atlas Mountains replaces, and it leaves erosion and ridges alone: erosion is what decides
where mountain country is, so ranges are the size they are without this mod. Mountains still only stand on land.

Run:  python3 build.py            writes build/atlascontinents-<version>.jar
The jar is a Fabric mod with no code (it needs Fabric API to be read as a mod). The same file renamed to .zip works as
a plain data pack.
"""
import json, os, shutil, sys, zipfile

VERSION = '0.5.0'
NS = 'atlascontinents'

LAND_SHARE = 0.30          # of the world, measured where continentalness is above the coast line (-0.19)
BIOME_SCALE = 2.5          # halfway between vanilla (1) and Large Biomes (4)

# The landmasses come from one slow noise of this mod's own. Its first wave is 16,384 / CONTINENT_XZ blocks long; a
# landmass (the part of a wave above the 70th percentile) comes out at roughly 0.4 of that across.
CONTINENT_OCTAVE = -14
CONTINENT_XZ = 1.3          # 12,600 blocks to a wave: landmasses about 10,000 blocks across (at 0.8 they measured 16,000)
CONTINENT_AMPLITUDES = [1.0, 0.55, 0.3, 0.18, 0.1]

# Vanilla's own continent noise, at vanilla's size, laid on top at this strength: bays, headlands and offshore islands.
COAST_DETAIL = 0.11

# So that a new world does not begin at sea: the field is lifted round the origin, by ORIGIN_LIFT at 0 0 falling to
# nothing ORIGIN_REACH blocks away along each axis. It is added before the field is turned into land and sea, so what
# it makes is an ordinary landmass with the noise's own outline, not an island of its own shape.
ORIGIN_LIFT = 0.6          # the noise is under -0.5 (so 0 0 could still be sea) in about one world in 300
ORIGIN_REACH = 4000

# Where vanilla's coast value (-0.19) sits, as a share of the world below it. Not simply 1 - LAND_SHARE: between -0.19
# and about -0.16 vanilla's land is still under water four times in five (measured), so the shore a player sees is
# at about -0.16, and that is what is put at 1 - LAND_SHARE. Rivers and lakes inland are counted as land.
COAST_AT = 1 - LAND_SHARE - 0.015

# The slow noise is turned into vanilla's "continentalness" through this table: (share of the world below, value).
# Vanilla reads the value as: under -1.05 mushroom fields, to -0.455 deep ocean, to -0.19 ocean, to -0.11 coast,
# to 0.03 near inland, to 0.3 mid inland, above that far inland. The coast line sits at 1 - LAND_SHARE.
SHAPE = [
    (0.000, -1.20),
    (0.015, -0.80),
    (0.200, -0.50),
    (0.600, -0.30),
    (COAST_AT, -0.19),
    (COAST_AT + 0.040, -0.11),
    (COAST_AT + 0.105, 0.03),
    (0.900, 0.30),
    (1.000, 0.65),
]
# The slow noise's value at each of those shares of the world, measured on the dev server over 393,216 blocks square
# (tools: /atlasmountains debug dfmap "atlascontinents:base"). None until measured: see CALIBRATE below.
QUANTILES = [-0.8791, -0.4492, -0.1799, 0.0496, 0.0982, 0.1227, 0.1656, 0.2629, 0.9209]

CALIBRATE = '--calibrate' in sys.argv      # build with a plain pass-through so the noise itself can be measured


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def add(a, b): return {'type': 'minecraft:add', 'left': a, 'right': b}
def mul(a, b): return {'type': 'minecraft:mul', 'left': a, 'right': b}
def cache(a): return {'type': 'minecraft:cache', 'input': a}


def spline(coordinate, points):
    """A smooth curve through the points: the slope at each is the mean of the slopes either side (0 at the ends)."""
    out = []
    for i, (x, y) in enumerate(points):
        if i == 0 or i == len(points) - 1:
            d = 0.0
        else:
            (x0, y0), (x1, y1) = points[i - 1], points[i + 1]
            left, right = (y - y0) / (x - x0), (y1 - y) / (x1 - x)
            d = 0.0 if left * right <= 0 else 2 * left * right / (left + right)      # harmonic mean: never overshoots
        out.append({'location': x, 'value': y, 'derivative': d})
    return {'type': 'minecraft:spline', 'spline': {'coordinate': coordinate, 'points': out}}


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, 'build', 'pack')
    shutil.rmtree(os.path.join(here, 'build'), ignore_errors=True)
    data = os.path.join(out, 'data')
    df = os.path.join(data, NS, 'worldgen', 'density_function')
    mc = os.path.join(data, 'minecraft', 'worldgen', 'density_function', 'overworld')

    write(os.path.join(data, NS, 'worldgen', 'noise', 'continents.json'),
          {'amplitude_modifiers': CONTINENT_AMPLITUDES, 'base_amplitude': 0.9494731054427981,
           'base_octave': CONTINENT_OCTAVE, 'octave_count': len(CONTINENT_AMPLITUDES)})
    write(os.path.join(df, 'base.json'),
          cache({'type': 'minecraft:noise', 'noise': f'{NS}:continents', 'xz_scale': CONTINENT_XZ, 'y_scale': 0.0}))

    def away(axis):     # 1 at the origin, 0 from ORIGIN_REACH blocks away along this axis
        g = {'type': 'minecraft:gradient', 'axis': axis, 'from_coordinate': -ORIGIN_REACH, 'from_value': -1.0,
             'to_coordinate': ORIGIN_REACH, 'to_value': 1.0}
        return add(1.0, mul(-1.0, {'type': 'minecraft:abs', 'input': g}))
    write(os.path.join(df, 'origin.json'), cache(mul(ORIGIN_LIFT, mul(away('x'), away('z')))))
    write(os.path.join(df, 'field.json'), cache(add(f'{NS}:base', f'{NS}:origin')))

    vanilla_detail = {'type': 'minecraft:noise', 'noise': 'minecraft:continentalness', 'shift_x': 'minecraft:shift_x',
                      'shift_z': 'minecraft:shift_z', 'xz_scale': 0.25, 'y_scale': 0.0}
    if CALIBRATE or QUANTILES is None:
        shaped = f'{NS}:field'
    else:
        shaped = spline(f'{NS}:field', [(q, v) for q, (_, v) in zip(QUANTILES, SHAPE)])
    write(os.path.join(mc, 'continents.json'), cache(add(shaped, mul(COAST_DETAIL, vanilla_detail))))

    for name in ('temperature', 'vegetation'):
        write(os.path.join(mc, f'{name}.json'),
              {'type': 'minecraft:noise', 'noise': f'minecraft:{name}', 'shift_x': 'minecraft:shift_x',
               'shift_z': 'minecraft:shift_z', 'xz_scale': 0.25 / BIOME_SCALE, 'y_scale': 0.0})

    write(os.path.join(out, 'pack.mcmeta'), {'pack': {
        'description': 'Atlas Continents: land in separate landmasses over 30% of the world, biomes 2.5x vanilla',
        'pack_format': 121, 'min_format': 121, 'max_format': 121}})
    write(os.path.join(out, 'fabric.mod.json'), {
        'schemaVersion': 1, 'id': NS, 'version': VERSION, 'name': 'Atlas Continents',
        'description': 'Land in separate landmasses over about 30% of the world, and biomes halfway between vanilla '
                       'and Large Biomes. Changes nothing else. A companion to Atlas Mountains; works without it.',
        'authors': ['Sammi'], 'license': 'MIT', 'environment': '*',
        'depends': {'fabricloader': '>=0.20.0', 'fabric-api': '*', 'minecraft': '~26.3'}})

    jar = os.path.join(here, 'build', f'{NS}-{VERSION}.jar')
    with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as z:
        for root, _, files in os.walk(out):
            for f in sorted(files):
                p = os.path.join(root, f)
                z.write(p, os.path.relpath(p, out))
    print('wrote', jar, os.path.getsize(jar), 'bytes', '(calibration build)' if CALIBRATE or QUANTILES is None else '')


if __name__ == '__main__':
    main()
