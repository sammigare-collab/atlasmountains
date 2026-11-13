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
     a field of pyramids, each a solid of its own built up from the land's level under its
     summit; the ground is whichever is higher, the land or the pyramid. Each peak reads the
     strength once, at its own summit, and is that fraction of its full height with its angles
     unchanged; a weak one is left out. A river that meets a peak runs in a straight-sided V
     (atlasmountains:river_wall, see RiverWallFunction.java).
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
CHANNEL = 0.04        # along the river channel itself (|weirdness| below this) nothing stands above the land ...
RIVER_WALL = 0.95     # ... and beside it a piece of a peak may rise only this many blocks per block of distance from the
                      # channel (44 degrees, the angle of the peaks' own faces): a river that meets a peak runs in a
                      # straight-sided V. Up to 0.4.7 the limit was a multiple of weirdness itself (VALLEY_STEEP),
                      # a noise: lumpy, 22 to 71 degrees, and the cause of the fins, towers and domes Sammi found
RIVER_WALL_MAX = 1.73 # a piece whose summit is too close to a river for that gets a steeper wall, up to this (60 degrees)
PEAK_TEMP_FROM, PEAK_TEMP_FULL = 3 / 128.0, 6.5 / 128.0  # the temperature the biomes see is taken over where a peak stands 3 to 6.5 blocks above the land ...
PEAK_BIOME_FROM, PEAK_BIOME_FULL = 7 / 128.0, 9 / 128.0  # ... and the weirdness and erosion from 7 to 9 (after it, so no snowy slope biome can form a ring between)
ROCK_BELOW = -10000    # the rock line is OFF since 0.5.1 (a huge negative number: no ground is ever that far above the snow line).
#                        It was: in mountain country, ground higher than 100 blocks under the snow line is rock, so that
#                        vanilla land showing between the flat-plane peaks high up was not meadow (0.4.10). The eroded
#                        mountains of 0.5.0 sit on top of the land, so no land shows through them, and next to a small low
#                        peak the line fell below the ordinary ground around it: Sammi's screenshot at -9837 -2238 (seed
#                        -3550034452994718772) is a flat patch of bare rock in a grassy hollow with no mountain on it.
OFF_PEAK_E = -0.30    # the erosion the biomes are shown on vanilla's own land in mountain country (its third band: meadow, forest, taiga)
PEAK_TEMPERATURE = 0.4 # below the snow a peak's biome is shown this temperature: vanilla's stony peaks (bare rock, no snow)
SNOW_BIOME_ABOVE = 25 # the cold peak biome starts this many blocks above the snow line (the line itself is one per range:
                      # HornsFunction.snowLine and SnowLine.java), where the peaks' own snow is already complete ...
CAP_WARMEST = 0.55    # ... in vanilla's cold and temperate bands (temperature below this), shown as CAP_TEMPERATURE there
CAP_TEMPERATURE = -0.3 #    so the cap's biome is vanilla's jagged (or, colder, frozen) peaks
SNOW_EDGE = 0.06      # the cold biome's lower edge wanders up and down by this much (in offset units: about 8 blocks)
SNOW = {'edge': 9.0,   # the snow itself (PeakSnowCondition.java): its edge wanders up and down by this many blocks,
        'gully': 14.0, # it reaches this much lower in the gullies and starts this much higher on the ribs,
        'fringe': 12.0}  # and it is in patches for this many blocks above its edge, complete above that
ROCK_SPAN = 220       # the rock gradient: 100% is the snow line, 0% is this many blocks below it (on the tallest peaks;
ROCK = [               # in proportion on smaller ones). Dark smooth basalt and deepslate in a band about 30 blocks deep
    ('minecraft:andesite', 5), ('minecraft:stone', 30), ('minecraft:tuff', 62),    # under the snow, tuff then stone on the
    ('minecraft:deepslate', 86), ('minecraft:smooth_basalt', 95)]                  # body, andesite only about 200 down.
                      # Aim: about a fifth dark, most of it mid-grey. 0.4.7 was two-thirds light grey; 0.4.8 two-thirds dark,
