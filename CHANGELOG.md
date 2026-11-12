# Changelog

## 0.4.5 (unreleased): sampler build, four looks in one world

Sammi on 0.4.4: "a bit too perfectly pyramidal", then ": /" at two screenshots where a peak
read as a tent. The shape had been swung from one extreme to the other four times in a day on
my guesses, and my renders could not show the differences that matter, so this build puts four
looks in one world for him to compare in the game itself:

- A plain pyramid (0.4.4), B light ribs, C strong ribs, D strong ribs and one or two shoulders.
  Every peak is given one of the four at random.
- Ribs: straight ridges and gullies running down each flat face, side by side, with sharp
  creases; they fade in below the summit.
- Shoulders: one or two smaller pyramids joined to the side of a peak.
- `/atlasmountains peaks` lists the nearest peak of each look with a teleport command.
- Temporary: `rib_depth` below zero switches the sampler on. To be replaced by the chosen look.

## 0.4.4 (unreleased): true pyramids with big flat sides

Sammi on 0.4.3: "they all seem rounded", "I want big flat side".

- Each peak is now 3 or 4 flat planes meeting at a point. In 0.4.3 the faces were made by
  bending a cone's angle, and my uneven spacing of the edges curved every face.
- Each peak is a solid of its own, built up from the land's level under its summit. Before, it
  was added on top of the land column by column, so every bump in the land showed on its faces.
- One face slope (44 degrees, give or take 12% face by face) from summit to foot; no extra
  steepening at the top, no gullies on the faces.
- Where a river meets a peak it runs through a V-shaped valley. (An interim version pressed the
  ground flat along the channel, which left a wall.)
- Where peaks generate and their spacing are unchanged.

## 0.4.3 (unreleased): pyramids that keep their angles

Sammi in the game on 0.4.2: "these aren't looking very angular, very smooth". Three causes, all
fixed here, and one over-correction on the way that he caught ("more star like than pyramidal").

- Peaks are no longer squashed. Each peak now reads the strength of mountain country once, at
  its summit, and is that fraction of its full height with its angles unchanged. Before, every
  column was multiplied by the local strength, which flattened most peaks into rounded humps
  (only 36% of peak ground was at full shape on his seed) and the clean cut then smoothed them.
- A peak that would be shorter than 60 blocks is left out.
- Pyramids: 3 or 4 fully flat sides (was 3 to 5, three-quarters flat). Five-sided peaks have
  edges too shallow to see and read as cones.
- Far fewer lines down each face (gully 0.03, was 0.12), and straighter, unevenly spaced edges.
- Where peaks generate and how far apart they are is unchanged, at Sammi's request.
- Tried and removed: ridges with V gullies between them and finer ribs on the faces. From above
  each peak was a pleated star.

## 0.4.2 (unreleased): shallower peaks

- Sammi chose the shallowest of three rendered options: 48 degrees at the summit easing to 35
  (0.4.1: 54 to 42). Summit heights are unchanged, so each peak is wider and neighbours meet
  higher up.

## 0.4.1 (unreleased): clean, flat-faced peaks

After Sammi's first look in-game (12 Nov 2026): the row of separate peaks seen from a distance was
right, but close up the summits were torn into shards, holes and blade-thin walls. He asked for
the even, angular slopes of the old mod on the new separate peaks, each peak straight-sided with a
sharp top.

- Clean faces: where a peak stands clear of the land, the ground is cut exactly at the peak's own
  height, block by block, with no 3-D roughness, and is solid for 12 blocks down. On a test peak
  99.1% of columns sit exactly at the intended height.
- Factor is vanilla's again. Raising it to 16 had let caves come within 3 blocks of the surface,
  which is where the cave mouths in the faces came from.
- Flat faces: between two ridges the face is now mostly flat, like the side of a pyramid, where
  it was a scooped hollow (`facet` 0.75).
- Sturdier: nearly straight sides, 54 degrees at the summit easing to 42 (was 62 to 39), and
  ridge lines wander less.
- Debug commands `dfmap` and `biomemap` write maps of a density function or of the biomes.

## 0.4.0 (unreleased): restart

- Started again from scratch (Sammi, 11 Nov 2026). The custom mountain generator, the continents,
  the mountain biomes, the world type, the debug commands and their tests are all removed; the
  world is vanilla again.
- New approach: work through vanilla's own `offset`, `factor` and `jaggedness` density functions.
  Two attempts at adjusting them (ridge lines, filled rings, wider spikes) moved the ground only
  about 4 blocks on a 250-block range, so they were dropped.
- Horn peaks, after Sammi's WorldPainter references: in low-erosion country away from the coast
  a field of separate pyramid peaks is added to the land
  (`atlasmountains:horns`): steep summits, flaring skirts, 3 to 5 ridges per peak, varied heights.
  River valleys are kept.
- The upper part of each peak takes vanilla's peak biome for its climate, so summits are rock,
  snow or ice, not forest.
- Vanilla's land is left at its own height under the peaks (a first build pressed it down to
  y 110). To make room the overworld is taller: build height y 320 to y 512, terrain ceiling
  y 256 to y 472-488.
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
