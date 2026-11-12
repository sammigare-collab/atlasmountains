# Changelog

## 0.4.8 (unreleased): straight river walls, darker rock, peak biome on the whole peak

Sammi's in-game look at 0.4.7 (six screenshots, "........"): thin fins, rounded towers and domes,
whole mountainsides in light grey, and one peak that was right (dark rock under a ragged cap).
Where peaks generate and their spacing are unchanged.

What was wrong, measured on his seed (-3550591538524093002):
- The fins, towers and domes came from the rule that keeps peaks out of rivers. It limited a peak's
  height by vanilla's weirdness noise, so its surface was lumpy and anywhere from 22 to 71 degrees,
  and it decided the ground on 70% of the peak area in a 384-block square, taking a median 83
  blocks off. 0.4.6 had the same shapes under its snow. (My first guess, vanilla's raised terrain
  ceiling, was wrong: no vanilla land there is above y 203.)
- The light grey came from measuring the rock gradient from the land under each summit, which is
  often 100 blocks above the valley: everything below that was the lightest rock.

Changes:
- River walls. Beside a river a piece of a peak may stand no higher above the land than its real
  distance from the river channel times a slope, so a river runs in a straight-sided V between
  flat walls. The slope is 44 degrees (the peaks' own face angle) unless the piece's summit stands
  too close to a river to be reached at that angle; then it is as steep as that summit needs, up
  to 60 degrees. A first build with 44 degrees everywhere cut the top off 7 of the 9 summits in a
  test square, including the one peak he had liked.
- Rock gradient measured from y 70 (about the valley floors) up to each peak's snow cap, with the
  dark rocks starting lower: andesite and stone only in the bottom fifth or so, then tuff,
  deepslate, smooth basalt.
- Peak biome on the whole of every peak. The erosion the biomes see is now pushed down on peaks
  as weirdness already was. Before, peaks near the coast or in only just low enough erosion were
  plains, forest, meadow or savanna: no snow caps, stray trees, and (found in testing) a savanna
  village built up a rock wall. In warm country a peak's cap is stony peaks, not badlands.

Checked on a dev server on his seed, from real generated chunks, at his screenshot positions:
- The fins and slabs in his screenshots 4, 5 and 6 are gone; the peak in screenshot 2 keeps its
  pointed top and cap. `0.4.7 vs 0.4.8` renders were sent with the build.
- Summits in a 768-block square that do not sit on a river: 9. Cut by more than 3 blocks: 3 in
  0.4.8 (by 29, 41 and 56 blocks; their summits are 30 to 68 blocks from a river), 5 in 0.4.7, 1 in
  0.4.6. Nine more shoulder summits sit on a river channel and are removed in every version.
- Not less steep overall: ground steeper than 65 degrees is 19% of the peak area in both 0.4.7
  and 0.4.8. On wall ground the middle steepness is 64 degrees (0.4.7: 60), the steepest tenth is
  above 72 (0.4.7: 76). The walls are flat now, not gentle.
- Rock on peaks: andesite 1%, stone 9%, tuff 19%, deepslate 32%, smooth basalt 33%, snow 6%
  (0.4.7: 53, 15, 12, 9, 8, 4).
- Biome on peak columns: jagged, frozen or stony peaks on over 99% of one square and 91% of a
  coastal one (the rest is ocean biome under the outer skirts).
- The peak shape away from rivers is identical to 0.4.7 (compared column by column over the square).
- Generating the same two test squares took 3 min 36 s against 3 min 26 s for 0.4.7.

## 0.4.7 (unreleased): the look of Sammi's dark-rock valley screenshot, and more distinct peaks

Sammi on 12 Nov 2026, with a screenshot of the old mod (two big bare rock slopes, dark rock under
ragged snow caps, lighter rock lower down, grass only on the valley floor): "i want the mountains to
look like this ... and add more distinct peaks". Where peaks generate and their spacing are
unchanged. Look D (ribs and shoulders) and the skirt are kept.

Surface (new):
- Bare rock on every peak from foot to summit, shading up each peak's own face: andesite and stone
  at the foot, then tuff, deepslate and smooth basalt just under the snow (the gradient from his
  reference, measured against the height of each peak or shoulder).
- A snow cap on the top 28% of each peak and shoulder in cold and temperate country, with a ragged
  lower edge and a few bare holes. In warm country only a patchy dusting on the top 6%.
- Below its cap a peak's biome is vanilla's stony peaks whatever the climate, so no snow layers, ice
  sheets or trees there. Peak biomes start 4 blocks above the land (was 35), so the dirt-and-trees
  collar round each foot is gone. Vanilla's own crest land between the peaks gets the same bare-rock
  biome, so its snow and blue ice sheets are gone too.
- A river that cuts a peak runs through a gentler V (about 38 degrees, was about 55), so no huge smooth wall.

Shape:
- More distinct peaks: two or three shoulder peaks per peak (was one or two), each standing out in
  front of the middle of one of the main peak's faces, 0.55 to 0.85 of its height away, 0.65 of its
  height give or take a quarter. In 0.4.6 they stood closer in and in any direction and were mostly
  swallowed by the main peak.
- Pointier summits: the top 18% of each peak is 1.5 times as steep as the face (about 55 degrees).

Checked on a dev server (seed 12345), from real generated chunks:
- Peak field alone, 640-block square at x -800 z -100: 11 summits with 15+ blocks of prominence
  (0.4.6: 7), 8 with 30+ (0.4.6: 5). Allowing up to four shoulders gave no more: the peaks already
  cover 86% of that square, so extra shoulders are buried in neighbouring peaks.
- Snow or ice on the surface: 2.7% of that square (0.4.6: 25%). In a cold range (x 5450 z 5600) the
  cap covers the top 30 to 35 blocks of a 116-block peak, with bare rock below.
- Grass is down to 9% of that square (0.4.6: 28%): the skirts between peaks are bare rock now.

Debug: `/atlasmountains snapshot <size>` generates a square round the player and writes each
column's top block and height; `tools/render_snapshot.py` draws it as a map or a view, lit as
Minecraft lights blocks.

## 0.4.6 (unreleased): look D, and peaks that run down to the ground

- Sammi picked look D from the sampler: strong ribs down each face (10 blocks deep, about 34
  apart) and one or two shoulders. The sampler itself is removed.
- The skirt: the lowest 40% of a peak's height carries on down at 0.62 of the face's steepness
  until it meets the ground. Before, a peak stopped at a line part-way down and sat on a collar
  of vanilla hills ("the bottom hill terrain ... not connected to the rest of the terrain").
- Peaks are looked for three cells each way (was two), since a skirt reaches further.
- `/atlasmountains peaks` now lists the nearest peaks with a teleport to each.

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