ROCK_BLEND = 0.6      # which hid the ribs and clashed with vanilla's stone. ROCK_BLEND: how much of each gap is a mix
PEAK_BIOME_W = 0.70   # the weirdness the biomes are shown there (a crest line is 2/3) ...
PEAK_BIOME_E = -0.90  # ... and the erosion (vanilla's lowest band, the only one with peak biomes right down to the coast)
CLEAN_FROM = 0.03     # where a peak stands this far above the land (4 blocks) its faces start to be cut clean ...
CLEAN_FULL = 0.16     # ... and by here (20 blocks) the ground is exactly the peak's own shape: no 3-D roughness
SHELL_BLOCKS = 12     # and solid for this many blocks down, so caves cannot open holes in a face
WORLD_MIN_Y, WORLD_HEIGHT = -64, 576   # the overworld: y -64 to 511 (vanilla: height 384, top y 319)
TOP_FROM, TOP_TO = 472, 488   # the terrain ceiling (vanilla: 240 to 256)
# the peaks themselves: see HornsFunction.java and tools/prototype_horns.py (same names)
HORNS = {
    'cell': 230.0,          # one peak per square this size
    'sides': [3, 4],        # flat faces per peak. Five and more read as a cone, not a pyramid
    'side_jitter': 0.3,     # how unevenly the faces are turned (0 = a regular pyramid)
    'slope': 0.95,          # how steep a face is: blocks down per block out (0.95 is 44 degrees) ...
    'slope_var': 0.25,      # ... give or take this fraction, face by face (35 to 50 degrees): one steeper side, one longer (0.12 to 0.4.9)
    'edge_min': 0.45,       # no edge between two faces is shallower than this fraction of 'slope'
    'warp': 3.0,            # edges wander this many blocks from a ruler line
    'height': 172.0,        # the tallest peaks, above the land they stand on
    'height_var': 0.42,     # other peaks are up to this much lower
    'min_height': 60.0,     # a peak that would be shorter than this (weak mountain country) is left out
    # Look D, which Sammi picked from four in the game (12 Nov 2026): strong ribs and shoulders
    'shoulders': [1, 2],    # how many steps (sub-summits) a peak has on its edges; see the piece loop in HornsFunction.java
    'shoulder_size': 0.65,  # not used since 0.4.10 (a step's height comes from the edge it stands on)
                            # (how far out they stand, and the steeper summits, are constants in HornsFunction)
    'rib_depth': 20.0,      # buttresses: the gully between two panels of a face is up to this many blocks deep ...
    'rib_spacing': 46.0,    # ... and a panel is this many blocks wide (look C+ of the 0.4.10 sampler, Sammi's choice)
    # the skirt: the lowest part of a peak carries on down more gently until it meets the ground
    'skirt_from': 0.4,      # the lowest 40% of a peak's height is skirt ...
    'skirt_slope': 0.62,    # ... at this fraction of the face's steepness (about 30 degrees)
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


def write_surface():
    """8. The peaks' own surface (material rules). On a peak, above water: the top block is snow
    where atlasmountains:peak_snow says snow lies (above the range's snow line, lower in the
    gullies, in patches at its edge); everywhere else on the peak, and under the snow, the rock
    gradient (atlasmountains:peak_rock). Below water, and off the peaks, vanilla's own surface. This
    replaces vanilla's peak surfaces (snow and ice sheets on jagged and frozen peaks) and the
    dirt-and-grass collar round each foot."""
    rule = load('worldgen/material_rule/overworld.json')
    seq = rule['sequence']
    i = next(k for k, r in enumerate(seq) if isinstance(r, dict) and r.get('if_true', {}).get('type') == 'minecraft:above_preliminary_surface')
    assert seq[i]['then_run'] == 'minecraft:overworld/surface', 'vanilla material rule changed'
    def cond(c, r): return {'type': 'minecraft:condition', 'if_true': c, 'then_run': r}
    def noise(name, lo, hi): return {'type': 'minecraft:noise_threshold', 'noise': f'{NS}:{name}', 'min_threshold': lo, 'max_threshold': hi}
    snow = {'type': 'minecraft:block', 'result_state': 'minecraft:snow_block'}
    top = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': False, 'secondary_depth_range': 0}
    deep = {'type': 'minecraft:stone_depth', 'offset': 8, 'surface_type': 'floor', 'add_surface_depth': True, 'secondary_depth_range': 0}
    rock = {'type': f'{NS}:peak_rock', 'wobble': 8.0, 'blend_width': ROCK_BLEND, 'top_fraction': 0.72, 'span': ROCK_SPAN,
            'stops': [{'state': b, 'at': a} for b, a in ROCK]}
    peak_surface = {'type': 'minecraft:sequence', 'sequence': [
        cond(top, cond({'type': f'{NS}:peak_snow', **SNOW}, snow)),
        cond(deep, rock),
    ]}
    on_peak = cond({'type': f'{NS}:on_peak', 'tolerance': 3, 'rock_below': float(ROCK_BELOW)}, cond('minecraft:not_underwater', peak_surface))
    seq[i]['then_run'] = {'type': 'minecraft:sequence', 'sequence': [on_peak, 'minecraft:overworld/surface']}
    write('minecraft/worldgen/material_rule/overworld.json', rule)


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
    write(f'{DF}/river_wall.json', cache({'type': f'{NS}:river_wall', 'channel': CHANNEL, 'slope': RIVER_WALL, 'max_slope': RIVER_WALL_MAX,
                                            'rib_room': 40.0}))
    valley = f'{NS}:overworld/river_wall'
    write(f'{DF}/horns.json', cache(dict({'type': f'{NS}:horns'}, **HORNS)))
    #    horns gives the level the peaks reach here (far below ground where there is none); a peak
    #    is a solid of its own, so what it adds to the land is whatever of it stands above the land.
    above = {'type': 'minecraft:max', 'left': 0.0, 'right': add(f'{NS}:overworld/horns', mul(-1.0, VOFF))}
    write(f'{DF}/peak_height.json', cache({'type': 'minecraft:min', 'left': above, 'right': valley}))
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
    #    The rock line (0.4.10): in mountain country any ground higher than ROCK_BELOW blocks under
    #    the snow line counts as peak too, vanilla's own land included (see OnPeakCondition.java).
    #    It depends on height, so nothing built from it may be wrapped in a (2-D) cache.
    write(f'{DF}/peak_cap.json', cache({'type': f'{NS}:peak_cap', 'above': SNOW_BIOME_ABOVE}))
    y_off = {'type': 'minecraft:gradient', 'axis': 'y', 'from_coordinate': -64, 'from_value': (-64 - 128) / 128.0,
             'to_coordinate': 512, 'to_value': (512 - 128) / 128.0}          # this block's height, in offset units
    edge = mul({'type': 'minecraft:noise', 'noise': f'{NS}:snow_edge', 'xz_scale': 1.0, 'y_scale': 0.0}, SNOW_EDGE)
    above_cap = add(add(y_off, edge), mul(-1.0, f'{NS}:overworld/peak_cap'))    # offset units above the cold-biome line
    #    A hard step, not a ramp: half-way through a ramp the biomes would see erosion low enough
    #    and a temperature cold enough for vanilla's snowy slopes, and a line of snow patches
    #    formed along the rock line (first test of 0.4.10).
    ROCKY = mul(clamp(mul(add(above_cap, (SNOW_BIOME_ABOVE + ROCK_BELOW) / 128.0), 100000.0), 0.0, 1.0),
                clamp(mul(add(AMP, -0.45), 100000.0), 0.0, 1.0))
    K = {'type': 'minecraft:max', 'left': smooth('peak_biome', ramp(PEAK, PEAK_BIOME_FROM, PEAK_BIOME_FULL)), 'right': ROCKY}
    sign = clamp(mul(W, 10000.0), -1.0, 1.0)
    write(f'{DF}/biome_ridges.json', add(W, mul(K, add(mul(sign, PEAK_BIOME_W), mul(-1.0, W)))))
    ns = load('worldgen/noise_settings/overworld.json')
    assert ns['noise_router']['ridges'] == 'minecraft:overworld/ridges', 'vanilla noise router changed'
    ns['noise_router']['ridges'] = f'{NS}:overworld/biome_ridges'
    #    The erosion the biomes see is pushed down on the peaks in the same way (0.4.8). Weirdness
    #    alone was not enough: near the coast, and where erosion is only just low enough for peaks,
    #    vanilla's table gives plains, forest, meadow or savanna even on a crest line. On Sammi's
    #    seed that left whole peaks in those biomes: no snow caps, stray trees, and a savanna
    #    village built up a rock wall. Only vanilla's lowest erosion band has peak biomes everywhere.
    assert ns['noise_router']['erosion'] == 'minecraft:overworld/erosion', 'vanilla noise router changed'
    #    Off the peaks, the other way (0.4.10): vanilla's own land in mountain country is shown
    #    erosion OFF_PEAK_E. At the erosion it really has, vanilla makes its slopes snowy slopes
    #    and grove in every cool climate, so there was snow, powder snow and ice on valley sides
    #    far below peaks that were bare rock up to their snow line, and where vanilla land poked
    #    through a peak's face it made a white patch in the middle of it. One erosion band up the
    #    same land is meadow, forest or taiga by its own climate (still snowy in frozen country).
    def lerp(a, b, t): return add(a, mul(t, add(b, mul(-1.0, a))))
    #    The lift covers all of vanilla's two lowest erosion bands inland (they end at -0.375),
    #    not just where peaks stand: slopes on the approach to mountain country were snowy too.
    lift = mul(ramp(EROSION, -0.365, -0.385), clamp(mul(ramp(CONT, INLAND_FROM, INLAND_FULL), 8.0), 0.0, 1.0))
    e_off = lerp(EROSION, {'type': 'minecraft:max', 'left': EROSION, 'right': OFF_PEAK_E}, lift)
    write(f'{DF}/biome_erosion_off_peak.json', cache(e_off))
    write(f'{DF}/biome_erosion.json', lerp(f'{NS}:overworld/biome_erosion_off_peak', {'type': 'minecraft:min', 'left': EROSION, 'right': PEAK_BIOME_E}, K))
    ns['noise_router']['erosion'] = f'{NS}:overworld/biome_erosion'
    #    And the temperature the biomes see: on a peak, below its snow cap, it is PEAK_TEMPERATURE,
    #    so the biome there is vanilla's stony peaks (bare rock, no snow layers, no trees) in any
    #    climate. In the cap the biomes see vanilla's own temperature, so a peak in cold country
    #    has vanilla's cold peak biome (snow, and snow falling) in its cap and nowhere else, and a
    #    peak in warm country stays stony to the top (shown PEAK_TEMPERATURE there too: vanilla's
    #    hottest peaks would be badlands). Before 0.4.7 the whole upper part of every
    #    peak in cold country was jagged or frozen peaks, which covered it in snow and blue ice.
    #    From 0.4.9 the snow itself is placed by the peaks' surface rule from one snow line per
    #    range, and the cold biome only starts SNOW_BIOME_ABOVE over that line (atlasmountains:peak_cap).
    assert ns['noise_router']['temperature'] == 'minecraft:overworld/temperature', 'vanilla noise router changed'
    in_cap = clamp(mul(above_cap, 128.0 / 4.0), 0.0, 1.0)
    #    In the cap, temperate country (vanilla's stony band) is shown cold too, so its peaks get
    #    caps as in Sammi's reference, not just a dusting; only warm country stays bare. (0.4.7 to
    #    0.4.9 also showed vanilla's crest land between the peaks PEAK_TEMPERATURE; the erosion
    #    shown off the peaks, above, does that job now.)
    T = 'minecraft:overworld/temperature'
    KT = {'type': 'minecraft:max', 'left': smooth('peak_biome_temperature', ramp(PEAK, PEAK_TEMP_FROM, PEAK_TEMP_FULL)), 'right': ROCKY}
    not_warm = clamp(mul(add(CAP_WARMEST, mul(-1.0, T)), 1000.0), 0.0, 1.0)
    t_cap = lerp(PEAK_TEMPERATURE, {'type': 'minecraft:min', 'left': T, 'right': CAP_TEMPERATURE}, not_warm)
    t_base = lerp(T, t_cap, mul(KT, in_cap))
    push = mul(KT, add(1.0, mul(-1.0, in_cap)))                             # on a peak, below its cap
    write(f'{DF}/biome_temperature.json', lerp(t_base, PEAK_TEMPERATURE, push))
    ns['noise_router']['temperature'] = f'{NS}:overworld/biome_temperature'
    for name, octave, count in (('snow_edge', -5, 2), ('snow_patches', -4, 3), ('rock_wobble', -8, 2), ('rock_mottle', -2, 2)):
        write(f'{NS}/worldgen/noise/{name}.json', {'base_amplitude': 0.9494731054427981, 'base_octave': octave, 'octave_count': count})
    write_surface()

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
