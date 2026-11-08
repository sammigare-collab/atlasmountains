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

    gradlew build          # jar in fabric/build/libs/atlasmountains-0.1.0.jar
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
valley floor 80, rock line 340, summit 600, sea level 79, snow line 540, caves stop at 300.

## World presets

- **Atlas Mountains** (`atlasmountains:atlasmountains`): a vanilla world whose mountains are replaced by ours.
  Vanilla never makes its own peaks (its erosion input is floored at -0.2). Mountain zones come from
  a noise stretched ~4:1 along the range direction (`MountainSettings.mask*`), faded out towards
  the coast using vanilla's continentalness. Inside a zone: our ranges, valleys and lakes at vanilla
  sea level; our three biomes and surface rules. Outside: untouched vanilla. Terrain, biomes and
  surface all read the same `atlasmountains:mountain_mask`.
- **Atlas Mountains (everywhere)** (`atlasmountains:all`): the whole world is mountain country; for testing.

Commands: `/atlasmountains info`, `/atlasmountains scan` (mask map around you, to the log),
`/atlasmountains heightmap <size> <step>` (surface heightmap PNG into `<world>/atlasmountains/`;
about 1.5 ms per column, so 6144 at step 16 takes a few minutes).

## Biomes

The preset uses its own three biomes (`atlasmountains:cold`, `temperate`, `warm`), chosen by the
rescaled temperature noise only. They are copies of vanilla snowy_taiga, meadow and savanna with
the vegetation step replaced by `atlasmountains:sparse_spruce` plus short grass, and no
freeze_top_layer (snow comes from the surface rule, top of the relief only). Ores, caves,
carvers and mob spawns are vanilla. The vanilla overworld biome list is not used because it put
jungles and forests on the flanks; nothing outside the preset is changed.

## Tested

- `gradlew build` (Loom 1.10.5, Gradle 8.14.3) and `:core:test` pass.
- Dedicated dev server (`gradlew :fabric:runServer`) loads the preset, erodes the four spawn
  regions in ~9 s, and generates ~6 chunks/s on a 2-core machine; 1,900 chunks read back from
  the region files match the cached heightmap and show the expected surface blocks.
- Not yet tested: the client, and the look from a valley floor.

## Known limitations

- Treeline: spruce grows on any grass (up to the rock line, ~y 340) rather than only the lowest
  25 to 35% of the relief; vanilla placement modifiers cannot filter by height.
- Vanilla cave carvers still cut the lower flanks (they run to y 180); the density-function caves
  stop at y 300.
- The `continents` and `erosion` climate noises are rescaled and shipped but no longer affect
  biome choice (temperature only), so they only matter if more biomes are added.
