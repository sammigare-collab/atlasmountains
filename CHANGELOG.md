# Changelog

## 0.4.0 (unreleased): restart

- Started again from scratch (Sammi, 11 Nov 2026). The custom mountain generator, the continents,
  the mountain biomes, the world type, the debug commands and their tests are all removed; the
  world is vanilla again.
- New approach: work through vanilla's own `offset`, `factor` and `jaggedness` density functions.
  Two attempts at adjusting them (ridge lines, filled rings, wider spikes) moved the ground only
  about 4 blocks on a 250-block range, so they were dropped.
- Horn peaks, after Sammi's WorldPainter references: in low-erosion country away from the coast
  the land is pressed down to about y 110 and a field of separate pyramid peaks is added
  (`atlasmountains:horns`): steep summits, flaring skirts, 3 to 5 ridges per peak, varied heights.
  River valleys are kept. The terrain ceiling moves from y 256 to y 296-312.
- Debug commands: `mountains`, `heightmap`, `df`. Shape prototype and viewer under `tools/`.

## 0.3.0 (2026-11-10)

Minecraft 26.3.

- Ported to Minecraft 26.3, Fabric Loader 0.20.1, Fabric API 0.165.2 and Java 25.
- Mountains now fill vanilla's own mountain footprint: a network of ridges meeting at junctions,
  with small enclosed valleys where ridges part.
- Smaller, rougher mountain provinces; biomes elsewhere at twice vanilla size.
- Mountains about 20% lower (design summit y 232), with summit heights varying between massifs.
- Snow starts at a climate-dependent snow line instead of a hard biome edge.
- Province edges blend into vanilla terrain by height, so there are no walls, flooded rings or
  sunken plates at the edge.
- Faster region generation.
- Removed the older layout experiments (they are on the `archive/layout-experiments` branch).

## 0.2.0 (2026-11-08)

Minecraft 1.21.1.

- Continents from a large fractal noise: big organic landmasses and islands, spawn on land.
- Branching mountain chains placed inside mountain provinces, with foothills, dominant summits and
  taller peaks at junctions.
- Treeless biomes above the treeline; vanilla's own peaks removed.

## 0.1.0

Minecraft 1.21.1. First prototype: parallel horn-peak ranges over the whole world.
