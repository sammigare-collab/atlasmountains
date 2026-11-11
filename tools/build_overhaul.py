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

What this pack does
-------------------
Small changes to those three numbers barely show (two attempts on 11 Nov 2026 moved the ground
about 4 blocks on a 250-block range). The look Sammi wants, separate pyramid peaks with ridges
running down from each summit, needs a shape of its own. So, in vanilla's mountain country:

  1. Where: "amp" is 1 deep in low-erosion country, far enough inland, and fades to 0 as
     erosion rises (erosion is vanilla's widest map, features a couple of kilometres across, so
     amp changes slowly and whole clusters of peaks fit inside it). A first version took amp
     from vanilla's target height instead; that is only high along narrow crest bands, and
     multiplying the peaks by it cut them into fragments.
  2. The land: vanilla's own, with whatever stands above BASE pressed down to a fraction of its
     height, so there is rolling ground at about y 110 for the peaks to stand on. Valleys and
     rivers below BASE are left exactly as they are.
  3. The peaks: the mod's atlasmountains:horns density function (see HornsFunction.java) is
     added on top, scaled by amp, and held back near rivers (weirdness near 0) so a river valley
     still runs between mountains.
  4. Factor is raised there so the ground follows the peaks closely (a little 3-D roughness is
     left), and vanilla's random spikes are turned off there.
  5. Vanilla cuts terrain off at y 256; that ceiling is moved up so summits fit.

Only vanilla's offset, factor, jaggedness, final_density and preliminary_surface_level are
replaced, by name, so everything that reads them stays consistent. Biomes are not touched: they
are chosen from the same climate maps, so they stay where they were.
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, 'tools', 'vanilla-26.3')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- settings -------------------------------------------------------------------------------
# Offsets are in vanilla's units: ground height is about y = 128 + 128 * offset.
EROSION_FROM = -0.40  # peaks start where erosion is below this ...
EROSION_FULL = -0.65  # ... and are full size where it is below this
INLAND_FROM = -0.05   # and where continentalness is above this (just in from the coast) ...
INLAND_FULL = 0.20    # ... full size above this
RIVER_FROM = 0.05     # peaks are held back where |weirdness| is below this (vanilla's river band) ...
RIVER_FULL = 0.27     # ... and are full size above this
BASE = -0.14          # land above this (y 110) is pressed down among the peaks ...
BASE_KEEP = 0.15      # ... to this share of its height above it
FACTOR_IN = 16.0      # factor among the peaks (vanilla's mountains: about 5)
TOP_FROM, TOP_TO = 296, 312   # the terrain ceiling (vanilla: 240 to 256)
# the peaks themselves: see HornsFunction.java and tools/prototype_horns.py (same names)
HORNS = {
    'cell': 230.0,          # one peak per square this size
    'jitter': 0.7,
    'slope_top': 1.1,       # extra slope at the summit, on top of slope_far: 62 degrees together
    'slope_far': 0.8,       # slope far down the skirt: 39 degrees
    'r0': 45.0,             # how quickly the summit steepness eases off, blocks
    'aretes': [3, 5],       # ridges per peak
    'arete_depth': 0.38,    # how much slower the ridges fall than the faces
    'arete_reach': 130.0,   # the ridges fade over about this distance from the summit
    'warp': 30.0, 'warp_length': 120.0,
    'gully': 0.12, 'gully_cycles': 3.0,
    'height': 172.0,        # the tallest peaks, above the land they stand on: summits at about y 285 to 300
    'height_var': 0.42,     # other peaks are up to this much lower
}


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


def move_ceiling(node):
    """Vanilla's 'air above the top' gradient (y 240 to 256), moved up in place."""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:gradient' and node.get('from_coordinate') == 240 and node.get('to_coordinate') == 256:
            node['from_coordinate'], node['to_coordinate'] = TOP_FROM, TOP_TO
            return 1
        return sum(move_ceiling(v) for v in node.values())
    if isinstance(node, list):
        return sum(move_ceiling(v) for v in node)
    return 0


def main():
    for sub in ('minecraft', NS):               # start clean, so a file dropped from the pack does not linger
        shutil.rmtree(os.path.join(OUT, sub), ignore_errors=True)

    offset = load('worldgen/density_function/overworld/offset.json')
    factor = load('worldgen/density_function/overworld/factor.json')
    jagged = load('worldgen/density_function/overworld/jaggedness.json')
    # each of the three is cache(lerp(blend_alpha, <value for old chunks>, <the real thing>)); only the real thing changes
    for d in (offset, factor, jagged):
        assert d['type'] == 'minecraft:cache' and d['input']['type'] == 'minecraft:lerp', 'vanilla layout changed'

    EROSION, CONT, W = 'minecraft:overworld/erosion', 'minecraft:overworld/continents', 'minecraft:overworld/ridges'
    DF = f'{NS}/worldgen/density_function/overworld'
    def ramp(f, lo, hi):          # 0 at lo, 1 at hi, clamped
        return clamp(mul(add(f, -lo), 1.0 / (hi - lo)), 0.0, 1.0)
    def smooth(name, linear):     # a smooth step of a 0..1 ramp, written as its own cached file
        write(f'{DF}/{name}_linear.json', cache(linear))
        t = f'{NS}:overworld/{name}_linear'
        write(f'{DF}/{name}.json', cache(mul(mul(t, t), add(3.0, mul(-2.0, t)))))
        return f'{NS}:overworld/{name}'
    # 1. where
    AMP = smooth('amp', mul(ramp(EROSION, EROSION_FROM, EROSION_FULL), ramp(CONT, INLAND_FROM, INLAND_FULL)))
    RIVER = smooth('river_guard', ramp({'type': 'minecraft:abs', 'input': W}, RIVER_FROM, RIVER_FULL))
    # vanilla's own target height, under our name so the new offset can read it
    write(f'{DF}/vanilla_offset.json', cache(offset['input']['second']))
    VOFF = f'{NS}:overworld/vanilla_offset'
    # 2. the land: what stands above BASE is pressed down to BASE_KEEP of its height, by amp
    above = {'type': 'minecraft:max', 'left': add(VOFF, -BASE), 'right': 0.0}
    land = add(VOFF, mul(mul(AMP, -(1.0 - BASE_KEEP)), above))
    # 3. the peaks
    write(f'{DF}/horns.json', cache(dict({'type': f'{NS}:horns'}, **HORNS)))
    offset['input']['second'] = add(land, mul(mul(AMP, RIVER), f'{NS}:overworld/horns'))
    # 4. factor rises to FACTOR_IN among the peaks; vanilla's spikes fade out there
    vfactor = factor['input']['second']
    factor['input']['second'] = add(vfactor, mul(AMP, add(FACTOR_IN, mul(-1.0, vfactor))))
    jagged['input']['second'] = mul(jagged['input']['second'], add(1.0, mul(-1.0, AMP)))
    write('minecraft/worldgen/density_function/overworld/offset.json', offset)
    write('minecraft/worldgen/density_function/overworld/factor.json', factor)
    write('minecraft/worldgen/density_function/overworld/jaggedness.json', jagged)
    # 5. the ceiling, in the terrain itself and in the preliminary surface the aquifers and surface rules use
    for name in ('final_density', 'preliminary_surface_level'):
        d = load(f'worldgen/density_function/overworld/{name}.json')
        assert move_ceiling(d) == 1, f'vanilla {name} changed'
        write(f'minecraft/worldgen/density_function/overworld/{name}.json', d)
    print('wrote the overhaul data pack under', OUT)


if __name__ == '__main__':
    main()
