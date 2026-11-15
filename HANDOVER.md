# Handover: atlasmountains, 14 November 2026

UPDATE, 15 November 2026, evening: 0.6.2 IS BUILT AND SENT. READ ITS CHANGELOG ENTRY FIRST: it records what he
meant by "smaller" (not the mountains, not the edges of the range: whole stretches of a range gone, the rest as
packed as it was) and the two wrong turns before that. HIS VERDICT ON 0.6.2 in the game, a new world (seed
4381596988616472538 at -219 117 2430): "wow fantastic 10/10". SO 0.6.2 IS THE BASELINE: the mountains, how packed a
range is, how much of the map is mountain. Change none of it unless he asks. Nothing is open.
- The amount of mountain is the RANGES block in `tools/build_overhaul.py`: `RANGE_FROM`/`RANGE_FULL` move the share
  (the noise's 40th percentile is -0.08: lower both for more mountain), `RANGE_OCTAVE` the size of the stretches,
  `RANGE_SHIFT_X/Z` which stretches are on in any one world (picked for his world: 493 774 on, the belt east of
  x 1,700 off). `SIZE` (how big each mountain is) stays 1.0: he loves the mountain itself.
- Likely next complaints: what stands in a stretch that is off (vanilla's high ground and snow biomes, without its
  spikes, rivers cut to water level); worlds made in 0.6.1 get a hard stop where old chunks meet new.
- Loading: reopening a world is fixed (`KeptColumns.java`, 81 s to 2 s to the first stronghold). Creating a world
  is not (64 s on the dev server on his seed, all of it eroding regions near stronghold sites). Making chunks is
  no faster than 0.6.1: the per-thread column memory is exact but saved nothing measurable.
- Every speed change must leave terrain the same to the last bit. Checks that exist: the preview prints "checksum
  of the relief" (677954a2 dense, 6fb51169 sparse, at SIZE 1.0); files /tmp/atlas_nomemory and /tmp/atlas_nokeeping
  switch the two memories off for an A/B run; `/tmp/strong.sh`-style `/locate` from fixed places between openings.
- Dev scripts are in `tools/dev/` (they expect to be run from /tmp copies or by path): srv_start.sh (fresh world), srv_again.sh (same world),
  mapshot.sh (land and peak height maps, no chunks), drawzoom.py/drawmaps.py (his world from above from those maps).
  Never run a second server command while a timing run is going: the box has two cores.
- A straight edge between jungle and badlands at about 1700..2050, 1120 on his seed (in the stretch that is now
  off) was looked at and taken to be vanilla's own biome boundary. Not checked against a world without the mod.

Also since 0.6.1: his screenshot is the README's picture (docs/atlas-mountains.png) and the mod's icon.

UPDATE, 15 November 2026, later: 0.6.1 is the current build. READ ITS CHANGELOG ENTRY FIRST. He tried 0.6.0
in the game (seed -5156097255193664704) and said: general shape liked, but "very small", "feel free to make
the biome bigger and make them taller", "these should be replacing snowy peaks etc not just jagged", "rivers
are scuffed", "it seems to have stopped covering river valleys as we agree and rivers are gone". So 0.6.1
changes WHERE peaks stand and HOW BIG they are, at his asking: mountain country is all of vanilla's two
lowest erosion bands; everything is half as big again (cell 345, height 258); a summit stands on the
strongest spot of its cell; rivers in mountain country are cut down to water level (the mod LOWERS vanilla's
land along river channels, nowhere else); a river is covered wherever the mountains would stand 120 blocks or
more over it. Two old rules below are therefore out of date: "do not change where peaks generate" (he asked)
and "vanilla's land is never lowered" (true except along rivers). Also in 0.6.1: a cache-key fault that had
been mixing up mirrored cells since 0.6.0 (HornsFunction.cellKey), and the erosion made twice as fast.
Traps met this time: a 1536 snapshot runs the dev server out of memory on its second go (keep to 1024);
`pkill` without -9 does not stop a hung server and the next one then fails to bind its port;
`./gradlew runServer` recompiles, so to run the build before your edits use `git stash`; never print a long
list from a script into the chat. New dev scripts: `tools/dev/srv_start.sh`, `tools/dev/batch2.sh <tag>
"<name|seed|x y z>" ...`, `tools/dev/topdown.py <out.png> <snapshot.bin> ...`, and
`tools/preview/.../Seams.java` (how far two neighbouring regions disagree; run it after any change of size).
The preview tool's stand-in for the game does NOT have the room rule or the filling of covered valleys;
judge rivers on the dev server only. HIS VERDICT ON 0.6.1 IN THE GAME (15 Nov 2026, seed 2089824661498644259
at 493 774): "loving it good job". So 0.6.1 is the baseline: size, spacing, where mountains stand, real rivers and
the covering rule are all liked as they are. Change none of them unless he criticises that part. Still open, and
not something he has raised: opening a world is slow (see the changelog, "Speed").

UPDATE, 15 November 2026: 0.6.0 is the current build. READ ITS CHANGELOG ENTRY FIRST: it records the
finding that changed everything (the mod had only been shaping the 60-odd blocks it adds on top of
vanilla's smooth 80-block hill; the preview's flat land hid it) and the rules it bends. How to work now:
`tools/dev/snap5.sh <dir> "<x y z>"` dumps a real world's maps; add the seed to `tools/preview/real.sh`;
iterate there in seconds on real land and rivers; `tools/dev/batch.sh <tag>` then runs real chunks on four
of his seeds (15 minutes) and `tools/preview/sheet.py` makes before and after sheets from his camera spots.
Never judge a change on the stand-in land alone. Waiting for his in-game verdict on 0.6.0.

UPDATE, later the same day: 0.5.4 is built and sent (see CHANGELOG.md). The preview tool exists
(`tools/preview`: `build.sh`, then `java -cp classes com.atlasmountains.fabric.Preview ...`,
then `render.py`). The open question below is answered: he chose joined peaks (version C), to be
judged in the game; he set the reference pictures aside ("im worried the references were sending
you down a wrong rabbit hole"). The shape is now `Massif.java`; "How 0.5.3 works" below is out of
date on the shape only. 0.5.5 (faces carved, his choice A) followed the same day; then 0.5.6 (a river valley between two peaks of a group is covered: his choice, "burry it"). THREE things keep rivers clear, not one: the fade in Massif, RIVER_EASE in HornsFunction, and river_wall in the data pack. Then 0.5.7 (tower, domes, rock line; heights can now come out lower where a peak is hemmed in). 0.5.7 made cones on a hot-country seed and its shape changes were undone in 0.5.8 (see CHANGELOG.md: read that entry before touching the shape). LESSONS: the preview has no real land or rivers and did not show the fault; before sending a shape change, run it on real chunks on at least two seeds (6021084639467433954 at -7500 5600 and -5999978045571072206 at 4200 -650) and compare with the build before from his camera spots. After that: 0.5.9 commands (/locate mountain), 0.5.10 biomes, 0.5.11 a three-look sampler he could not tell apart, 0.5.12 knife-edge ridges after he sent two reference pictures and agreed a reading of them (CHANGELOG.md). Then 0.5.13: the top a short crest, at his asking. Waiting for his verdict on 0.5.13. Use tools/preview/shade.py to see gullies; plain renders hide them. Dev loop that worked: /tmp-style script that starts the server, snapshots 1024 at a spot, optional dfmaps, stops (see tools/dev/snap2.sh); render from his screenshot camera positions for a like-for-like before and after. `tools/build_overhaul.py`
needs `tools/vanilla-26.3` (from `tools/extract_vanilla.py`) and deletes the data pack before it
fails without it: `git checkout -- fabric/src/main/resources` puts it back.

Written by Claude at the end of a long chat, for the next chat to start from. Read this, then
`CHANGELOG.md` (every build, what Sammi said, what was measured), then the code.

## The project

- A Fabric mod for Minecraft 26.3 that replaces vanilla's mountains with better-looking ones, in
  a normal world (no world type). Sammi's first coding project, built entirely by prompting Claude.
- Repo `sammigare-collab/atlasmountains`, branch `claude/vanilla-overhaul`, PR #8. Current
  version 0.6.1 (this paragraph was written at 0.5.3: commit "Sampler build: snow in hollows, bands of growth, scooped faces, a shared base").
- Fabric Loader 0.20.1, Fabric API 0.165.2+26.3, Loom 1.19.4, Gradle 9.8.2, Java 25 (a cloud
  session has to download a JDK 25 first). Build: `python3 tools/build_overhaul.py` (regenerates
  the data pack; easy to forget, and the Gradle build does not do it), then `./gradlew build`.
  Jar in `fabric/build/libs/`.

## How Sammi wants to work (his words)

"British English, no em dashes, direct, no praise. Treat my screenshots and drawn lines
literally. Recommend, don't say 'it depends'. Change only the part I criticised. Say plainly
what was and wasn't tested. Send jars as zips. Commit and push each build to the branch. Keep my
email address out of commits and everything else." Also: before writing code, say in words how
his references differ from his screenshots and get his agreement; only in-game screenshots
count (renders have misled him); sampler builds (several looks in one world) worked best.

## Where it stands

His verdict on 0.5.3: "I don't think that's good enough." Claude agreed. Why:

- Every mountain is still one summit with ridges radiating from it (a cone with decoration).
  Legs, scoops and a swell were added on top; the underlying form never changed.
- The erosion is too weak to make the shape: it scratches gullies a few blocks deep into a
  shape drawn by hand, on a 4-block grid.
- The snow went from bare hollows (0.5.2) to blotches (0.5.3).
- The method was poor: one six-minute server test per change, one square, one seed; several
  changes per build; patching whatever the last screenshot showed.

What he has liked, for calibration: the old mod's eroded mountains (0.1 to 0.3; "the best I've
ever seen in Minecraft randomly generated"); of 0.5.0, "sometimes nice and angularly eroded,
sometimes too soft"; grass on the flats running a little way up gentle ground. His references:
a row of horn peaks over a lake valley, one big summit with ridges stepping down from it, a
WorldPainter row of sharp uneven peaks. He asked for "more realistic".

## The agreed plan (he said "yes, let's go")

1. Build a preview tool first: draw the terrain as a shaded picture in seconds, without
   Minecraft, so shapes can be tried by the dozen and put beside his reference pictures before
   he sees a jar. `ErodedPeaks.java` is plain Java except for three hooks (PeakStrength for where
   peaks stand and how tall, RiverDistance, HornsFunction's hash and noise); stub those.
   `tools/render_snapshot.py` already renders Minecraft-lit 3-D views from a height grid.
2. Let erosion make the shape: start from a rough lump for each group of peaks and run much
   heavier erosion over the whole group, so ridges and valleys come from the water and not from
   lines placed by hand.
3. Ship only when a preview resembles his references. Then one change per build.

OPEN QUESTION, not answered: his references are groups of joined peaks; his standing rule is
separate individual peaks, not a ridge line. Heavy erosion over a group joins peaks at ridges
and saddles more than now. Claude said this is probably what "realistic" needs and that it is
his call. Ask before building it.

## Rules that still stand

- Do not change where peaks generate or their spacing (erosion, inland, river settings, "cell"
  in `tools/build_overhaul.py`) unless he asks. (He asked, for 0.6.1. The rule stands again from there.)
- Rejected, do not bring back: flat-plane peaks in any form (0.4.x: plain pyramid, ribs,
  buttress panels, steps, folded star, two-tier base, skirt); vanilla 3-D roughness on peaks;
  the old mod's layouts (rings round a bowl, ridge skeleton, long straight side ridges, long
  parallel ranges); a raised valley floor (0.4.13); a "rectangular" level top (asked for and
  withdrawn); the rock line (painted ordinary land as rock); round cones; matching the mountain
  to the surrounding biome.
- Vanilla's land is never lowered under the peaks. (Since 0.6.1: except along river channels in mountain
  country, which are cut down to water level so the rivers are real.)

## How 0.5.3 works (file map, all under `fabric/src/main/java/com/atlasmountains/fabric/`)

- `HornsFunction.java`: where peaks stand and how tall (unchanged since 0.4). `ERODED = true`
  switches its shape to `ErodedPeaks`; the 0.4 flat-plane code below that is dead and can go.
- `ErodedPeaks.java`: one mountain per peak (crest, old cross profile, legs, scoop), a shared
  swell, erosion per 1024-block region (thermal, droplets, fill, thermal), kept in memory;
  also the surface questions: `cover` (trees / open grass / rock) and `snowShift`.
- `PeakStrength.java` (vanilla land and mountain-country strength), `RiverDistance.java`,
  `SnowLine.java`, `PeakSnowCondition.java`, `PeakRockRule.java`, `OnPeakCondition.java`
  (tolerance -1 asks for the open-grass band), `PeakRockyFunction.java`, `FindCommand.java`
  (`/atlasmountains peaks | snapshot <size> | dfmap "<df>" <size> <step>`).
- `tools/build_overhaul.py`: every setting, and the generator for the data pack.

## Testing (what was used, and its traps)

- `tools/dev/` holds the scratch scripts as they were (paths assume `/home/claude/atlasmountains`
  and a JDK at `/opt/jdk25`): `restart.sh` (rebuild, wipe the dev world, start a headless
  server; it does NOT regenerate the data pack), `rcon.py` (dev server only; port 25575,
  password in `fabric/run/server.properties`), `run50.sh` (snapshot plus density-function maps),
  `cmpv.py` (side-by-side renders and surface shares), `soft.py`. Sammi approved accepting
  Mojang's EULA for the dev server.
- Seeds and spots he has sent: 5300383603861270436 (around -200 -450; snow spot 163 331 -523;
  165 139 -868; 535 189 -507); -4410578526466350703 (around -5050 -3600);
  -3550034452994718772 (-9837 -2238); -4547848503839837251 (6480 -6050);
  -5156097255193664704 (722 178 2304, 4964 178 -1162, 6493 153 1685: his complaints about 0.6.0);
  2089824661498644259 (493 774: where he liked 0.6.1).
- Traps met: snapshots over about 1024 blocks run the dev server out of memory; the peaks
  command's teleport height is 20 ABOVE the summit (Claude once mis-calibrated every mountain 20
  blocks too tall from this); a world made with one build shows cliffs where it meets chunks
  from the next, so he needs a new world per build; he plays with Distant Horizons, whose flat
  slabs in screenshots are not terrain.
- Erosion cost on the dev server at 0.5.3: 411 ms per region on average, 870 at worst.

## Loose ends

- The 0.5.3 sampler (scoop depth S / M / L) has no answer from him.
- Not touched: steep walls where a mountain meets a river or the coast; an overhanging ledge
  at a mountain's foot; trees running up between two peaks at 165 139 -868.
- Not known: memory use of eroded regions in long play; cold biomes; other mods.
- A separate mod, Atlas Continents 0.4.0, is not on GitHub; he has its source zip.
