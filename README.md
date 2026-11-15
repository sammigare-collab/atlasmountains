# Atlas Mountains

A Fabric mod for Minecraft 26.3 that overhauls **vanilla's own mountains**.

Built entirely by prompting Claude.

The mod was restarted from scratch on 11 November 2026. The earlier versions (0.1 to 0.3) replaced
vanilla's mountains with a custom terrain generator and their own world type; that code is in the
Git history and on the `main` branch up to v0.2. This version has no generator of its own. It works
through vanilla's own terrain settings, so the mountains stand in vanilla's mountain country, with
vanilla's biomes, rivers, caves and structures. It applies to the normal world type; there is nothing to pick.

## What it changes

In all of vanilla's mountain country (where its peak and slope biomes are: its two lowest bands of
erosion, inland) the blobby ranges are replaced by mountains shaped by erosion, standing about
260 blocks above the land at their tallest (summits to about y 400):

- **Whole mountains, from the valley floor to the summit.** Each mountain is shaped as one
  thing from the low ground round it: running water cuts valleys and leaves ridges that run out
  into the land, couloirs are cut into the faces and the rock between them stands as sharp ribs.
  Peaks close together share a base and meet at saddles. What is added to vanilla's land is
  whatever of that shape stands above it; vanilla's land is never lowered, except along rivers
  (below).
- **Snow and rock that follow the shape.** Snow lies in the couloirs and hollows and on the
  gentler ground high up; ribs and cliffs stand out of it as dark rock. In frozen country a
  mountain is white down to its foot. Lower down are open grass and then vanilla's own woods.
- **One tallest peak, others stepping down**, biggest in the heart of the mountain country and
  shrinking towards its edges.
- **Real rivers.** Vanilla lifts its river valleys clear of the water inside mountain country,
  so they are dry there. The mod cuts them down to water level like vanilla's rivers everywhere
  else. A mountain comes down to the land at a river; where the mountains would stand 120
  blocks or more over a river, they cover it and it comes out again on the far side.
- **A taller world.** Vanilla's land keeps its own height and the peaks stand on top of it, so
  the overworld's build height is raised from y 320 to y 512 and terrain may reach about y 480.

The top of each peak takes vanilla's own peak biome for its climate (jagged, frozen or stony
peaks). Other biomes, caves, structures and everything outside mountain country are vanilla's.
It applies to the default world type only (not Large Biomes or Amplified).

Where the peaks stand and how tall they are is `HornsFunction.java`; their shape is `Massif.java`
(read back by `ErodedPeaks.java`); where the snow lies and how dark the rock is, `Surface.java`;
how they meet vanilla's land and biomes is set in `tools/build_overhaul.py`. `tools/preview` draws
the mountains in seconds without Minecraft, from the mod's own code, on a stand-in land or on a
real world's land and rivers dumped from the dev server (`tools/preview/real.sh`).

Commands: `/locate mountain` finds the nearest mountain and `/locate mountain tallest` the
tallest within 6,000 blocks, answered as vanilla's `/locate` answers (click the coordinates to
teleport; cheats or operator needed, as for `/locate biome`). `/atlasmountains` says the version.

For testing, under `/atlasmountains debug` (cheats or operator): `mountains <radius>` lists where
the mountain country is; `peaks` lists the nearest peaks and how long erosion has taken;
`snapshot <size>` generates a square and writes its top blocks and heights (draw it with
`tools/render_snapshot.py`); `heightmap <size> <step>` dumps the surface heights without
generating chunks; `df "<id>"` samples a density function; `dfmap "<id>" <size> <step>` and
`biomemap <size> <step>` write a map of a density function or of the biomes into the world folder.

## Building

    ./gradlew build                      # the jar is in fabric/build/libs
    python3 tools/extract_vanilla.py     # copies vanilla's worldgen JSON out of the game jar (not committed)
    python3 tools/build_overhaul.py      # regenerates the data pack under fabric/src/main/resources/data

Use a new world: chunks that already exist keep their old shape.
