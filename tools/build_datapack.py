#!/usr/bin/env python3
"""
Builds the mod's worldgen JSON from the vanilla 1.21.1 data files in tools/vanilla-1.21.1
(copied from the mcmeta mirror of the Minecraft jar). Run from the project root:

    python3 tools/build_datapack.py

Everything the mod ships under data/ is generated here, so a change to vanilla or to a
parameter below is a re-run, not a hand edit. The height parameters must match the defaults
in MountainSettings (valley floor, rock line, summit height).

Two world presets are produced:
  atlasmountains:atlasmountains  "Atlas Mountains"              Voronoi plate continents, our ranges in the mountain zones, vanilla elsewhere
  atlasmountains:all             "Atlas Mountains (everywhere)" the whole world is mountain country (testing)
"""
import json, os, copy

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
VAN = os.path.join(ROOT, 'tools', 'vanilla-1.21.1')
OUT = os.path.join(ROOT, 'fabric', 'src', 'main', 'resources', 'data')
NS = 'atlasmountains'

# ---- parameters (keep in step with MountainSettings) -------------------------------
VALLEY_FLOOR = 64          # MountainSettings.valleyFloorHeight (one above vanilla sea level)
SUMMIT = 272               # MountainSettings.summitHeight
ROCK_LINE = 168            # MountainSettings.rockLineHeight() = summit - 0.5 * relief
SEA_LEVEL = 63             # vanilla; the lake surface (floor - 1) is the same height
SNOW_LINE = 238            # top ~15-20% of the relief
SNOW_JITTER_MULT = 3       # surface_depth_multiplier on the y_above condition (jitters by a few blocks)
ROCK_SLOPE_DEG = 35        # steeper than this = stone
CAVE_TOP = 150             # no density-function cave carving at or above this y (mountain zones)
DIM_MIN_Y = -64
DIM_HEIGHT = 1024          # build limit 960
DENSITY_PER_BLOCK = 0.15   # sloped_cheese = (terrain_height - y) * this; vanilla caves expect ~1.56 ten blocks down
VANILLA_EROSION_FLOOR = -0.1   # vanilla erosion is never lower than this, so vanilla makes no peaks of its own
VANILLA_TOP = 320          # vanilla's own build height; its 3D noise is skipped above this (see gate_interpolated)

CLIMATE = {   # firstOctave: vanilla verified from the data files -> target. Each step of -1 doubles the wavelength.
    'temperature': -14,      # vanilla -10, sixteen times slower: a climate band is many kilometres
    'vegetation': -12,       # vanilla -8, sixteen times slower
    'continentalness': -10,  # vanilla -9, twice vanilla; only the "everywhere" preset still uses this noise (the mixed preset uses the plates)
    'erosion': -12,          # vanilla -9, eight times slower: flat or hilly provinces kilometres across
}
# Biome size: with these octaves a vanilla biome spans kilometres. The ridge (weirdness) noise is
# left at vanilla so rivers keep their vanilla spacing and shape.

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

TERRAIN = {'type': f'{NS}:terrain_height', 'chains': False}                 # "everywhere" preset: parallel spines
TERRAIN_FAST = {'type': f'{NS}:terrain_height', 'eroded': False, 'chains': False}   # analytic model, no erosion; for biome depth only
MASK = {'type': f'{NS}:mountain_mask'}
SC = f'{NS}:overworld/sloped_cheese'
SC_MASKED = f'{NS}:overworld/sloped_cheese_masked'      # relief faded by the mask (mixed world)
TERRAIN_MASKED = {'type': f'{NS}:terrain_height', 'mask_relief': True, 'chains': True}     # mixed preset: chains
TERRAIN_FAST_MASKED = {'type': f'{NS}:terrain_height', 'eroded': False, 'mask_relief': True, 'chains': True}

