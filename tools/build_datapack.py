#!/usr/bin/env python3
"""
Builds the mod's worldgen JSON from the vanilla 26.3 data files in tools/vanilla-26.3
(extracted from data/minecraft in the Minecraft jar by tools/extract_vanilla.py; not committed). Run from the project root:

    python3 tools/build_datapack.py

Everything the mod ships under data/ is generated here, so a change to vanilla or to a
parameter below is a re-run, not a hand edit. The height parameters must match the defaults
in MountainSettings (valley floor, summit height, rock fraction); SettingsSyncTest in core
checks them.

Two world presets are produced:
  atlasmountains:atlasmountains  "Atlas Mountains"              noise continents, our ridges filling vanilla's mountain footprint, vanilla elsewhere
  atlasmountains:all             "Atlas Mountains (everywhere)" the same ridges over the whole world, no footprint (for testing the mountains)

How the script is laid out, top to bottom:
  1. parameters         heights, lines and limits shared with the Java side
  2. helpers            JSON loading and writing, and small builders for density-function nodes
  3. noises             climate noises at twice vanilla's wavelength, plus the material noises
  4. density functions  our terrain, the continents, and copies of vanilla's terrain shape
                        re-pointed at them
  5. final density      vanilla's, and ours with caves gated off near the summits
  6. material rules     rock by height, snow, gravel and grass on our slopes; vanilla elsewhere
  7. biomes             three climate bands with sparse spruce, and treeless twins above the treeline
  8. presets            build_variant() puts it all together, once per preset

Vocabulary used in the comments:
  density        Minecraft's terrain is solid where a 3D "final density" is positive and air
                 where it is negative. A height h becomes density (h - y) * DENSITY_PER_BLOCK.
  mask           0..1, how much of our terrain applies (atlasmountains:mountain_mask): 1 in our
                 mountain provinces, 0 in vanilla terrain, a soft fade between.
  router         the noise settings' table of named density functions vanilla reads for terrain,
                 biomes, aquifers and spawn.
  material rules the 26.x name for surface rules: which block goes where near the surface.
"""
import json, os, copy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VAN = os.path.join(ROOT, 'tools', 'vanilla-26.3')
if not os.path.isdir(VAN):
    raise SystemExit('tools/vanilla-26.3 is missing: run python3 tools/extract_vanilla.py first')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- 1. parameters (keep in step with MountainSettings) -------------------------------
VALLEY_FLOOR = 64          # MountainSettings.valleyFloorHeight (one above vanilla sea level)
SUMMIT = 232               # MountainSettings.summitHeight (design summit, before the field layout's height scale)
ROCK_FRACTION = 0.5        # MountainSettings.rockFraction
ROCK_LINE = round(SUMMIT - ROCK_FRACTION * (SUMMIT - VALLEY_FLOOR))   # MountainSettings.rockLineHeight(), 148
SEA_LEVEL = 63             # vanilla; the lake surface (floor - 1) is the same height
SNOW_LINE = 205            # typical summits are about y 212 (design 232 x the field height scale, mostly near 0.88), the tallest about y 286
SNOW_JITTER_MULT = 3       # surface_depth_multiplier on the y_above condition (jitters by a few blocks)
SNOW_PATCH_LOWEST = 160    # patchy snow on ledges reaches down to here (atlasmountains:snow_cover)
ROCK_SLOPE_DEG = 35        # steeper than this = stone
CAVE_TOP = 135             # no density-function cave carving at or above this y (mountain zones)
DIM_MIN_Y = -64
DIM_HEIGHT = 576           # build limit 512; the tallest summits are under y 300, so there is headroom
DENSITY_PER_BLOCK = 0.15   # sloped_cheese = (terrain_height - y) * this; vanilla caves expect ~1.56 ten blocks down
VANILLA_EROSION_FLOOR = -0.1   # vanilla erosion is never lower than this, so vanilla makes no peaks of its own
VANILLA_TOP = 320          # vanilla's own build height; its 3D noise is skipped above this (see gate_interpolated)

CLIMATE = {   # firstOctave: vanilla verified from the data files -> target. Each step of -1 doubles the wavelength.
    'temperature': -11,      # vanilla -10, twice vanilla
    'vegetation': -9,        # vanilla -8, twice vanilla
    'continentalness': -10,  # vanilla -9, twice vanilla; only the "everywhere" preset still uses this noise (the mixed preset uses the mod's continents)
    'erosion': -10,          # vanilla -9, twice vanilla: flat or hilly provinces; also vanilla's mountain footprint, which ours fill
}
# Biome size: twice vanilla on every noise (one octave slower). The ridge
# (weirdness) noise is left at vanilla so rivers keep their vanilla spacing and shape.
TREELINE = 92              # no trees in the mountain biomes above this y (lower third of the grass flank only)

def load(rel):
    with open(os.path.join(VAN, rel)) as f:
        return json.load(f)

