#!/usr/bin/env python3
"""Writes the data pack that reshapes vanilla's own mountains.

    python3 tools/extract_vanilla.py     # once: copies vanilla's worldgen JSON out of the game jar
    python3 tools/build_overhaul.py      # writes fabric/src/main/resources/data/...

How vanilla shapes land, and what this changes
----------------------------------------------
Vanilla decides the height of the ground from three numbers it works out per column from its
climate noises (continents, erosion, ridges):

  offset      how high the ground is (0 is about y 128, each 0.1 is about 13 blocks)
  factor      how tightly the ground follows that height (low = loose, blobby, overhangs)
  jaggedness  how much random spikiness is added on top (only in the peaks)

Its mountains are "offset high, factor low, jaggedness on": a tall blobby mass with random spikes.

This pack keeps all of that (same places, same biomes, same rivers and caves, because nothing but
these three functions changes) and, only where vanilla's ground is high (the mask below):

  1. adds ridge lines to the height: a "ridged" noise, 1 - 2|n|, is highest along the wandering
     lines where the noise n crosses zero, and falls away at an even slope on both sides, so it
     makes crests with straight-sided faces. A second, smaller one adds side ridges.
  2. raises factor, so the ground follows those crests instead of dissolving into blobs.
  3. turns the random spikes down.

The three vanilla functions are replaced by name (minecraft:overworld/offset, /factor,
/jaggedness). Everything else in vanilla that reads them (depth, the terrain density, the
preliminary surface the aquifers and surface rules use) reads them by those names, so it all
stays consistent without copying it.
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, 'tools', 'vanilla-26.3')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- settings -------------------------------------------------------------------------------
# Offsets are in vanilla's units: ground height is about y = 128 + 128 * offset.
MASK_FROM = 0.12      # the change starts where vanilla's ground is about y 143 ...
MASK_TO = 0.42        # ... and is full from about y 182 (vanilla's mountain slopes and peaks)
RIDGE_AMP = 0.30      # main ridges: crest to gully is 2 x this (about 77 blocks)
RIDGE_LIFT = 0.30     # the ridge term is (ridged - this): crests rise less than gullies cut, so mountains do not get taller overall
SIDE_AMP = 0.13       # side ridges, relative to nothing: about 33 blocks crest to gully
RIDGE_SCALE = 0.8     # main ridge noise: base wavelength 256 blocks / this
SIDE_SCALE = 1.0      # side ridge noise: base wavelength 128 blocks / this
FACTOR_BOOST = 1.5    # factor is multiplied by 1 + this in the mask
JAGGED_KEEP = 0.4     # share of vanilla's random spikiness that is kept


def load(rel):
    with open(os.path.join(VANILLA, rel)) as f:
        return json.load(f)


def write(rel, obj):
    path = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=1)
        f.write('\n')


def add(a, b): return {'type': 'minecraft:add', 'left': a, 'right': b}
def mul(a, b): return {'type': 'minecraft:mul', 'left': a, 'right': b}
def cache(f): return {'type': 'minecraft:cache', 'input': f}
def clamp(f, lo, hi): return {'type': 'minecraft:clamp', 'input': f, 'min': lo, 'max': hi}
def noise(name, scale): return {'type': 'minecraft:noise', 'noise': name, 'xz_scale': scale, 'y_scale': 0.0}
def ridged(n): return add(1.0, mul(-2.0, {'type': 'minecraft:abs', 'input': n}))   # 1 on the crest lines, falling evenly either side


def main():
    # two plain noises for the ridge lines (2 octaves each, so the lines wander instead of running straight)
    write(f'{NS}/worldgen/noise/ridge_main.json', {'base_amplitude': 0.9494731054427981, 'base_octave': -8, 'octave_count': 2})
    write(f'{NS}/worldgen/noise/ridge_side.json', {'base_amplitude': 0.9494731054427981, 'base_octave': -7, 'octave_count': 2})

    offset = load('worldgen/density_function/overworld/offset.json')
    factor = load('worldgen/density_function/overworld/factor.json')
    jagged = load('worldgen/density_function/overworld/jaggedness.json')
    # each is cache(lerp(blend_alpha, <value for old chunks>, <the real thing>)); only the real thing changes
    for d in (offset, factor, jagged):
        assert d['type'] == 'minecraft:cache' and d['input']['type'] == 'minecraft:lerp', 'vanilla layout changed'

    # vanilla's own height, kept under our name so the new offset can read it
    write(f'{NS}/worldgen/density_function/overworld/vanilla_offset.json', cache(offset['input']['second']))
    VOFF = f'{NS}:overworld/vanilla_offset'
    # where the change applies: 0 below MASK_FROM, 1 above MASK_TO, by vanilla's own ground height
    write(f'{NS}/worldgen/density_function/overworld/mountain_mask.json',
          cache(clamp(mul(add(VOFF, -MASK_FROM), 1.0 / (MASK_TO - MASK_FROM)), 0.0, 1.0)))
    MASK = f'{NS}:overworld/mountain_mask'
    # the ridge lines, in offset units
    ridges = add(mul(RIDGE_AMP, add(ridged(noise(f'{NS}:ridge_main', RIDGE_SCALE)), -RIDGE_LIFT)),
                 mul(SIDE_AMP, ridged(noise(f'{NS}:ridge_side', SIDE_SCALE))))
    write(f'{NS}/worldgen/density_function/overworld/ridge_lines.json', cache(ridges))

    offset['input']['second'] = add(VOFF, mul(MASK, f'{NS}:overworld/ridge_lines'))
    factor['input']['second'] = mul(factor['input']['second'], add(1.0, mul(FACTOR_BOOST, MASK)))
    jagged['input']['second'] = mul(jagged['input']['second'], JAGGED_KEEP)
    write('minecraft/worldgen/density_function/overworld/offset.json', offset)
    write('minecraft/worldgen/density_function/overworld/factor.json', factor)
    write('minecraft/worldgen/density_function/overworld/jaggedness.json', jagged)
    print('wrote the overhaul data pack under', OUT)


if __name__ == '__main__':
    main()
