# Handover: atlasmountains, 14 November 2026

Written by Claude at the end of a long chat, for the next chat to start from. Read this, then
`CHANGELOG.md` (every build, what Sammi said, what was measured), then the code.

## The project

- A Fabric mod for Minecraft 26.3 that replaces vanilla's mountains with better-looking ones, in
  a normal world (no world type). Sammi's first coding project, built entirely by prompting Claude.
- Repo `sammigare-collab/atlasmountains`, branch `claude/vanilla-overhaul`, PR #8. Current
  version 0.5.3 (commit "Sampler build: snow in hollows, bands of growth, scooped faces, a shared base").
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
  in `tools/build_overhaul.py`) unless he asks.
- Rejected, do not bring back: flat-plane peaks in any form (0.4.x: plain pyramid, ribs,
  buttress panels, steps, folded star, two-tier base, skirt); vanilla 3-D roughness on peaks;
  the old mod's layouts (rings round a bowl, ridge skeleton, long straight side ridges, long
  parallel ranges); a raised valley floor (0.4.13); a "rectangular" level top (asked for and
  withdrawn); the rock line (painted ordinary land as rock); round cones; matching the mountain
  to the surrounding biome.
- Vanilla's land is never lowered under the peaks.

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
  -3550034452994718772 (-9837 -2238); -4547848503839837251 (6480 -6050).
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
