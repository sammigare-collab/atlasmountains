#!/usr/bin/env python3
"""Writes the data pack that reshapes vanilla's own mountains.

    python3 tools/extract_vanilla.py     # once: copies vanilla's worldgen JSON out of the game jar
    python3 tools/build_overhaul.py      # writes fabric/src/main/resources/data/...

How vanilla builds a mountain (checked against the 26.3 data, Nov 2026)
----------------------------------------------------------------------
Vanilla works out a target ground height per column, "offset", from three climate maps:
continents (how far inland), erosion (low = mountainous) and peaks-and-valleys, PV, which is the
weirdness map folded: PV = 1 - |3|w| - 2|, so PV is -1 where w = 0 (rivers), +1 on the lines
where |w| = 2/3 (the crests) and falls back to 0 towards |w| = 1. Ground height is about
y = 128 + 128 * offset. Far inland the crest line stands at y 230 for erosion -0.9, y 176 for
-0.6, y 163 for -0.4 and only y 123 for -0.2; the valley floor is about y 90.

Two more numbers finish the shape: "factor" (how tightly the ground follows that target; the
3-D noise smears it less when factor is high) and "jaggedness" (how much of a small-scale noise
is added as spikes; it is only above zero for erosion below about -0.45, 0.6 where weirdness is
negative and 0.3 where it is positive).

So a vanilla mountain is: a crest along a closed contour of weirdness (hence rings), dropping to
a bowl inside the ring, smeared by 3-D noise, with small random spikes on top.

What this pack changes, and only where erosion is low (the mask below)
--------------------------------------------------------------------
  1. Solid massifs. Inside the ring (|w| > 2/3) the terrain's PV stays near 1 instead of
     falling back, so the ring is filled in and stands as one mountain.
  2. Ridge lines. Across the upper slopes and the massif a ridged noise, 1 - 2|n|, cuts gullies
     between crests (it mostly lowers: the crests are the massif's own height).
  3. Cleaner faces. Factor is raised, so the ground keeps the edges of 1 and 2.
  4. Fewer, bigger peaks. The spike noise is stretched (features about three times wider) and
     its strength lowered, so it makes separate summits instead of a bed of needles.
  5. A soft ceiling. Vanilla cuts terrain off flat at y 256; the target height is compressed
     above OFFSET_KNEE so summits finish as points below that.

Only vanilla's offset, factor, jaggedness and sloped_cheese are replaced, by name, so
everything else that reads them (depth, the preliminary surface, aquifers, surface rules) stays
consistent. Biomes are not touched: they are chosen from the same climate maps, so they stay
where they were. Rivers are not touched either (PV below the crest line is unchanged).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, 'tools', 'vanilla-26.3')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- settings -------------------------------------------------------------------------------
# Offsets are in vanilla's units: ground height is about y = 128 + 128 * offset.
EROSION_FULL = -0.45  # the changes are full where erosion is below this (vanilla's two mountain tiers) ...
EROSION_NONE = -0.30  # ... and absent above this
FILL_DROP = 0.25      # inside the ring PV runs from 1 at the crest line down to 1 - this at |w| = 1 (vanilla: down to 0)
RIDGE_AMP = 0.22      # ridge lines: crest to gully is 2 x this (about 56 blocks)
RIDGE_LIFT = 0.75     # the ridge term is (ridged - this): crests rise 7 blocks, gullies cut 49
RIDGE_FROM = 0.4      # ridge lines start where the terrain PV is above this ...
RIDGE_TO = 1.0        # ... and are full at the crest line, so a gully never dips below the slope under it
RIDGE_SCALE = 1.0     # ridge noise: base wavelength 256 blocks / this
FACTOR_BOOST = 0.6    # factor is multiplied by 1 + this in the mask
JAGGED_KEEP = 0.5     # share of vanilla's spike strength that is kept
JAGGED_SCALE = 500.0  # vanilla's spike noise runs at 1500: lower = wider, fewer peaks
OFFSET_KNEE = 0.72    # target heights above this (about y 220) ...
OFFSET_SQUASH = 0.5   # ... are compressed by this, so summits stay under vanilla's y 256 cut-off


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


def rename(obj, old, new):
    """Every exact string old in obj becomes new (for repointing spline coordinates)."""
    if isinstance(obj, dict): return {k: rename(v, old, new) for k, v in obj.items()}
    if isinstance(obj, list): return [rename(v, old, new) for v in obj]
    return new if obj == old else obj


def main():
    # a plain noise for the ridge lines (2 octaves, so the lines wander instead of running straight)
    write(f'{NS}/worldgen/noise/ridge_main.json', {'base_amplitude': 0.9494731054427981, 'base_octave': -8, 'octave_count': 2})

    offset = load('worldgen/density_function/overworld/offset.json')
    factor = load('worldgen/density_function/overworld/factor.json')
    jagged = load('worldgen/density_function/overworld/jaggedness.json')
    cheese = load('worldgen/density_function/overworld/sloped_cheese.json')
    # each of the three is cache(lerp(blend_alpha, <value for old chunks>, <the real thing>)); only the real thing changes
    for d in (offset, factor, jagged):
        assert d['type'] == 'minecraft:cache' and d['input']['type'] == 'minecraft:lerp', 'vanilla layout changed'

    EROSION, W, PV = 'minecraft:overworld/erosion', 'minecraft:overworld/ridges', 'minecraft:overworld/ridges_folded'
    # the mask: 1 where erosion is below EROSION_FULL, 0 above EROSION_NONE
    write(f'{NS}/worldgen/density_function/overworld/mountain_mask.json',
          cache(clamp(mul(add(EROSION, -EROSION_NONE), 1.0 / (EROSION_FULL - EROSION_NONE)), 0.0, 1.0)))
    MASK = f'{NS}:overworld/mountain_mask'

    # 1. the terrain's own PV: vanilla's, except inside the ring (|w| > 2/3), where it only eases
    #    down from 1 to 1 - FILL_DROP instead of falling to 0; blended in by the mask
    absw = {'type': 'minecraft:abs', 'input': W}
    filled = {'type': 'minecraft:range_choice', 'input': absw, 'min_inclusive': 2.0 / 3.0, 'max_exclusive': 4.0,
              'when_in_range': add(1.0, mul(-3.0 * FILL_DROP, add(absw, -2.0 / 3.0))), 'when_out_of_range': PV}
    write(f'{NS}/worldgen/density_function/overworld/terrain_pv.json',
          cache(add(PV, mul(MASK, add(filled, mul(-1.0, PV))))))
    TPV = f'{NS}:overworld/terrain_pv'

    # 2. the ridge lines, in offset units, where the terrain PV is high and the mask is on
    high = clamp(mul(add(TPV, -RIDGE_FROM), 1.0 / (RIDGE_TO - RIDGE_FROM)), 0.0, 1.0)
    write(f'{NS}/worldgen/density_function/overworld/ridge_lines.json',
          cache(mul(mul(MASK, high), mul(RIDGE_AMP, add(ridged(noise(f'{NS}:ridge_main', RIDGE_SCALE)), -RIDGE_LIFT)))))

    # vanilla's height curve, reading the terrain PV, plus the ridge lines
    write(f'{NS}/worldgen/density_function/overworld/raw_offset.json',
          cache(add(rename(offset['input']['second'], PV, TPV), f'{NS}:overworld/ridge_lines')))
    RAW = f'{NS}:overworld/raw_offset'
    # 5. the soft ceiling: unchanged below the knee, compressed above it
    over = {'type': 'minecraft:max', 'left': add(RAW, -OFFSET_KNEE), 'right': 0.0}
    offset['input']['second'] = add(RAW, mul(-(1.0 - OFFSET_SQUASH), over))
    # 3. tighter faces; the factor spline reads the terrain PV too, so the filled ring is treated as mountain
    factor['input']['second'] = mul(rename(factor['input']['second'], PV, TPV), add(1.0, mul(FACTOR_BOOST, MASK)))
    # 4. the spikes: lower strength here, wider features in sloped_cheese
    jagged['input']['second'] = mul(rename(jagged['input']['second'], PV, TPV), JAGGED_KEEP)
    text = json.dumps(cheese)
    assert '"xz_scale": 1500.0' in text, 'vanilla sloped_cheese changed'
    cheese = json.loads(text.replace('"xz_scale": 1500.0', f'"xz_scale": {JAGGED_SCALE}'))

    write('minecraft/worldgen/density_function/overworld/offset.json', offset)
    write('minecraft/worldgen/density_function/overworld/factor.json', factor)
    write('minecraft/worldgen/density_function/overworld/jaggedness.json', jagged)
    write('minecraft/worldgen/density_function/overworld/sloped_cheese.json', cheese)
    print('wrote the overhaul data pack under', OUT)


if __name__ == '__main__':
    main()
