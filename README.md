# atlasmountains

Minecraft 1.21.1 Fabric mod that generates horn-peak ridge terrain: linear ranges with rows of
pyramid summits, planar faces with gullies, broad flat valley floors with lakes. Only active in
the "Atlas Mountains" world type; the normal world type is untouched.

## Layout

- `core/` pure Java (JDK 21, no Minecraft dependency): heightmap model, erosion, region cache,
  diagnostic tool, unit tests.
- `fabric/` the mod. Compiles `core/` sources straight into the jar.
- `tools/build_datapack.py` generates every JSON under `fabric/src/main/resources/data/` from
  the vanilla 1.21.1 files in `tools/vanilla-1.21.1/`. Re-run it after changing a height
  parameter; do not hand-edit the generated JSON.

## Build

    gradlew build          # jar in fabric/build/libs/atlasmountains-0.2.0.jar
    gradlew :core:test     # core unit tests

Versions (verified 2026-11-07): Loom 1.10.5, Loader 0.20.1, Yarn 1.21.1+build.3, Fabric API 0.116.17+1.21.1.

## Diagnostic tool

Writes hillshade, contour, slope and zoom PNGs, a cross-section CSV and raw float grids to
`out/seed_<seed>/`, plus a 3x3-region un-eroded overview.

    gradlew :core:diag -Pseed=1 -Prx=0 -Prz=0
    # or without Gradle:
    javac -d build/classes $(find core/src/main/java -name '*.java')
    java -cp build/classes com.atlasmountains.core.diag.Diagnostics <seed> <regionX> <regionZ> [outDir] [overviewSpan] [raw] [name=value ...]

Any field of `MountainSettings` can be overridden on the command line, e.g.
`dropletsPerRegion=60000 saddleRounding=0`.

## In game

Create a world with world type "Atlas Mountains". Entering a new 2048x2048 region pauses for a
few seconds while it is eroded (then cached under `<world>/atlasmountains/`).
`/atlasmountains info` prints the region, whether it is cached, and the terrain height and slope
at your position.

Height parameters that live in JSON (`tools/build_datapack.py`) must match `MountainSettings`:
valley floor 64, rock line 168, summit 272 (chains scale this up to about 420), sea level 63, snow line 238,
caves stop at 150, treeline 100 (no trees in the mountain biomes above it), dimension -64..512.

## World presets

- **Atlas Mountains** (`atlasmountains:atlasmountains`): continents, vanilla terrain and biomes, our
  mountains in chains. Three layers, all in `core/`:
  - *Continents* (`NoiseContinents`, `MountainSettings.continent*`): a large fractal noise, Continents-mod
    style, replaces vanilla's continentalness: mostly ocean, big organic landmasses and islands, spawn
    on land. Mapped onto vanilla's continentalness scale by gradient-normalised distance to the shore,
    so beaches and the mountain coast fade are a fixed number of blocks. `PlateContinents` (Voronoi
    plates) is the alternative model (`noiseContinents=false`).
  - *Mountain chains* (`ChainLayout`, `MountainSettings.chain*`): each chain is a curved centreline of its
    own direction, 2.5 to 6 km long, carrying 1 to 4 spines of the usual design. Gaps between spines are
    usually tight (360 to 520 blocks), so neighbouring ranges meet back to back in a col; about 30% are
    wide (760 to 900) and leave the flat forested valley with lakes. About half the chains fork once or
    twice at 35 to 65 degrees. Heights vary: each chain has its own scale (0.85 to 1.2), outer spines are
    30% lower than the middle one (foothills), one dominant summit per chain stands 30% above its
    neighbours, and summits near a fork are up to 18% taller. Chains are only
    placed where the world's erosion noise is low (`chainErosionBelow`), so they cluster into the hilly
    provinces of the biome layer and leave the plains alone; the cell containing spawn always has one.
    The mask is the capsule around a chain's spines, fading over `chainEdgeWidth` at the sides and ends
    (the relief is scaled by the mask) and at the coast.
  - *Vanilla everywhere else*: terrain, rivers, caves, structures, biomes. Climate noises are slowed
    to Large Biomes scale (temperature, vegetation and erosion 4x vanilla); the ridge noise is left
    alone so rivers stay vanilla. Vanilla's own peaks are removed by flooring erosion at -0.1.
    Note: vanilla's terrain splines read `minecraft:overworld/continents` and `/erosion` by name, so
    the preset ships repointed copies (`vanilla_*` density functions) that read the mod's continents and
    the floored erosion; the router entries alone only affect biomes.
  Inside a chain: our ranges, valleys and lakes at vanilla sea level; our three biomes and surface rules,
  with treeless twins (`*_high`) above the treeline (y 100) so only the lower flanks are wooded.
  Terrain, biomes and surface all read the same `atlasmountains:mountain_mask`.
