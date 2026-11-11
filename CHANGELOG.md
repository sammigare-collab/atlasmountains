# Changelog

## Unreleased

- Side ridges are only slightly lower than the main ridge (0.9) with peaks of their own, end
  in one smaller last peak (0.65), and then a tail 200 to 320 blocks long runs down to nothing,
  so they fade into the land. Free ends of the main ridge get the same tail.
- One clear main peak per main ridge: one elbow stands 1.25 times the relief of the rest, the
  ridge climbing to it from both sides. Where that would pass y 308 the rest of the ridge is
  lowered to make the room.
- All mountains a little smaller: the height scale is 0.77 (was 0.88), and the snow line moved
  down with it (y 184, patches down to 144; were 205 and 160).
- Ridge ends and side ridges: a ridge now ends at a last peak and a steep face (end drop 0.8
  to 0.3) and side ridges stay at 0.85 to 0.6 of the main height (tip was 0.3), because the long
  low tails came out as smooth dark tongues with no peaks, some standing alone in the grass.
  Low rock is shaded as the foot of a big mountain (light) instead of getting the whole run to
  dark summit rock. `/atlasmountains heightmap` also writes the raw heights (.f32).
- Ridge lines after Sammi's sketch: a main ridge about 1500 blocks long (a free end runs on
  500 blocks past its site) in straight stretches with an elbow about every 280 blocks, side
  ridges leaving the elbows at 40 to 75 degrees with an elbow of their own and descending to a
  low tip, and a summit of its own height on every elbow and fork. Every spine in reach is
  unioned, not only the nearest two. Footprint threshold back to -0.62 (about 6% of land).
- No springs in the mountain biomes: they poured down steep faces as wide sheets of water.
- New `/atlasmountains spines <blocks>`: the ridge skeleton drawn over the un-eroded terrain.
- New mountain layout: a ridge skeleton instead of cell edges. Ranges are spines that wander and
  branch like a tree and never close into a ring, with free ends that taper down and legs (side
  ridges with small peaks) branching off the summits at varied angles. No more rings or
  triangles round a bowl, and no plain outer walls.
- Mountains stand on the land instead of in a hole: their relief is added on top of vanilla's
  surface, and the ground between ranges is vanilla's own (with its rivers).
- The foot of a mountain keeps the biome of the land around it; the mod's own mountain biomes
  start 30 blocks up.
- The grass flank runs out longer and more gently (`flankConcavity` 1.3 to 1.7), and the
  smallest massifs are less small (`fieldHeightVariation` 0.5 to 0.35).
- The footprint threshold is now -0.46 (was -0.62) to keep mountains at about 7 to 8% of land
  with the new layout. All of this changes the terrain: use a new world.
- Sharper mountains: rock faces 53 degrees instead of 49, crests falling 37 degrees between
  summits instead of 31, saddles half as rounded, and the scree smoothing now allows those
  angles instead of rounding every crest back to 49 degrees. Each summit stands as its own
  pyramid. Ranges are about 10 blocks narrower on each side.
- Mountain areas use the mod's own cold, temperate and warm biomes again (matching the
  surrounding biome was tried and dropped).
- No more straight walls where a mountain area ends. Outside the mountain cells, the distance to
  the nearest mountain ridge was measured only from the sample's own cell, so it jumped where
  that cell met a neighbour, and the flank stopped dead along a straight line. It now takes every
  nearby ridge into account, so flanks run on smoothly (some mountain areas reach a little
  further). Terrain changes, so region caches are versioned again.
- No more straight ditch around the mountain areas. At the outer edge of the blend, vanilla's
  ground was pushed down onto our surface, which sat 12 blocks under; in flat land that dropped
  it by 10 blocks or more in one step. Our surface now rises from further below and vanilla is
  only pushed further in, so the ground eases down over a few tens of blocks.
- Rivers and swamps outside the mountains have their water back. The inland lowland lift
  (about 4 blocks, plus 8 more in vanilla's flattest class) also lifted river beds and swamps,
  leaving rivers as dry green valleys and mangrove swamps as dry hills under solid canopy. The
  extra 8 blocks are gone, and the 4-block lift now fades out along river valleys and over the
  swamp class. Measured on seed -3604899424632918474: river columns in water went from 0-11% to
  38-88% (vanilla on the same seed: 37-93%); mangrove swamp from 0% to 13-21% (vanilla 45%),
  ground from about y 78 to y 65 (vanilla 62).
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
- Fixed straight strips of mountain biome running out of mountain areas into open country,
  and the walls and bare grass slopes along them: outside a mountain area, ridges are now
  measured to their real, finite length instead of as endless lines.
- Fixed the white fringe of snow along the edge of cold mountain areas: the cold mountain biome
  now only counts as freezing above about y 200, where the mountains' own snow starts.
- New debug commands: `/atlasmountains climate` (the biome and six climate values where you stand), `/atlasmountains biomes <km> <step>` (a map of the real biomes) and
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