def mul(a, b): return {'type': 'minecraft:mul', 'argument1': a, 'argument2': b}
def add(a, b): return {'type': 'minecraft:add', 'argument1': a, 'argument2': b}
def dmin(a, b): return {'type': 'minecraft:min', 'argument1': a, 'argument2': b}
def dmax(a, b): return {'type': 'minecraft:max', 'argument1': a, 'argument2': b}

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
    argument is 0, so outside the zones our terrain (and its region erosion) is never
    evaluated, and inside them vanilla is not."""
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
            inner = node['argument']
            gate_interpolated(inner, gate_y, fallback)
            node['argument'] = {'type': 'minecraft:range_choice', 'input': 'minecraft:y',
                                'min_inclusive': -1000000.0, 'max_exclusive': float(gate_y),
                                'when_in_range': inner, 'when_out_of_range': fallback}
            return
        for v in node.values(): gate_interpolated(v, gate_y, fallback)
    elif isinstance(node, list):
        for v in node: gate_interpolated(v, gate_y, fallback)

def fix_top(node):
    """the vanilla 'air above the top' gradient (240..256) moved near our build limit"""
    if isinstance(node, dict):
        if node.get('type') == 'minecraft:y_clamped_gradient' and node.get('from_y') == 240 and node.get('to_y') == 256:
            node['from_y'] = DIM_MIN_Y + DIM_HEIGHT - 60
            node['to_y'] = DIM_MIN_Y + DIM_HEIGHT - 20
        for v in node.values(): fix_top(v)
    elif isinstance(node, list):
        for v in node: fix_top(v)

# ---- climate noises ------------------------------------------------------------------
for name, octave in CLIMATE.items():
    n = load(f'worldgen/noise/{name}.json')
    n['firstOctave'] = octave
    write(f'{NS}/worldgen/noise/{name}.json', n)

# ---- density functions ---------------------------------------------------------------
write(f'{NS}/worldgen/density_function/overworld/sloped_cheese.json',
      mul(DENSITY_PER_BLOCK, add(TERRAIN, mul(-1.0, 'minecraft:y'))))
write(f'{NS}/worldgen/density_function/overworld/sloped_cheese_masked.json',
      mul(DENSITY_PER_BLOCK, add(TERRAIN_MASKED, mul(-1.0, 'minecraft:y'))))
# biome depth inside the zones: 0 at the terrain surface, +1 at 128 blocks down
write(f'{NS}/worldgen/density_function/overworld/depth.json',
      mul(1.0 / 128, add(TERRAIN_FAST, mul(-1.0, 'minecraft:y'))))
write(f'{NS}/worldgen/density_function/overworld/depth_masked.json',
      mul(1.0 / 128, add(TERRAIN_FAST_MASKED, mul(-1.0, 'minecraft:y'))))
# Continentalness (noise continents by default, plates optional), once per column. The vanilla terrain splines (offset, factor, jaggedness)
# read "minecraft:overworld/continents" and "minecraft:overworld/erosion" BY NAME, not the router
# entries, so the mixed preset ships its own copies of those splines below pointing at these two.
PLATES = f'{NS}:overworld/continents'
write(f'{NS}/worldgen/density_function/overworld/continents.json',
      {'type': 'minecraft:flat_cache', 'argument': {'type': f'{NS}:continents'}})
erosion_df = rename(load('worldgen/density_function/overworld/erosion.json'), 'minecraft:erosion', f'{NS}:erosion')
write(f'{NS}/worldgen/density_function/overworld/erosion.json', erosion_df)
EROSION_FLOORED = f'{NS}:overworld/erosion_floored'
write(f'{NS}/worldgen/density_function/overworld/erosion_floored.json', dmax(VANILLA_EROSION_FLOOR, f'{NS}:overworld/erosion'))

# Vanilla's terrain-shape functions, re-pointed at the plates and the floored erosion. Names are
# prefixed "vanilla_" because our own sloped_cheese/depth live beside them.
VANILLA_SHAPE = ['offset', 'depth', 'factor', 'jaggedness', 'sloped_cheese']
def repoint(obj):
    obj = rename(obj, 'minecraft:overworld/continents', PLATES)
    obj = rename(obj, 'minecraft:overworld/erosion', EROSION_FLOORED)
    for n in VANILLA_SHAPE:
        obj = rename(obj, f'minecraft:overworld/{n}', f'{NS}:overworld/vanilla_{n}')
    return obj
for n in VANILLA_SHAPE:
    write(f'{NS}/worldgen/density_function/overworld/vanilla_{n}.json', repoint(load(f'worldgen/density_function/overworld/{n}.json')))

NOODLE = f'{NS}:overworld/caves/noodle'
noodle = load('worldgen/density_function/overworld/caves/noodle.json')
gate_interpolated(noodle, VANILLA_TOP, 0.0)      # its own outer range_choice already returns 64 above y 321
write(f'{NS}/worldgen/density_function/overworld/caves/noodle.json', noodle)

# ---- vanilla final density, trimmed to vanilla's height -------------------------------------
vanilla_ns = load('worldgen/noise_settings/overworld.json')
vanilla_final = rename(copy.deepcopy(vanilla_ns['noise_router']['final_density']), 'minecraft:overworld/caves/noodle', NOODLE)
fix_top(vanilla_final)
gate_interpolated(vanilla_final, VANILLA_TOP, -64.0)   # air above vanilla's own ceiling, computed for free

def make_our_final(sc):
    ours = rename(copy.deepcopy(vanilla_ns['noise_router']['final_density']), 'minecraft:overworld/sloped_cheese', sc)
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
                assert wi['type'] == 'minecraft:min' and wi['argument1'] == sc
                node['when_in_range'] = y_gate(wi['argument2'], 64.0)          # 5 * entrances only
                node['when_out_of_range'] = y_gate(node['when_out_of_range'], 64.0)
                return
            for v in node.values(): gate_caves(v)
        elif isinstance(node, list):
            for v in node: gate_caves(v)
    gate_caves(ours)
    assert ours['type'] == 'minecraft:min' and ours['argument2'] == NOODLE
    caves_part = ours['argument1']                     # squeeze(0.64 * interpolated(...))
    exact_part = {'type': 'minecraft:squeeze', 'argument': mul(0.64, sc)}
    return dmin(dmin(exact_part, caves_part), y_gate(NOODLE, 64.0))

# ---- surface rules ---------------------------------------------------------------------
def block(name, props=None):
    b = {'type': 'minecraft:block', 'result_state': {'Name': name}}
    if props: b['result_state']['Properties'] = props
    return b
def cond(c, then): return {'type': 'minecraft:condition', 'if_true': c, 'then_run': then}
def seq(*rules): return {'type': 'minecraft:sequence', 'sequence': list(rules)}
def noise_between(lo, hi):
    return {'type': 'minecraft:noise_threshold', 'noise': 'minecraft:surface', 'min_threshold': lo, 'max_threshold': hi}

top_block = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': False, 'secondary_depth_range': 0}
surface_layer = {'type': 'minecraft:stone_depth', 'offset': 0, 'surface_type': 'floor', 'add_surface_depth': True, 'secondary_depth_range': 0}
above_water = {'type': 'minecraft:water', 'offset': 0, 'surface_depth_multiplier': 0, 'add_stone_depth': False}
snow = {'type': 'minecraft:y_above', 'anchor': {'absolute': SNOW_LINE}, 'surface_depth_multiplier': SNOW_JITTER_MULT, 'add_stone_depth': False}
def steep(chains): return {'type': f'{NS}:slope_above', 'degrees': ROCK_SLOPE_DEG, 'chains': chains}
in_mountains = {'type': f'{NS}:in_mountains', 'threshold': 0.5}

rock = seq(   # warm rock palette: a simple noise-selected mix of three vanilla blocks
    cond(noise_between(-10.0, -0.25), block('minecraft:tuff')),
    cond(noise_between(0.3, 10.0), block('minecraft:packed_mud')),
    block('minecraft:stone'),
)
def mountain_rules(chains): return seq(
    cond(top_block, seq(
        cond(snow, block('minecraft:snow_block')),
        cond(steep(chains), rock),
        cond(above_water, block('minecraft:grass_block', {'snowy': 'false'})),
        block('minecraft:gravel'),
    )),
    cond(surface_layer, seq(
        cond(steep(chains), rock),
        block('minecraft:dirt'),
    )),
)
vanilla_sr = vanilla_ns['surface_rule']
assert vanilla_sr['type'] == 'minecraft:sequence' and len(vanilla_sr['sequence']) == 3
vanilla_surface = vanilla_sr['sequence'][1]
assert vanilla_surface['if_true']['type'] == 'minecraft:above_preliminary_surface'

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
            'state': {'Name': 'minecraft:spruce_sapling', 'Properties': {'stage': '0'}}}},
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
    ns['noise'] = {'min_y': DIM_MIN_Y, 'height': DIM_HEIGHT, 'size_horizontal': 1, 'size_vertical': 2}
    ns['sea_level'] = SEA_LEVEL
    r = ns['noise_router']
    r['temperature'] = rename(r['temperature'], 'minecraft:temperature', f'{NS}:temperature')
    r['vegetation'] = rename(r['vegetation'], 'minecraft:vegetation', f'{NS}:vegetation')
    continents = rename(load('worldgen/density_function/overworld/continents.json'), 'minecraft:continentalness', f'{NS}:continentalness')
    if mixed:
        # Voronoi plate continents replace vanilla's continentalness; erosion floored so vanilla never makes its own peaks.
        # Biomes (router) and terrain (the vanilla_* shape copies) now read the same two functions.
        r['continents'] = PLATES
        r['erosion'] = EROSION_FLOORED
    else:
        # clamp's "input" accepts an inline function only (DensityFunction.CODEC, not FUNCTION_CODEC)
        r['continents'] = {'type': 'minecraft:clamp', 'input': continents, 'min': -0.1, 'max': 1.0}  # never ocean/beach
        r['erosion'] = f'{NS}:overworld/erosion'
    sc = SC_MASKED if mixed else SC
    depth = f'{NS}:overworld/depth_masked' if mixed else f'{NS}:overworld/depth'
    vanilla_depth = copy.deepcopy(vanilla_ns['noise_router']['depth'])
    vanilla_initial = copy.deepcopy(vanilla_ns['noise_router']['initial_density_without_jaggedness'])
    fix_top(vanilla_initial)
    v_final = vanilla_final
    if mixed:
        vanilla_depth, vanilla_initial, v_final = repoint(vanilla_depth), repoint(vanilla_initial), repoint(copy.deepcopy(vanilla_final))
    r['depth'] = lerp(mask, vanilla_depth, depth)
    r['initial_density_without_jaggedness'] = union(mask, vanilla_initial, sc)
    r['final_density'] = union(mask, v_final, make_our_final(sc))
    sr = copy.deepcopy(vanilla_sr)
    if mixed:
        sr['sequence'][1] = cond({'type': 'minecraft:above_preliminary_surface'},
                                 seq(cond(in_mountains, mountain_rules(True)), vanilla_surface['then_run']))
    else:
        sr['sequence'][1] = cond({'type': 'minecraft:above_preliminary_surface'}, mountain_rules(False))
    ns['surface_rule'] = sr
    write(f'{NS}/worldgen/noise_settings/{settings_name}.json', ns)

    wp = load('worldgen/world_preset/normal.json')
    if mixed:
        biome_source = {'type': f'{NS}:mask',
                        'vanilla': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'},
                        'cold': f'{NS}:cold', 'temperate': f'{NS}:temperate', 'warm': f'{NS}:warm',
                        'cold_below': BIOMES['cold'][1][1], 'warm_above': BIOMES['warm'][1][0], 'mask_threshold': 0.5}
    else:
        biome_source = {'type': 'minecraft:multi_noise', 'biomes': entries}
    wp['dimensions']['minecraft:overworld'] = {
        'type': f'{NS}:mountains',
        'generator': {'type': 'minecraft:noise', 'biome_source': biome_source, 'settings': f'{NS}:{settings_name}'}}
    write(f'{NS}/worldgen/world_preset/{preset_name}.json', wp)

build_variant('mountains', 'atlasmountains', mixed=True)
build_variant('mountains_all', 'all', mixed=False)
write('minecraft/tags/worldgen/world_preset/normal.json', {'replace': False, 'values': [f'{NS}:atlasmountains', f'{NS}:all']})
