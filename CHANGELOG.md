# Changelog

## Unreleased

- Steep rock now shades down each face, measured from that face's own peak: dark smooth basalt
  where the rock leaves the snow (or at the top of a peak too low for snow), then deepslate,
  tuff and stone, pale andesite at the foot, blended in small clumps. Replaces the large tuff
  and packed-mud blotches.
- Snow above the snow line is a few blocks deep, so steep summits read white rather than dark
  rock with white specks.
- Ridge junctions rise into their own summit, taller than the peaks around them
  (`junctionPeakBoost`). This changes the terrain: region caches are versioned, so a world made
  with an older build regenerates its regions rather than mixing two terrain models.
- Below the snow line, snow settles in patches on ledges and gentle ground down to y 160, so the
  white of the summits frays downwards instead of ending at one height.
- Mountains now come as separate areas a kilometre or two across instead of a web over the
  land: about 9-10% of land (was about 40%; vanilla's own mountain biomes take about 5.6%).
- New debug commands: `/atlasmountains biomes <km> <step>` (a map of the real biomes) and
  `/atlasmountains footprint <km> <threshold>` (mountain share and map for another threshold).
- Much more detailed comments throughout the code.

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