def write(rel, obj):
    p = os.path.join(OUT, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')
    print('wrote', os.path.relpath(p, ROOT))

def rename(obj, old, new):
    return json.loads(json.dumps(obj).replace(f'"{old}"', f'"{new}"'))

# Our 2D functions are wrapped in a cache node. In 26.x there is one generic "minecraft:cache"
# (the old cache_2d, flat_cache and cache_once are gone); it stores a compiled sampler's results
# so that several parents reading the same function (the blend height is read by the terrain,
# the preliminary surface and the vanilla push-down) compute it once. Our column functions
# already compute one value per column and copy it down (ColumnFunction), so this is about
# sharing between parents, not about y.
def cache2d(f): return {'type': 'minecraft:cache', 'input': f}
TERRAIN = cache2d({'type': f'{NS}:terrain_height', 'mixed': False})                 # "everywhere" preset's cache
TERRAIN_FAST = cache2d({'type': f'{NS}:terrain_height', 'eroded': False, 'mixed': False})   # analytic model, no erosion; for biome depth only
MASK = cache2d({'type': f'{NS}:mountain_mask'})
SC = f'{NS}:overworld/sloped_cheese'
TERRAIN_MIXED = cache2d({'type': f'{NS}:terrain_height', 'mixed': True})       # raw eroded height, no mask: the blend below applies the mask
# Edge blend (mixed world). In the mask's fade band the surface is a true blend of heights,
# h = vanilla_h + mask * (ours - vanilla_h), where vanilla_h is vanilla's jaggedness-free surface
# 128 + 128 * offset (computed analytically in build_variant; find_top_surface was tried and sits
# 6-12 blocks too low, which flooded rings round every province). Vanilla's own 3D density is shifted down by VANILLA_SLOPE per block
# of (vanilla_h - h) so its surface follows the blend, and is faded out by the mask; so a
# vanilla hill inside a mountain cell ramps down to our valley floor across the band instead of
# standing in it as a wall, and nothing is hollow. VANILLA_SLOPE is vanilla's final density per
# block near the surface (about 0.1 to 0.2); erring high only pushes vanilla below the blend,
# where our term holds the surface.
VANILLA_SLOPE = 0.25
EDGE_SINK = 64             # blocks our surface sits under the blend at mask 0 (it rises to the blend over EDGE_SINK_BAND, quadratically)
FOOT_BAND = 16             # our terrain takes over from vanilla's across its first this many blocks above its floor (see build_variant)
EDGE_SINK_BAND = 0.15      # the sink fades in over this much of the mask at its outer edge
EDGE_PUSH = 12             # blocks vanilla is pushed under the blend at mask 1
PSL_SLOPE = 0.4            # the same for the jaggedness-free preliminary-surface density
TERRAIN_FAST_MASKED = cache2d({'type': f'{NS}:terrain_height', 'eroded': False, 'mask_relief': True, 'mixed': True})

# ---- 2. helpers: density-function nodes as Python dicts (26.x keys: left/right) ----------------
def mul(a, b): return {'type': 'minecraft:mul', 'left': a, 'right': b}
def sub(a, b): return {'type': 'minecraft:sub', 'left': a, 'right': b}
def add(a, b): return {'type': 'minecraft:add', 'left': a, 'right': b}
def dmin(a, b): return {'type': 'minecraft:min', 'left': a, 'right': b}
def dmax(a, b): return {'type': 'minecraft:max', 'left': a, 'right': b}

def lerp(mask, vanilla, ours):
    """Weighted average: (1 - mask) * vanilla + mask * ours. Only for smooth 2D quantities such
    as the biome depth. Never for the terrain density: averaging a solid and an air density
    gives a hollow interior where the mask is near 0.5."""
    if mask == 1.0:
        return ours
    return add(mul(add(1.0, mul(-1.0, mask)), vanilla), mul(mask, ours))

def union(mask, vanilla, ours):
    """Terrain blend: max(mask * ours, (1 - mask) * vanilla). Solid wherever either side is
    solid, so a range (whose relief is already faded by the mask) always sits on top of
    vanilla ground instead of hovering over a hollow. mul short-circuits when its first
    argument is 0, so outside the mountains our terrain (and its region erosion) is never
    evaluated."""
    if mask == 1.0:
        return ours
    return dmax(mul(mask, ours), mul(add(1.0, mul(-1.0, mask)), vanilla))

def y_gate(expr, else_value):
    """expr below CAVE_TOP, else_value at and above it."""
    return {'type': 'minecraft:range_choice', 'input': 'minecraft:y',
            'min_inclusive': -1000000.0, 'max_exclusive': float(CAVE_TOP),
            'when_in_range': expr, 'when_out_of_range': else_value}

def gate_interpolated(node, gate_y, fallback):
    """Every 'interpolated' node's inner expression is evaluated for the whole column, all the
    way to the build limit, whatever the outer expression does with it. In a 1024-tall world
    that triples the cost of vanilla's cave noise for nothing. Gating the inner expression on y
    makes everything above gate_y a constant."""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:interpolated':
            inner = node['input']
            gate_interpolated(inner, gate_y, fallback)
            node['input'] = {'type': 'minecraft:range_choice', 'input': 'minecraft:y',
                                'min_inclusive': -1000000.0, 'max_exclusive': float(gate_y),
                                'when_in_range': inner, 'when_out_of_range': fallback}
            return
        for v in node.values(): gate_interpolated(v, gate_y, fallback)
    elif isinstance(node, list):
        for v in node: gate_interpolated(v, gate_y, fallback)

def fix_top(node):
    """the vanilla 'air above the top' gradient (240..256) moved near our build limit"""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:gradient' and node.get('from_coordinate') == 240 and node.get('to_coordinate') == 256:
            node['from_coordinate'] = DIM_MIN_Y + DIM_HEIGHT - 60
            node['to_coordinate'] = DIM_MIN_Y + DIM_HEIGHT - 20
        for v in node.values(): fix_top(v)
    elif isinstance(node, list):
        for v in node: fix_top(v)

# ---- 3. climate noises ------------------------------------------------------------------
# Copies of vanilla's noises with the first octave moved one step lower (twice the wavelength),
# so every biome is twice vanilla's size. Only base_octave changes; amplitudes stay vanilla's.
for name, octave in CLIMATE.items():
    n = load(f'worldgen/noise/{name}.json')
    n['base_octave'] = octave
    write(f'{NS}/worldgen/noise/{name}.json', n)

# ---- 3b. material noises -----------------------------------------------------------------
# Small noises read by our own material rules (see RockGradientRule and SnowCoverCondition).
# 26.x noise files give the first octave, the octave count and base_amplitude, a normalising
# factor that depends only on the octave count; the values below are copied from vanilla noises
# with the same count (pillar.json for 2 octaves, surface.json for 3).
BASE_AMPLITUDE = {2: 0.9494731054427981, 3: 0.9381732587751008}
def material_noise(name, base_octave, octaves):
    write(f'{NS}/worldgen/noise/{name}.json',
          {'base_amplitude': BASE_AMPLITUDE[octaves], 'base_octave': base_octave, 'octave_count': octaves})
material_noise('rock_wobble', -8, 2)    # wavelength about 256 blocks: the rock gradient rises and dips across a ridge
material_noise('rock_mottle', -2, 2)    # wavelength about 4 blocks, 3D: the rocks of the gradient mix in small clumps
material_noise('snow_patches', -4, 3)   # wavelength about 16 blocks, with finer detail: blobs a dozen blocks across

# ---- 4. density functions ---------------------------------------------------------------
# Our terrain as density: (terrain height - y) * DENSITY_PER_BLOCK, so the surface is where it
# crosses 0. This is the "everywhere" preset's; the mixed preset builds a blended one per variant.
write(f'{NS}/worldgen/density_function/overworld/sloped_cheese.json',
      mul(DENSITY_PER_BLOCK, add(TERRAIN, mul(-1.0, 'minecraft:y'))))
# biome depth inside the zones: 0 at the terrain surface, +1 at 128 blocks down
write(f'{NS}/worldgen/density_function/overworld/depth.json',
      mul(1.0 / 128, add(TERRAIN_FAST, mul(-1.0, 'minecraft:y'))))
write(f'{NS}/worldgen/density_function/overworld/depth_masked.json',
      mul(1.0 / 128, add(TERRAIN_FAST_MASKED, mul(-1.0, 'minecraft:y'))))
# Continentalness from the mod's noise continents, once per column. The vanilla terrain splines (offset, factor, jaggedness)
# read "minecraft:overworld/continents" and "minecraft:overworld/erosion" BY NAME, not the router
# entries, so the mixed preset ships its own copies of those splines below pointing at these two.
CONTINENTS = f'{NS}:overworld/continents'
write(f'{NS}/worldgen/density_function/overworld/continents.json',
      cache2d({'type': f'{NS}:continents'}))
# The mod's erosion noise, sampled stretched along the range direction (see StretchedErosionFunction);
# biomes, vanilla terrain shape and the mountain footprint all read this one function.
write(f'{NS}/worldgen/density_function/overworld/erosion.json',
      cache2d({'type': f'{NS}:stretched_erosion'}))
EROSION_FLOORED = f'{NS}:overworld/erosion_floored'
write(f'{NS}/worldgen/density_function/overworld/erosion_floored.json', dmax(VANILLA_EROSION_FLOOR, f'{NS}:overworld/erosion'))

# Vanilla's terrain-shape functions, re-pointed at our continents and the floored erosion. Names are
# prefixed "vanilla_" because our own sloped_cheese/depth live beside them.
VANILLA_SHAPE = ['offset', 'depth', 'factor', 'jaggedness', 'sloped_cheese']
def repoint(obj):
    obj = rename(obj, 'minecraft:overworld/continents', CONTINENTS)
    obj = rename(obj, 'minecraft:overworld/erosion', EROSION_FLOORED)
    for n in VANILLA_SHAPE:
        obj = rename(obj, f'minecraft:overworld/{n}', f'{NS}:overworld/vanilla_{n}')
    return obj
# The erosion noise runs larger than vanilla's (twice vanilla now; it was 4x, Large Biomes scale,
# when this was added), so its flatter classes come in wide patches that sit at y 62-64 and pool.
# Vanilla's offset is lifted by LOWLAND_LIFT (in offset units, 128 blocks each) from the coast band
# inland so those flats stand a few blocks clear of sea level; the shore and the sea are untouched.
# The lift is faded out along vanilla's river valleys (folded ridges near -1, the same band that gets
# the river biome), so river beds stay at vanilla's depth and keep their water. There used to be an
# extra 8-block lift in the flattest class too (erosion above ~0.45); that is vanilla's swamp class,
# so it left swamps and mangrove swamps as dry hills about 12 blocks above the sea (Nov 2026), and
# it was removed, and the 4-block lift is now faded out over that class as well: swamps are meant to
# be wet, and even 4 blocks left a mangrove swamp at a median y 70, under a fifth of it in water.
LOWLAND_LIFT = 0.03        # inland except river valleys and swamps: about 4 blocks
RIDGES_FOLDED = 'minecraft:overworld/ridges_folded'   # vanilla's own, unchanged (weirdness is vanilla)
LOWLAND_RAMP = mul(mul(
    {'type': 'minecraft:spline', 'spline': {'coordinate': CONTINENTS, 'points': [
        {'location': -0.05, 'value': 0.0, 'derivative': 0.0},
        {'location': 0.25, 'value': LOWLAND_LIFT, 'derivative': 0.0}]}},
    # 0 in the valley floor (river biome is |weirdness| < 0.05, folded ridges below -0.85), full
    # lift by -0.5, so the valley sides ease up instead of stepping
    {'type': 'minecraft:spline', 'spline': {'coordinate': RIDGES_FOLDED, 'points': [
        {'location': -0.9, 'value': 0.0, 'derivative': 0.0},
        {'location': -0.5, 'value': 1.0, 'derivative': 0.0}]}}),
    # and 0 in vanilla's swamp class (erosion above 0.55 gets swamp and mangrove swamp), fading from 0.4
    {'type': 'minecraft:spline', 'spline': {'coordinate': f'{NS}:overworld/erosion', 'points': [
        {'location': 0.4, 'value': 1.0, 'derivative': 0.0},
        {'location': 0.55, 'value': 0.0, 'derivative': 0.0}]}})
for n in VANILLA_SHAPE:
    body = repoint(load(f'worldgen/density_function/overworld/{n}.json'))
    if n == 'offset':
        body = add(body, LOWLAND_RAMP)
    write(f'{NS}/worldgen/density_function/overworld/vanilla_{n}.json', body)

NOODLE = f'{NS}:overworld/caves/noodle'
noodle = load('worldgen/density_function/overworld/caves/noodle.json')
gate_interpolated(noodle, VANILLA_TOP, 0.0)      # its own outer range_choice already returns 64 above y 321
write(f'{NS}/worldgen/density_function/overworld/caves/noodle.json', noodle)

# ---- 5. vanilla final density, trimmed to vanilla's height -------------------------------------
# 26.x: final_density is its own file, add(min(squeeze(interpolated(...)), noodle), beardifier).
vanilla_ns = load('worldgen/noise_settings/overworld.json')
vanilla_final = rename(load('worldgen/density_function/overworld/final_density.json'), 'minecraft:overworld/caves/noodle', NOODLE)
fix_top(vanilla_final)
gate_interpolated(vanilla_final, VANILLA_TOP, -64.0)   # air above vanilla's own ceiling, computed for free

def make_our_final(sc):
    """Our final density: vanilla's final_density with its sloped_cheese replaced by ours, caves
    switched off at and above CAVE_TOP, and the surface taken from the exact (un-interpolated)
    terrain density so the faces do not show vanilla's 4x8 cell grid."""
    ours = rename(load('worldgen/density_function/overworld/final_density.json'), 'minecraft:overworld/sloped_cheese', sc)
    ours = rename(ours, 'minecraft:overworld/caves/noodle', NOODLE)
    fix_top(ours)
    gate_interpolated(ours, CAVE_TOP, 64.0)    # solid above the cave ceiling; the exact surface is min'ed in below
    # The surface itself must not go through vanilla's 4x8-block cell interpolation, or the 4-block
    # grid shows on the faces. So the near-surface branch keeps only its cave-entrance term, and the
    # exact terrain density is min'ed in OUTSIDE the interpolated expression. Caves are gated off
    # at and above CAVE_TOP (the noodle caves too).
    def gate_caves(node):
        if isinstance(node, dict):
            if node.get('type') == 'minecraft:range_choice' and node.get('input') == sc:
                wi = node['when_in_range']
                assert wi['type'] == 'minecraft:min' and wi['left'] == sc
                node['when_in_range'] = y_gate(wi['right'], 64.0)          # 5 * entrances only
                node['when_out_of_range'] = y_gate(node['when_out_of_range'], 64.0)
                return
            for v in node.values(): gate_caves(v)
        elif isinstance(node, list):
            for v in node: gate_caves(v)
    gate_caves(ours)
    assert ours['type'] == 'minecraft:add' and ours['right'] == {'type': 'minecraft:beardifier'}
    body = ours['left']
    assert body['type'] == 'minecraft:min' and body['right'] == NOODLE
    caves_part = body['left']                     # squeeze(interpolated(...))
    exact_part = {'type': 'minecraft:squeeze', 'input': mul(0.64, sc)}
    return add(dmin(dmin(exact_part, caves_part), y_gate(NOODLE, 64.0)), {'type': 'minecraft:beardifier'})

# ---- preliminary surface (26.x): find_top_surface over the initial density ----------------
# Vanilla's preliminary_surface_level is find_top_surface over the jaggedness-free density; the
# material rules' above_preliminary_surface, the aquifers and the router's chunk_surface_level
# all read it. Ours searches the same union of vanilla and our terrain that the final density
# uses, so the surface rules and aquifers see our mountains.
vanilla_psl = load('worldgen/density_function/overworld/preliminary_surface_level.json')
assert vanilla_psl['type'] == 'minecraft:find_top_surface'
def make_preliminary(mask, sc, terrain_height, vanilla_density, vanilla_upper):
    psl = copy.deepcopy(vanilla_psl)
    fix_top(psl)
    psl['density'] = union(mask, vanilla_density, sc)
    psl['upper_bound'] = dmax(vanilla_upper, add(terrain_height, 16.0)) if mask != 1.0 else add(terrain_height, 16.0)
    return psl

# ---- 6. material rules (26.x name for surface rules) ------------------------------------------
# A rule is a tree of conditions and results, evaluated top to bottom for each block near the
# surface; the first result reached wins. Ours, for a block on our slopes (mountain_rules below):
#   top block    snow above the snow line, patchy snow below it on gentle ground, rock (gradient)
#                on steep ground, grass where water can't reach, gravel otherwise
#   just below   snow above the snow line, rock on steep ground, dirt otherwise
# Anything deeper falls through to vanilla's underground rules (stone, deepslate, ores).
def block(name, props=None):
    if props:
        return {'type': 'minecraft:block', 'result_state': {'id': name, 'properties': props}}
    return {'type': 'minecraft:block', 'result_state': name}
def cond(c, then): return {'type': 'minecraft:condition', 'if_true': c, 'then_run': then}
def seq(*rules): return {'type': 'minecraft:sequence', 'sequence': list(rules)}
def noise_between(lo, hi):
    return {'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface', 'min_threshold': lo, 'max_threshold': hi}

top_block = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': False, 'secondary_depth_range': 0}
surface_layer = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': True, 'secondary_depth_range': 0}
above_water = {'type': 'minecraft:water', 'offset': 0, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
snow = {'type': 'minecraft:y_above', 'anchor': {'absolute': SNOW_LINE}, 'surface_depth_multiplier': SNOW_JITTER_MULT, 'add_stone_depth': False}
def steep(mixed): return {'type': f'{NS}:slope_above', 'degrees': ROCK_SLOPE_DEG, 'mixed': mixed}
in_mountains = {'type': f'{NS}:on_our_slope', 'above_floor': 6.0}   # valley floors and our flanks; not the foot where vanilla shows

# Exposed rock: a gradient down each column's own face (atlasmountains:rock_gradient, see
# RockGradientRule), so every peak shades the same way whatever its height (Sammi: "from each
# peak, not a universal height", and "snow to dark to light"). Stop y values are percentages of
# the face: 100 where the rock begins (the summit, or the snow line if the summit is above it),
# 0 at the valley floor. Darkest (smooth basalt) at the top, lightening step by step through
# deepslate, tuff and stone to pale andesite near the foot. The rocks are in order of their
# average texture brightness (about 72, 80, 108, 125, 136 of 255). Mixed in small clumps between
# stops and wobbled up and down by a slow noise so the change never follows one contour.
# (Tried and dropped: noise blotches of tuff and packed mud, horizontal strata ("too stripy"),
# calcite seams ("too messy"), the gradient at fixed heights, and the gradient in blocks below
# the summit, whose dark band hid under the snow.)
def rock(mixed):
    return {'type': f'{NS}:rock_gradient', 'wobble': 8.0, 'blend_width': 1.0,
            'relative_to_peak': True, 'top_cap': SNOW_LINE, 'floor': int(VALLEY_FLOOR), 'mixed': mixed, 'stops': [
        {'state': 'minecraft:andesite',      'y': 20},
        {'state': 'minecraft:stone',         'y': 40},
        {'state': 'minecraft:tuff',          'y': 60},
        {'state': 'minecraft:deepslate',     'y': 78},
        {'state': 'minecraft:smooth_basalt', 'y': 94},
    ]}
def snow_cover(mixed):   # patchy snow on ledges below the snow line (see SnowCoverCondition)
    return {'type': f'{NS}:snow_cover', 'snow_line': SNOW_LINE, 'lowest': SNOW_PATCH_LOWEST,
            'ledge_slope': 38.0, 'steep_penalty': 0.45, 'mixed': mixed}
def mountain_rules(mixed): return seq(
    cond(top_block, seq(
        cond(snow, block('minecraft:snow_block')),
        cond(snow_cover(mixed), block('minecraft:snow_block')),
        cond(steep(mixed), rock(mixed)),
        cond(above_water, block('minecraft:grass_block')),
        block('minecraft:gravel'),
    )),
    cond(surface_layer, seq(
        # snow a few blocks deep above the snow line, not only on top: on steep ground most of what
        # you see is the sides of the voxel steps, which are surface-layer blocks, and with only a
        # top block of snow the summits read as dark rock with white specks
        cond(snow, block('minecraft:snow_block')),
        cond(steep(mixed), rock(mixed)),
        block('minecraft:dirt'),
    )),
)
# vanilla's overworld rule: sequence[bedrock_floor, copper, iron, condition(above_preliminary_surface, overworld/surface), overworld/underground]
vanilla_rule = load('worldgen/material_rule/overworld.json')
assert vanilla_rule['type'] == 'minecraft:sequence'
_surface_idx = [i for i, r in enumerate(vanilla_rule['sequence'])
                if isinstance(r, dict) and r.get('type') == 'minecraft:condition' and r['if_true'].get('type') == 'minecraft:above_preliminary_surface']
assert len(_surface_idx) == 1, vanilla_rule
SURFACE_IDX = _surface_idx[0]
vanilla_surface_run = vanilla_rule['sequence'][SURFACE_IDX]['then_run']      # "minecraft:overworld/surface"

# ---- 7. biomes: three climate bands, vanilla copies with vegetation replaced by sparse spruce ----
VEGETATION = [f'{NS}:sparse_spruce', 'minecraft:patch_grass_plain']
BIOMES = {   # our id: (vanilla template, temperature range in multi-noise units)
    'cold':      ('snowy_taiga', [-1.0, -0.2]),
    'temperate': ('meadow',      [-0.2,  0.25]),
    'warm':      ('savanna',     [ 0.25, 1.0]),
}
write(f'{NS}/worldgen/placed_feature/sparse_spruce.json', {
    'feature': 'minecraft:spruce',
    'placement': [
        {'type': 'minecraft:count', 'count': 1},
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:surface_water_depth_filter', 'max_water_depth': 0},
        {'type': 'minecraft:heightmap', 'heightmap': 'OCEAN_FLOOR'},
        {'type': 'minecraft:block_predicate_filter', 'predicate': {'type': 'minecraft:would_survive',
            'state': 'minecraft:spruce_sapling'}},
        {'type': 'minecraft:biome'},
    ]})
entries = []
for ours_id, (template, trange) in BIOMES.items():
    b = load(f'worldgen/biome/{template}.json')
    feats = b['features']
    while len(feats) < 11: feats.append([])
    feats[9] = VEGETATION          # vegetal decoration
    feats[10] = []                 # no freeze_top_layer; snow comes from the surface rule
    if ours_id == 'cold':
        # Snowy taiga is freezing at every height (-0.5), so vanilla's freeze_top_layer, which runs
        # for a whole chunk from any vanilla biome in it, laid snow on our cold columns in every
        # chunk along a province edge: a white fringe a chunk wide at any height. At 0.3 the game's
        # height-adjusted temperature only drops below freezing (0.15) above about y 200, which is
        # where our own snow starts, so precipitation and freezing match the snow line instead.
        # The grass and leaves keep snowy taiga's colours (from the colour map at temperature 0).
        b['temperature'] = 0.3
        b['effects']['grass_color'] = '#80b497'
        b['effects']['foliage_color'] = '#60a17b'
    write(f'{NS}/worldgen/biome/{ours_id}.json', b)
    # treeless twin for above the treeline (biomes are 3D, so the biome source can switch by height)
    high = copy.deepcopy(b)
    high['features'][9] = VEGETATION[1:]
    write(f'{NS}/worldgen/biome/{ours_id}_high.json', high)
    entries.append({'biome': f'{NS}:{ours_id}', 'parameters': {
        'temperature': trange, 'humidity': [-1.0, 1.0], 'continentalness': [-1.0, 1.0],
        'erosion': [-1.0, 1.0], 'weirdness': [-1.0, 1.0], 'depth': [-1.0, 1.0], 'offset': 0.0}})

# ---- dimension type: vanilla's overworld, made taller so the summits fit ------------------------
dt = load('dimension_type/overworld.json')
dt['min_y'] = DIM_MIN_Y
dt['height'] = DIM_HEIGHT
dt['logical_height'] = DIM_HEIGHT
write(f'{NS}/dimension_type/mountains.json', dt)

# ---- 8. noise settings + world preset, per variant ---------------------------------------------
def build_variant(settings_name, preset_name, mixed):
    """Writes one preset's noise settings, material rules, blended density functions and world
    preset. mixed=True is the player preset (our mountains in vanilla's footprint, blended into
    vanilla terrain at the edges); mixed=False is the test preset (our mountains everywhere, the
    mask a constant 1, so every blend collapses to ours)."""
    mask = MASK if mixed else 1.0
    ns = copy.deepcopy(vanilla_ns)
    ns['noise'] = {'min_y': DIM_MIN_Y, 'height': DIM_HEIGHT}
    ns['sea_level'] = SEA_LEVEL
    ns.pop('debug_functions', None)
    r = ns['noise_router']
    r['temperature'] = rename(load('worldgen/density_function/overworld/temperature.json'), 'minecraft:temperature', f'{NS}:temperature')
    r['vegetation'] = rename(load('worldgen/density_function/overworld/vegetation.json'), 'minecraft:vegetation', f'{NS}:vegetation')
    continents = rename(load('worldgen/density_function/overworld/continents.json'), 'minecraft:continentalness', f'{NS}:continentalness')
    if mixed:
        # Our continents replace vanilla's continentalness; erosion floored so vanilla never makes its own peaks.
        # Biomes (router) and terrain (the vanilla_* shape copies) read the same two functions.
        r['continents'] = CONTINENTS
        r['erosion'] = EROSION_FLOORED
    else:
        r['continents'] = {'type': 'minecraft:clamp', 'input': continents, 'min': -0.1, 'max': 1.0}  # never ocean/beach
        r['erosion'] = f'{NS}:overworld/erosion'
    sc = SC                    # mixed: replaced by the blended sloped_cheese below
    terrain_height = TERRAIN   # mixed: replaced by the blend height below
    depth = f'{NS}:overworld/depth_masked' if mixed else f'{NS}:overworld/depth'
    vanilla_depth = load('worldgen/density_function/overworld/depth.json')
    v_final = vanilla_final
    psl_density = copy.deepcopy(vanilla_psl['density'])
    psl_upper = copy.deepcopy(vanilla_psl['upper_bound'])
    fix_top(psl_density)
    if mixed:
        vanilla_depth, v_final = repoint(vanilla_depth), repoint(copy.deepcopy(vanilla_final))
        psl_density, psl_upper = repoint(psl_density), repoint(psl_upper)
        # vanilla's surface height, once per column, and the blended height
        # Vanilla's surface without its 3D noise: depth = gradient(y) + offset crosses 0 at
        # y = 128 + 128 * offset (jaggedness is nil here because vanilla's erosion is floored).
        # Vanilla's own preliminary surface (find_top_surface) sits 6-12 blocks under the real
        # surface by design and is capped by an estimate, so it is no use as a blend reference.
        write(f'{NS}/worldgen/density_function/{settings_name}/vanilla_surface.json',
              cache2d(add(128.0, mul(128.0, f'{NS}:overworld/vanilla_offset'))))
        HV = f'{NS}:{settings_name}/vanilla_surface'
        # The mountains stand on vanilla's ground instead of in a hole (Nov 2026). Our terrain is
        # relief above a floor at y 64; before, everything inside the layout's mask was ours down
        # to that floor, so where the land around was higher (and vanilla's land is highest exactly
        # where its erosion is low, which is where the mountains go) a mountain sat in a moat, its
        # foot dropping to 64 before the fade brought the ground back up. Now:
        #   - our relief is added on top of vanilla's surface (LIFTED = ours + max(HV - floor, 0)),
        #   - M, the mask everything below uses, is the layout's mask times a ramp that is 0 on our
        #     floor and 1 once our terrain stands FOOT_BAND blocks above it, so the floors between
        #     ranges are vanilla's own land and a flank rises out of it with no dip.
        LIFTED = add(TERRAIN_MIXED, dmax(sub(HV, float(VALLEY_FLOOR)), 0.0))
        write(f'{NS}/worldgen/density_function/{settings_name}/mask.json',
              cache2d(mul(MASK, {'type': 'minecraft:clamp', 'min': 0.0, 'max': 1.0,
                                 'input': mul(sub(TERRAIN_MIXED, float(VALLEY_FLOOR)), 1.0 / FOOT_BAND)})))
        M = f'{NS}:{settings_name}/mask'
        mask = M
        write(f'{NS}/worldgen/density_function/{settings_name}/blend_height.json',
              cache2d(add(HV, mul(M, sub(LIFTED, HV)))))
        HB = f'{NS}:{settings_name}/blend_height'
        # Our surface sits EDGE_SINK below the blend where the mask is 0 (so vanilla's real 3D surface,
        # which can differ from its jaggedness-free estimate by several blocks, is never undercut by
        # a plate at the estimate: that made a wall along the mask's outer contour). Vanilla is pushed
        # EDGE_PUSH below the blend where the mask is 1, so it leaves no step at that contour.
        # the sink only acts in the outermost EDGE_SINK_BAND of the mask (ours = the blend elsewhere,
        # so the band never dips under vanilla's own ground, let alone sea level)
        # Nov 2026: the sink was 12 blocks, linear in the mask, and vanilla was pushed linearly in
        # the mask too. In flat vanilla land (density changing only ~0.01 per block) even the small
        # push at mask 0.04 dropped vanilla's surface 10+ blocks onto our sunk plate, so a trench
        # ran along the mask's outer contour, which follows a Voronoi edge: a straight ditch across
        # open country. Now our plate rises from deep below (quadratic in the band, so it meets the
        # blend with no kink), and vanilla is only pushed inward of that band, starting from zero slope,
        # so it comes down onto the blend over a few tens of blocks instead of at one line.
        sink_t = {'type': 'minecraft:clamp', 'input': sub(1.0, mul(M, 1.0 / EDGE_SINK_BAND)), 'min': 0.0, 'max': 1.0}
        ours_h = sub(HB, mul(mul(sink_t, sink_t), EDGE_SINK))
        write(f'{NS}/worldgen/density_function/{settings_name}/sloped_cheese.json',
              mul(DENSITY_PER_BLOCK, sub(ours_h, 'minecraft:y')))
        sc = f'{NS}:{settings_name}/sloped_cheese'
        terrain_height = HB
        # vanilla is left alone in the outer band (where our plate is still rising from below, so
        # the ground there is simply vanilla's), then pushed in with zero slope; from mask 0.3 inwards the
        # push is what it always was. A push in the band let vanilla collapse in flat land (its density
        # changes only ~0.01 per block there) onto the sunk plate: dry pits a few blocks under sea level.
        push_t = {'type': 'minecraft:clamp', 'input': mul(sub(M, EDGE_SINK_BAND), 1.0 / EDGE_SINK_BAND), 'min': 0.0, 'max': 1.0}   # full by mask 0.3
        drop = mul(mul(push_t, push_t), add(dmax(sub(HV, HB), 0.0), mul(M, EDGE_PUSH)))   # never up
        v_final = sub(v_final, mul(VANILLA_SLOPE, drop))
        # The preliminary surface keeps the old, unramped push: the aquifers read it, and with the
        # ramped push it sat above the real ground in the band, so rivers and lakes there came out dry.
        drop_psl = add(dmax(sub(HV, HB), 0.0), mul(M, EDGE_PUSH))
        psl_density = sub(psl_density, mul(PSL_SLOPE, drop_psl))
    # the depth the router and the aquifers share
    d = lerp(mask, vanilla_depth, depth)
    write(f'{NS}/worldgen/density_function/{settings_name}/depth.json', cache2d(d) if isinstance(d, str) else d)   # a file can't be a bare reference
    r['depth'] = f'{NS}:{settings_name}/depth'
    write(f'{NS}/worldgen/density_function/{settings_name}/preliminary_surface_level.json',
          make_preliminary(mask, sc, terrain_height, psl_density, psl_upper))
    PSL = f'{NS}:{settings_name}/preliminary_surface_level'
    write(f'{NS}/worldgen/density_function/{settings_name}/chunk_surface_level.json',
          {'type': 'minecraft:interpolated', 'cell_size_xz': 16, 'cell_size_y': 1, 'input': PSL})
    r['chunk_surface_level'] = f'{NS}:{settings_name}/chunk_surface_level'
    write(f'{NS}/worldgen/density_function/{settings_name}/final_density.json', union(mask, v_final, make_our_final(sc)))
    r['final_density'] = f'{NS}:{settings_name}/final_density'
    # aquifers: same surface, and their exclusion reads erosion/depth by vanilla name
    aq = ns['aquifers']
    aq['surface_level'] = PSL
    aq['exclusion'] = rename(rename(aq['exclusion'], 'minecraft:overworld/depth', f'{NS}:{settings_name}/depth'),
                             'minecraft:overworld/erosion', EROSION_FLOORED if mixed else f'{NS}:overworld/erosion')
    # spawn search reads these by name too
    ns['spawn_target'] = json.loads(json.dumps(ns['spawn_target'])
                                    .replace('"minecraft:overworld/continents"', f'"{CONTINENTS}"' if mixed else '"minecraft:overworld/continents"')
                                    .replace('"minecraft:overworld/erosion"', f'"{EROSION_FLOORED}"' if mixed else f'"{NS}:overworld/erosion"'))
    # material rules: vanilla's overworld chain with our mountain surface in front of vanilla's surface
    rule = copy.deepcopy(vanilla_rule)
    if mixed:
        rule['sequence'][SURFACE_IDX] = cond({'type': 'minecraft:above_preliminary_surface'},
                                             seq(cond(in_mountains, mountain_rules(True)), vanilla_surface_run))
    else:
        rule['sequence'][SURFACE_IDX] = cond({'type': 'minecraft:above_preliminary_surface'}, mountain_rules(False))
    write(f'{NS}/worldgen/material_rule/{settings_name}.json', rule)
    ns['material_rule'] = f'{NS}:{settings_name}'
    write(f'{NS}/worldgen/noise_settings/{settings_name}.json', ns)

    wp = load('worldgen/world_preset/normal.json')
    if mixed:
        biome_source = {'type': f'{NS}:mask',
                        'vanilla': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'},
                        'cold': f'{NS}:cold', 'temperate': f'{NS}:temperate', 'warm': f'{NS}:warm',
                        'cold_high': f'{NS}:cold_high', 'temperate_high': f'{NS}:temperate_high', 'warm_high': f'{NS}:warm_high',
                        'treeline_y': TREELINE,
                        'cold_below': BIOMES['cold'][1][1], 'warm_above': BIOMES['warm'][1][0], 'mask_threshold': 0.5,
                        # snow line: y = base + (temperature - frozen) * scale (+/- jitter); snow everywhere below 'frozen'
                        'snow_line_base': 64, 'snow_line_scale': 700, 'snow_line_frozen': -0.2, 'snow_line_jitter': 25}
    else:
        biome_source = {'type': 'minecraft:multi_noise', 'biomes': entries}
    wp['dimensions']['minecraft:overworld'] = {
        'type': f'{NS}:mountains',
        'generator': {'type': 'minecraft:noise', 'biome_source': biome_source, 'settings': f'{NS}:{settings_name}'}}
    write(f'{NS}/worldgen/world_preset/{preset_name}.json', wp)

build_variant('mountains', 'atlasmountains', mixed=True)
build_variant('mountains_all', 'all', mixed=False)
write('minecraft/tags/worldgen/world_preset/normal.json', {'replace': False, 'values': [f'{NS}:atlasmountains', f'{NS}:all']})
