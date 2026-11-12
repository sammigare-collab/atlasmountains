# Atlas Mountains

A Fabric mod for Minecraft 26.3 that overhauls **vanilla's own mountains**.

Built entirely by prompting Claude.

The mod was restarted from scratch on 11 November 2026. The earlier versions (0.1 to 0.3) replaced
vanilla's mountains with a custom terrain generator and their own world type; that code is in the
Git history and on the `main` branch up to v0.2. This version has no generator of its own. It works
through vanilla's own terrain settings, so the mountains stand in vanilla's mountain country, with
vanilla's biomes, rivers, caves and structures. It applies to the normal world type; there is nothing to pick.

## What it changes

In vanilla's mountain country (low erosion, away from the coast) the blobby ranges are replaced
by clusters of separate pyramid peaks:

- **Horn peaks.** Each peak is its own point, steep at the top and flaring into the land, with
  three to five sharp ridges running down from the summit and a deep notch to its neighbour.
- **One tallest peak, others stepping down**, biggest in the heart of the mountain country and
  shrinking towards its edges.
- **Rivers still run between them**: river valleys are left as vanilla made them.
- **A taller world.** Vanilla's land keeps its own height and the peaks stand on top of it, so
  the overworld's build height is raised from y 320 to y 512 and terrain may reach about y 480.

The top of each peak takes vanilla's own peak biome for its climate (jagged, frozen or stony
peaks). Other biomes, caves, structures and everything outside mountain country are vanilla's.
It applies to the default world type only (not Large Biomes or Amplified).

The peak shape is the `atlasmountains:horns` density function (`HornsFunction.java`); where the
peaks go and how they meet vanilla's land is set in `tools/build_overhaul.py`. The shape was
worked out in `tools/prototype_horns.py`, which draws ground-level previews in seconds.

Debug commands: `/atlasmountains mountains <radius>` lists where the mountain country is;
`/atlasmountains heightmap <size> <step>` dumps the real surface heights (view them with
`tools/view_heightmap.py`); `/atlasmountains df "<id>"` samples a density function.

## Building

    ./gradlew build                      # the jar is in fabric/build/libs
    python3 tools/extract_vanilla.py     # copies vanilla's worldgen JSON out of the game jar (not committed)
    python3 tools/build_overhaul.py      # regenerates the data pack under fabric/src/main/resources/data

Use a new world: chunks that already exist keep their old shape.
