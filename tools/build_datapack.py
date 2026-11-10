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
"""
import json, os, copy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VAN = os.path.join(ROOT, 'tools', 'vanilla-26.3')
if not os.path.isdir(VAN):
    raise SystemExit('tools/vanilla-26.3 is missing: run python3 tools/extract_vanilla.py first')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- parameters (keep in step with MountainSettings) -------------------------------
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

# Our 2D functions are wrapped in cache_2d: the chunk filler walks each column top to bottom, so
# the eroded height and the mask are then computed once per column instead of once per block
# (576 times per column without it). flat_cache would be cheaper still but samples at 4-block
# resolution, which would terrace the surface.
def cache2d(f): return {'type': 'minecraft:cache', 'input': f}   # 26.x: one generic cache; our column functions fill a volume once per column anyway
TERRAIN = cache2d({'type': f'{NS}:terrain_height', 'mixed': False})                 # "everywhere" preset's cache
TERRAIN_FAST = cache2d({'type': f'{NS}:terrain_height', 'eroded': False, 'mixed': False})   # analytic model, no erosion; for biome depth only
MASK = cache2d({'type': f'{NS}:mountain_mask'})
SC = f'{NS}:overworld/sloped_cheese'
TERRAIN_MIXED = cache2d({'type': f'{NS}:terrain_height', 'mixed': True})       # raw eroded height, no mask: the blend below applies the mask
# Edge blend (mixed world). In the mask's fade band the surface is a true blend of heights,
# h = vanilla_h + mask * (ours - vanilla_h), where vanilla_h is vanilla's jaggedness-free surface
# found with find_top_surface. Vanilla's own 3D density is shifted down by VANILLA_SLOPE per block
# of (vanilla_h - h) so its surface follows the blend, and is faded out by the mask; so a
# vanilla hill inside a mountain cell ramps down to our valley floor across the band instead of
# standing in it as a wall, and nothing is hollow. VANILLA_SLOPE is vanilla's final density per
# block near the surface (about 0.1 to 0.2); erring high only pushes vanilla below the blend,
# where our term holds the surface.
VANILLA_SLOPE = 0.25
EDGE_SINK = 12             # blocks our surface sits under the blend at mask 0
EDGE_SINK_BAND = 0.15      # the sink fades in over this much of the mask at its outer edge
EDGE_PUSH = 12             # blocks vanilla is pushed under the blend at mask 1
PSL_SLOPE = 0.4            # the same for the jaggedness-free preliminary-surface density
TERRAIN_FAST_MASKED = cache2d({'type': f'{NS}:terrain_height', 'eroded': False, 'mask_relief': True, 'mixed': True})

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

# ---- climate noises ------------------------------------------------------------------
for name, octave in CLIMATE.items():
    n = load(f'worldgen/noise/{name}.json')
    n['base_octave'] = octave
    write(f'{NS}/worldgen/noise/{name}.json', n)

# ---- material noises -----------------------------------------------------------------
# Two small noises read by our own material rules (see StrataRule and SnowCoverCondition).
# 26.x noise files give the first octave, the octave count and base_amplitude, a normalising
# factor that depends only on the octave count; the values below are copied from vanilla noises
# with the same count (pillar.json for 2 octaves, surface.json for 3).
BASE_AMPLITUDE = {2: 0.9494731054427981, 3: 0.9381732587751008}
def material_noise(name, base_octave, octaves):
    write(f'{NS}/worldgen/noise/{name}.json',
          {'base_amplitude': BASE_AMPLITUDE[octaves], 'base_octave': base_octave, 'octave_count': octaves})
material_noise('strata_warp', -8, 2)    # wavelength about 256 blocks: beds rise and dip slowly along a ridge
material_noise('snow_patches', -4, 3)   # wavelength about 16 blocks, with finer detail: blobs a dozen blocks across

# ---- density functions ---------------------------------------------------------------
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
# The erosion noise runs 4x larger than vanilla's (Large Biomes scale), so its flattest, lowest
# class comes in kilometre-wide patches that sit at y 62-64 and pool everywhere. Vanilla's offset
# is lifted by LOWLAND_LIFT (in offset units, 128 blocks each) from the coast band inland so those
# flats stand a few blocks clear of sea level; the shore and the sea are untouched.
LOWLAND_LIFT = 0.03        # everywhere inland: about 4 blocks
SWAMP_LIFT = 0.06          # extra in vanilla's flattest class (erosion above ~0.45), which otherwise sits under water for kilometres: about 8 blocks
LOWLAND_RAMP = add(
    {'type': 'minecraft:spline', 'spline': {'coordinate': CONTINENTS, 'points': [
        {'location': -0.05, 'value': 0.0, 'derivative': 0.0},
        {'location': 0.25, 'value': LOWLAND_LIFT, 'derivative': 0.0}]}},
    mul({'type': 'minecraft:spline', 'spline': {'coordinate': CONTINENTS, 'points': [
            {'location': -0.05, 'value': 0.0, 'derivative': 0.0},
            {'location': 0.15, 'value': 1.0, 'derivative': 0.0}]}},
        {'type': 'minecraft:spline', 'spline': {'coordinate': f'{NS}:overworld/erosion', 'points': [
            {'location': 0.35, 'value': 0.0, 'derivative': 0.0},
            {'location': 0.6, 'value': SWAMP_LIFT, 'derivative': 0.0}]}}))
for n in VANILLA_SHAPE:
    body = repoint(load(f'worldgen/density_function/overworld/{n}.json'))
    if n == 'offset':
        body = add(body, LOWLAND_RAMP)
    write(f'{NS}/worldgen/density_function/overworld/vanilla_{n}.json', body)

NOODLE = f'{NS}:overworld/caves/noodle'
noodle = load('worldgen/density_function/overworld/caves/noodle.json')
gate_interpolated(noodle, VANILLA_TOP, 0.0)      # its own outer range_choice already returns 64 above y 321
write(f'{NS}/worldgen/density_function/overworld/caves/noodle.json', noodle)

# ---- vanilla final density, trimmed to vanilla's height -------------------------------------
# 26.x: final_density is its own file, add(min(squeeze(interpolated(...)), noodle), beardifier).
vanilla_ns = load('worldgen/noise_settings/overworld.json')
vanilla_final = rename(load('worldgen/density_function/overworld/final_density.json'), 'minecraft:overworld/caves/noodle', NOODLE)
fix_top(vanilla_final)
gate_interpolated(vanilla_final, VANILLA_TOP, -64.0)   # air above vanilla's own ceiling, computed for free

def make_our_final(sc):
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

# ---- material rules (26.x name for surface rules) ------------------------------------------
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

def layer(name, weight, max_thickness):
    return {'state': name, 'weight': weight, 'max_thickness': max_thickness}
# Exposed rock: horizontal beds of several stone types (atlasmountains:strata, see StrataRule).
# Grey stone and andesite carry the faces; tuff and dripstone add darker and warmer beds; thin
# calcite seams give the pale lines that make strata read from a distance. Weights are relative.
rock = {'type': f'{NS}:strata', 'min_thickness': 1, 'period': 192, 'warp': 12.0, 'speckle': 0.18, 'layers': [
    layer('minecraft:stone',           5.0, 6),
    layer('minecraft:andesite',        3.0, 5),
    layer('minecraft:tuff',            2.0, 4),
    layer('minecraft:dripstone_block', 1.0, 2),
    layer('minecraft:calcite',         0.8, 2),
]}
def snow_cover(mixed):   # patchy snow on ledges below the snow line (see SnowCoverCondition)
    return {'type': f'{NS}:snow_cover', 'snow_line': SNOW_LINE, 'lowest': SNOW_PATCH_LOWEST,
            'ledge_slope': 38.0, 'steep_penalty': 0.45, 'mixed': mixed}
def mountain_rules(mixed): return seq(
    cond(top_block, seq(
        cond(snow, block('minecraft:snow_block')),
        cond(snow_cover(mixed), block('minecraft:snow_block')),
        cond(steep(mixed), rock),
        cond(above_water, block('minecraft:grass_block')),
        block('minecraft:gravel'),
    )),
    cond(surface_layer, seq(
        cond(steep(mixed), rock),
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

# ---- biomes: three climate bands, vanilla copies with vegetation replaced by sparse spruce ----
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
    write(f'{NS}/worldgen/biome/{ours_id}.json', b)
    # treeless twin for above the treeline (biomes are 3D, so the biome source can switch by height)
    high = copy.deepcopy(b)
    high['features'][9] = VEGETATION[1:]
    write(f'{NS}/worldgen/biome/{ours_id}_high.json', high)
    entries.append({'biome': f'{NS}:{ours_id}', 'parameters': {
        'temperature': trange, 'humidity': [-1.0, 1.0], 'continentalness': [-1.0, 1.0],
        'erosion': [-1.0, 1.0], 'weirdness': [-1.0, 1.0], 'depth': [-1.0, 1.0], 'offset': 0.0}})

# ---- dimension type ------------------------------------------------------------------------
dt = load('dimension_type/overworld.json')
dt['min_y'] = DIM_MIN_Y
dt['height'] = DIM_HEIGHT
dt['logical_height'] = DIM_HEIGHT
write(f'{NS}/dimension_type/mountains.json', dt)

# ---- noise settings + world preset, per variant ---------------------------------------------
def build_variant(settings_name, preset_name, mixed):
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
        write(f'{NS}/worldgen/density_function/{settings_name}/blend_height.json',
              cache2d(add(HV, mul(MASK, sub(TERRAIN_MIXED, HV)))))
        HB = f'{NS}:{settings_name}/blend_height'
        # Our surface sits EDGE_SINK below the blend where the mask is 0 (so vanilla's real 3D surface,
        # which can differ from its jaggedness-free estimate by several blocks, is never undercut by
        # a plate at the estimate: that made a wall along the mask's outer contour). Vanilla is pushed
        # EDGE_PUSH below the blend where the mask is 1, so it leaves no step at that contour.
        # the sink only acts in the outermost EDGE_SINK_BAND of the mask (ours = the blend elsewhere,
        # so the band never dips under vanilla's own ground, let alone sea level)
        sink_t = {'type': 'minecraft:clamp', 'input': sub(1.0, mul(MASK, 1.0 / EDGE_SINK_BAND)), 'min': 0.0, 'max': 1.0}
        ours_h = sub(HB, mul(sink_t, EDGE_SINK))
        write(f'{NS}/worldgen/density_function/{settings_name}/sloped_cheese.json',
              mul(DENSITY_PER_BLOCK, sub(ours_h, 'minecraft:y')))
        sc = f'{NS}:{settings_name}/sloped_cheese'
        terrain_height = HB
        drop = add(dmax(sub(HV, HB), 0.0), mul(MASK, EDGE_PUSH))   # never up: where ours is higher, ours simply wins
        v_final = sub(v_final, mul(VANILLA_SLOPE, drop))
        psl_density = sub(psl_density, mul(PSL_SLOPE, drop))
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
