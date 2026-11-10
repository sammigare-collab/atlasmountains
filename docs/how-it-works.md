# How it works

Technical notes on the terrain generator. For installing and playing, see the [README](../README.md).

## Code layout

- `core/` pure Java (no Minecraft dependency): heightmap model, erosion, region cache, mask and
  continents, diagnostic tools, unit tests.
- `fabric/` the mod. Compiles `core/` sources straight into the jar (no nested jar).
- `tools/build_datapack.py` generates every JSON under `fabric/src/main/resources/data/` from the
  vanilla 26.3 files in `tools/vanilla-26.3/`, which `tools/extract_vanilla.py` copies out of the game jar (not committed).
  Re-run it after changing a parameter in it; do not hand-edit the generated JSON. The heights it
  shares with `MountainSettings` (valley floor, summit, rock fraction) are checked by
  `SettingsSyncTest`.

## World presets

**Atlas Mountains** (`atlasmountains:atlasmountains`), the one to play:

- *Continents*: `NoiseContinents` replaces vanilla's continentalness with a large fractal noise
  (Continents-mod style): mostly ocean, big organic landmasses, spawn on land. It is mapped onto
  vanilla's continentalness scale by distance to the shore, so beaches and the coast fade are a
  fixed number of blocks wide.
- *Mountain provinces* are vanilla's own mountain footprint: the world's erosion noise, read at
  `footprintErosionScale` (2.08x) the biome layer's frequency so provinces are smaller than other
  biomes, with ragged edges. The threshold (`erosionMountainBelow`, -0.62) is set so mountains
  cover about 9-10% of land, a little under twice vanilla's own mountain biomes (about 5.6%), in areas a
  kilometre or two across; check it with
  `/atlasmountains footprint <km> <threshold>` and `/atlasmountains biomes <km> <step>`.
- *The mountains* (`FieldLayout`): a domain-warped Voronoi diagram with cells about 420 blocks
  across. Its edges are the ridges, so a province fills with ranges back to back, meeting at
  three-way junctions, and wide cells leave a small enclosed valley floor with a lake. A cell is
  mountain when vanilla's footprint is on at its site, so a province edge follows whole cells and
  its last ridge stands at full height before fading into vanilla terrain. Summit heights are
  scaled by a slow 2-D field (`fieldHeightBoost` x (1 +/- `fieldHeightVariation`)), so some
  massifs are big and some small.
- *Vanilla everywhere else*: terrain, rivers, caves, structures, biomes. Temperature, vegetation
  and erosion noises run at half vanilla's frequency (biomes twice vanilla size); the ridge noise
  is left alone so rivers stay vanilla. Vanilla's own peaks are removed by flooring its erosion at
  -0.1, and its inland lowlands are lifted about 4 blocks to stop kilometre-wide pooling at sea level,
  except along river valleys and in the swamp class, which keep vanilla's height and their water.
  Vanilla's terrain splines read `minecraft:overworld/continents` and `/erosion` by name, so the
  preset ships repointed copies (`vanilla_*` density functions) that read the mod's continents and
  the floored erosion.
- *Edge blend*: in the mask's fade band the surface is a blend of our height and vanilla's
  jaggedness-free surface, with vanilla's 3-D density shifted to follow it, so vanilla hills ramp
  down into our valleys instead of standing in them.
- *Biomes and surface*: inside the mountains, below the snow line, the biome vanilla would put
  there, so a mountain matches the land around it; above the treeline (y 92) a treeless twin of
  that biome (`atlasmountains:high/<name>`, generated from vanilla's file with the tree features
  removed). Ordinary ground follows vanilla's rules for the biome, through a copy of them that
  knows the twins (`material_rule/vanilla_twins`). Above the snow line (y 205) our `cold` biome.
  Rivers, beaches and caves inside a mountain use our `cold`, `temperate` or `warm` (copies of
  snowy_taiga, meadow and savanna with sparse spruce) by temperature. Steep ground is rock that shades down each face (`atlasmountains:rock_gradient`,
  measured as a percentage of that face's height, from the valley floor to where its rock begins:
  the column's own summit, or the snow line if the summit is above it). Smooth basalt at the top,
  then deepslate, tuff, stone and andesite at the foot, mixed in small clumps and wobbled by a
  slow noise. Above the snow line the top few blocks are snow, not just the top one, so the sides
  of the voxel steps on steep summits are white too; below it, down to y 160, snow settles in patches
  (`atlasmountains:snow_cover`), more on gentle ground and less on steep faces. Outside the
  mountains, vanilla's biomes and surface.

**Atlas Mountains (everywhere)** (`atlasmountains:all`): the same field-layout ridges over the
whole world with no footprint, for looking at the mountain design itself. Its mask is 1
everywhere, so the surface rules (stone on steep faces, snow on top) treat every column as
mountain.

Each preset keeps its eroded terrain cache under `<world>/atlasmountains/mixed/` or
`.../everywhere/`. Entering a new 2048x2048 region pauses briefly while it is eroded. The cache
files are not keyed by settings: after changing a terrain parameter, test in a new world or delete
that folder.

## Heights

Valley floor y 64 (vanilla sea level 63), design summit y 232, rock line y 148, snow surface from
y 205 (patches down to y 160), density-function caves stop at y 135, treeline y 92, dimension -64..512. With the field
height scale typical summits are about y 212 and the tallest about y 286. The height parameters
live in `MountainSettings`; the surface and dimension ones in `tools/build_datapack.py`.

## Diagnostic tools (no game needed)

Region heightmap: hillshade, contour, slope and zoom PNGs, a cross-section CSV and raw float
grids to `out/seed_<seed>/`.

    gradlew :core:diag -Pseed=1 -Prx=0 -Prz=0

Continent and mask map:

    java -cp core/build/classes/java/main com.atlasmountains.core.diag.ContinentMap <seed> [span] [blocksPerPixel] [cx] [cz] [out.png] [name=value...]

Both accept `name=value` overrides of any `MountainSettings` field, e.g. `dropletsPerRegion=60000`.
Outside the game there is no erosion noise: the region tool draws ridges everywhere (like the
"everywhere" preset) and the continent map shows continents but no mountain provinces.

## Known limitations

- The mountain biomes are chosen by temperature only; humidity and erosion do not affect them.
- The standalone tools cannot reproduce the in-game mountain provinces (no erosion noise).

Older layout experiments (mountain chains, parallel ranges, Voronoi plate continents) were removed
from the tree; they are on the `archive/layout-experiments` branch.
