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

  1. Where: "strength" is 1 deep in low-erosion country, far enough inland and away from
     rivers, and fades to 0 outside it. (A first version used vanilla's target height instead;
     that is only high along narrow crest bands and cut the peaks into fragments.)
  2. The land: vanilla's own, at vanilla's own height. Nothing is lowered or flattened to make
     room (a first version pressed the land down to y 110 so the summits would fit under the
     world's ceiling; Sammi asked for the ceiling to be raised instead, 12 Nov 2026).
  3. The peaks: the mod's atlasmountains:horns density function (see HornsFunction.java) is
     added on top. Each peak reads the strength once, at its own summit, and is that fraction
     of its full height with its angles unchanged; a weak one is left out. Only along the river
     channel itself is the ground pressed back down, so a river still runs between mountains.
  4. Vanilla's random spikes are turned off there.
  5. The world is made taller so the summits fit: the overworld's build height goes from
     y 320 to y 512, and the level where vanilla cuts terrain off from y 256 to about y 480.
     (Vanilla's tallest land is about y 250 and the tallest peak adds 172.)

  6. Biomes: the upper part of each peak is given vanilla's own peak biome for its climate
     (jagged, frozen or stony peaks), by changing the weirdness the biome picker sees there.
     Everything else keeps the biome vanilla chose.
  7. Clean faces: where a peak stands clear of the land, the ground is cut exactly at the
     peak's own height, with no 3-D roughness and no cave mouths in the face.

Vanilla's offset, jaggedness, depth, sloped_cheese, final_density and preliminary_surface_level are
replaced by name, so everything that reads them stays consistent; the overworld noise settings
are vanilla's with the height and one router entry (ridges, used for biomes only) changed, and
the overworld dimension type is vanilla's with the height changed.
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VANILLA = os.path.join(ROOT, 'tools', 'vanilla-26.3')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- settings -------------------------------------------------------------------------------
# Where peaks generate and how far apart they are is NOT to be changed without Sammi asking
# ("don't change their spawning", 12 Nov 2026): the erosion, inland and river settings and 'cell'.
# Offsets are in vanilla's units: ground height is about y = 128 + 128 * offset.
EROSION_FROM = -0.40  # mountain country starts where erosion is below this ...
EROSION_FULL = -0.65  # ... and is at full strength below this
INLAND_FROM = -0.05   # and where continentalness is above this (just in from the coast) ...
INLAND_FULL = 0.20    # ... full strength above this
RIVER_FROM = 0.05     # a peak is weaker if its SUMMIT is where |weirdness| is below RIVER_FULL (near a river) ...
RIVER_FULL = 0.27     # ... and absent where it is below RIVER_FROM
CHANNEL_FROM = 0.04   # whatever stands there, the ground is pressed back to the land's own level along the
CHANNEL_FULL = 0.12   # river channel itself (|weirdness| below CHANNEL_FROM), easing off by CHANNEL_FULL
PEAK_BIOME_FROM = 0.27 # vanilla's peak biomes take over where a peak stands this far above the land (35 blocks) ...
PEAK_BIOME_FULL = 0.43 # ... fully by here (55 blocks)
PEAK_BIOME_W = 0.70   # the weirdness the biomes are shown there (a crest line is 2/3)
CLEAN_FROM = 0.03     # where a peak stands this far above the land (4 blocks) its faces start to be cut clean ...
CLEAN_FULL = 0.16     # ... and by here (20 blocks) the ground is exactly the peak's own shape: no 3-D roughness
SHELL_BLOCKS = 12     # and solid for this many blocks down, so caves cannot open holes in a face
WORLD_MIN_Y, WORLD_HEIGHT = -64, 576   # the overworld: y -64 to 511 (vanilla: height 384, top y 319)
TOP_FROM, TOP_TO = 472, 488   # the terrain ceiling (vanilla: 240 to 256)
# the peaks themselves: see HornsFunction.java and tools/prototype_horns.py (same names)
HORNS = {
    'cell': 230.0,          # one peak per square this size
    'jitter': 0.7,
    'slope_top': 0.4,       # extra slope at the summit, on top of slope_far
    'slope_far': 0.7,       # slope far down the skirt (Sammi picked these two from A/B/C, 12 Nov 2026)
    'r0': 50.0,             # how quickly the summit steepness eases off, blocks
    'aretes': [3, 4],       # sides per peak: a pyramid. Five and more read as a cone or a star, not a pyramid
    'arete_depth': 0.38,    # how much slower the ridges (the pyramid's edges) fall than the nominal slope
    'arete_reach': 130.0,   # that eases off over about this distance from the summit
    'warp': 8.0, 'warp_length': 120.0,     # how far the edges wander from straight
    'gully': 0.03,          # soft, irregular gullies on the faces (0.12 drew many lines down each face: "too many sides")
    'height': 172.0,        # the tallest peaks, above the land they stand on
    'height_var': 0.42,     # other peaks are up to this much lower
    'min_height': 60.0,     # a peak that would be shorter than this (weak mountain country) is left out
    'facet': 1.0,           # how flat the faces are: 1 = a true pyramid, 0 = scooped hollows between the edges
    'fold': 0.0,            # sharp V gullies between ridges: tried, made stars; left at 0
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


def extend_depth(node):
    """Vanilla's height-to-depth gradient stops at y 320 (it is clamped beyond its ends, so land
    could never rise above that); carried on at the same slope to the new top of the world."""
    top = WORLD_MIN_Y + WORLD_HEIGHT
    if isinstance(node, dict):
        if (node.get('type') == 'minecraft:gradient' and node.get('from_coordinate') == -64 and node.get('to_coordinate') == 320
                and node.get('from_value') == 1.5 and node.get('to_value') == -1.5):
            node['to_coordinate'], node['to_value'] = top, 1.5 - 3.0 * (top + 64) / 384.0
            return 1
        return sum(extend_depth(v) for v in node.values())
    if isinstance(node, list):
        return sum(extend_depth(v) for v in node)
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
    # 2. the land is vanilla's, untouched
    land = VOFF
    # 3. the peaks
    #    Each peak reads "strength" once, at its own summit, and is that fraction of its full
    #    height with its slopes unchanged (HornsFunction, PeakStrength). Multiplying every column
    #    by the local value instead, as up to 0.4.2, squashed most peaks into rounded humps.
    write(f'{DF}/strength.json', cache(mul(AMP, RIVER)))
    CHANNEL = smooth('channel', ramp({'type': 'minecraft:abs', 'input': W}, CHANNEL_FROM, CHANNEL_FULL))
    write(f'{DF}/horns.json', cache(dict({'type': f'{NS}:horns'}, **HORNS)))
    write(f'{DF}/peak_height.json', cache(mul(CHANNEL, f'{NS}:overworld/horns')))
    PEAK = f'{NS}:overworld/peak_height'      # how far the peaks stand above the land here, in offset units
    offset['input']['second'] = add(land, PEAK)
    # 4. vanilla's random spikes fade out among the peaks. Factor is left as vanilla has it: an
    #    earlier version raised it to 16 to hold the shape against the 3-D noise, but that also
    #    let caves come within 3 blocks of the surface (factor is how fast "depth below ground"
    #    grows, and vanilla keeps caves away from the surface by that depth), which is where the
    #    cave mouths in the faces came from. The clean cut (7) holds the shape now.
    jagged['input']['second'] = mul(jagged['input']['second'], add(1.0, mul(-1.0, AMP)))
    write('minecraft/worldgen/density_function/overworld/offset.json', offset)
    write('minecraft/worldgen/density_function/overworld/jaggedness.json', jagged)
    # 6. biomes on the peaks. Vanilla picks biomes from the climate maps, and its peak biomes
    #    (jagged, frozen, stony peaks, by temperature) need weirdness on a crest line (|w| near
    #    2/3). The peaks stand wherever erosion is low, whatever the weirdness, so without help a
    #    peak in forest country is a forest-covered pyramid. The weirdness the BIOMES see (the
    #    router's "ridges"; the terrain reads the map by name and is not affected) is therefore
    #    pushed to a crest value on the upper part of each peak: below PEAK_BIOME_FROM above the
    #    land the biome is whatever vanilla had (forest, meadow...), above PEAK_BIOME_FULL it is
    #    vanilla's peak biome for that climate, and vanilla's slope biomes come in between.
    K = smooth('peak_biome', ramp(PEAK, PEAK_BIOME_FROM, PEAK_BIOME_FULL))
    sign = clamp(mul(W, 10000.0), -1.0, 1.0)
    write(f'{DF}/biome_ridges.json', cache(add(W, mul(K, add(mul(sign, PEAK_BIOME_W), mul(-1.0, W))))))
    ns = load('worldgen/noise_settings/overworld.json')
    assert ns['noise_router']['ridges'] == 'minecraft:overworld/ridges', 'vanilla noise router changed'
    ns['noise_router']['ridges'] = f'{NS}:overworld/biome_ridges'

    ns['noise']['height'] = WORLD_HEIGHT
    write('minecraft/worldgen/noise_settings/overworld.json', ns)

    # 5. a taller world: the dimension itself, the depth gradient (in its own file and inlined in
    #    the preliminary surface), and the terrain ceiling (in the terrain and the preliminary surface)
    dim = load('dimension_type/overworld.json')
    assert dim['min_y'] == WORLD_MIN_Y and dim['height'] == 384, 'vanilla dimension type changed'
    dim['height'] = dim['logical_height'] = WORLD_HEIGHT
    write('minecraft/dimension_type/overworld.json', dim)
    depth = load('worldgen/density_function/overworld/depth.json')
    assert extend_depth(depth) == 1, 'vanilla depth changed'
    write('minecraft/worldgen/density_function/overworld/depth.json', depth)
    fd = load('worldgen/density_function/overworld/final_density.json')
    assert move_ceiling(fd) == 1 and extend_depth(fd) == 0, 'vanilla final_density changed'
    # 7. clean faces. Vanilla smears every surface with 3-D noise and lets caves break through it;
    #    on faces this steep that tears off shards and punches holes (Sammi's screenshots, 12 Nov
    #    2026). Where a peak stands clear of the land ("clean" = 1) the ground is instead cut
    #    exactly at the peak's own height, column by column, as the old mod's mountains were:
    #    air above it, solid for SHELL_BLOCKS below it, vanilla's own business (caves, ores)
    #    below that. This sits outside vanilla's 4x8x4 interpolation, so it is exact per block.
    #    depth is positive below the target height, one block = 1/128.
    CLEAN = smooth('clean', ramp(PEAK, CLEAN_FROM, CLEAN_FULL))
    DEPTH = 'minecraft:overworld/depth'
    below = clamp(mul(DEPTH, 16.0), -1.0, 1.0)                                           # > 0 below the peak's surface
    in_shell = clamp(mul(add(SHELL_BLOCKS / 128.0, mul(-1.0, DEPTH)), 16.0), -1.0, 1.0)   # > 0 above the shell's bottom
    solid = add(mul(CLEAN, add({'type': 'minecraft:min', 'left': below, 'right': in_shell}, 1.0)), -1.0)
    air = add(1.0, mul(CLEAN, add(below, -1.0)))
    assert fd['type'] == 'minecraft:add' and fd['left']['type'] == 'minecraft:min' and fd['right'] == {'type': 'minecraft:beardifier'}, 'vanilla final_density changed'
    fd['left'] = {'type': 'minecraft:min', 'left': {'type': 'minecraft:max', 'left': fd['left'], 'right': solid}, 'right': air}
    #    Vanilla's own 3-D noise is also faded out there, so its idea of the ground agrees with the
    #    clean cut and no hollow is left between the two under the solid shell.
    sc = load('worldgen/density_function/overworld/sloped_cheese.json')
    assert sc['type'] == 'minecraft:cache' and sc['input']['right'] == 'minecraft:overworld/base_3d_noise', 'vanilla sloped_cheese changed'
    sc['input']['right'] = mul('minecraft:overworld/base_3d_noise', add(1.0, mul(-1.0, CLEAN)))
    write('minecraft/worldgen/density_function/overworld/sloped_cheese.json', sc)
    write('minecraft/worldgen/density_function/overworld/final_density.json', fd)
    psl = load('worldgen/density_function/overworld/preliminary_surface_level.json')
    assert move_ceiling(psl) == 1 and extend_depth(psl) == 1, 'vanilla preliminary_surface_level changed'
    write('minecraft/worldgen/density_function/overworld/preliminary_surface_level.json', psl)
    print('wrote the overhaul data pack under', OUT)


if __name__ == '__main__':
    main()