- **Atlas Mountains (everywhere)** (`atlasmountains:all`): the whole world is mountain country with the
  parallel-spine layout (`RangeLayout`); for testing the mountain design. Separate terrain cache
  (`<world>/atlasmountains/parallel/` vs `chains/`).

Commands: `/atlasmountains info` (layout, continentalness, mask, terrain height), `/atlasmountains scan`
(mask map around you to the log: `#` mountains, `+` edge, `.` land, `~` sea), `/atlasmountains continents <km>`
(PNG of continents and chains around you into `<world>/atlasmountains/`, quick), `/atlasmountains fields <km>
<erosionBelow> <ridgesAbove>` (PNG of where the world's erosion/ridge noises would put biome-level mountains),
`/atlasmountains heightmap <size> <step>` (surface heightmap PNG; about 1.5 ms per column).

Layout previews without the game: `java -cp build/classes com.atlasmountains.core.diag.ContinentMap <seed> [span] [blocksPerPixel] [cx] [cz] [out.png] [name=value...]`
with `chainLayout=true` (no erosion gating outside the game, so it shows every candidate chain).

## Biomes

The preset uses its own three biomes (`atlasmountains:cold`, `temperate`, `warm`), chosen by the
rescaled temperature noise only. They are copies of vanilla snowy_taiga, meadow and savanna with
the vegetation step replaced by `atlasmountains:sparse_spruce` plus short grass, and no
freeze_top_layer (snow comes from the surface rule, top of the relief only). Ores, caves,
carvers and mob spawns are vanilla. The vanilla overworld biome list is not used because it put
jungles and forests on the flanks; nothing outside the preset is changed.

## Tested (2026-11-08, chains + noise continents)

- `gradlew build` and `:core:test` pass.
- Dedicated dev server, seed 1, both presets: the mixed preset loads, chains follow the erosion
  noise, continentalness/beach/ocean/deep-ocean bands verified at probed positions, chunks generated
  over a chain and read back from the region files (summits to y 281, snow/rock/grass surface);
  the everywhere preset loads with the parallel layout and gives the same spawn height as before.
- Region generation (chains) about 3 s per 2048x2048 region on a 2-core machine; neighbouring regions
  are prefetched when a sample comes within 700 blocks of a region edge.
- Performance (2-core dev server, 256 chunks): vanilla 43 s, this preset 55 s on ordinary land and 65 s
  over a chain. The terrain and mask functions are wrapped in `cache_2d` (once per column); the dimension
  is 576 tall (was 1024), which is most of the remaining difference.
- Not tested: the client; the look of a fork and its taller summit from the ground; long play.

## Known limitations

- Treeline: spruce grows on any grass (up to the rock line, ~y 340) rather than only the lowest
  25 to 35% of the relief; vanilla placement modifiers cannot filter by height.
- Vanilla cave carvers still cut the lower flanks (they run to y 180); the density-function caves
  stop at y 300.
- Our three biomes are chosen by temperature only; humidity and erosion do not affect them.
- Inside a chain zone the land between and beside the ranges is the flat valley floor at y 64 (with
  lakes), not vanilla terrain; vanilla returns over the 500-block edge fade.
- Chain placement depends on the world's erosion noise, which the mod receives when the overworld
  loads; the standalone preview tool has no erosion and shows every candidate chain.
