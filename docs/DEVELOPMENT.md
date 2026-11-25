# Development

## How it works

The mod has no terrain generator of its own. It works through vanilla's terrain settings (density
functions and noise settings), so the mountains sit inside vanilla's pipeline and there are no
joins where they meet vanilla land.

- Mountains stand in vanilla's mountain country: inland, in its two lowest bands of erosion, where
  its peak and slope biomes are. They fill about three fifths of that country.
- What is added to vanilla's land is whatever of the mountain's shape stands above it. Vanilla's
  land is never lowered, except along rivers, which are cut down to water level.
- Where the mountains would stand 120 blocks or more over a river, they cover it.
- The top of each peak takes vanilla's own peak biome for its climate (jagged, frozen or stony
  peaks).
- Terrain may reach about y 480; the build height is y 512.

## Where things are

| Path | What it is |
|---|---|
| `fabric/src/main/java/.../HornsFunction.java` | Where the peaks stand and how tall they are |
| `.../Massif.java`, `ErodedPeaks.java` | The shape of a mountain (erosion) and reading it back |
| `.../Surface.java` | Where the snow lies and how dark the rock is |
| `tools/build_overhaul.py` | Generates the data pack: how the mountains meet vanilla's land and biomes |
| `tools/extract_vanilla.py` | Copies vanilla's worldgen JSON out of the game jar (not committed) |
| `tools/preview/` | Draws the mountains in seconds without Minecraft, from the mod's own code |
| `tools/render_snapshot.py` | Draws a snapshot written by the debug command below |
| `tools/dev/` | Scripts for the dev server (`srv_start.sh`, `rcon.py`, `snap5.sh`) |
| `docs/dev-notes/` | The full changelog and the handover notes between work sessions |

## Building

Needs Java 25.

    ./gradlew build                      # the jar is in fabric/build/libs
    python3 tools/extract_vanilla.py     # copies vanilla's worldgen JSON out of the game jar
    python3 tools/build_overhaul.py      # regenerates the data pack under fabric/src/main/resources/data

The last two are only needed if you change `build_overhaul.py`. The generated data pack is
committed.

## Preview without the game

`tools/preview` compiles the mod's own shape code against small stand-ins and draws the result,
either on a stand-in land or on a real world's land and rivers dumped from the dev server
(`tools/preview/real.sh`, with maps written by `tools/dev/snap5.sh`).

## Debug commands

Under `/atlasmountains debug` (cheats or operator):

| Command | What it does |
|---|---|
| `mountains <radius>` | Lists where the mountain country is |
| `peaks` | Lists the nearest peaks and how long erosion took |
| `snapshot <size>` | Generates a square and writes its top blocks and heights |
| `heightmap <size> <step>` | Dumps surface heights without generating chunks |
| `df "<id>"` | Samples a density function |
| `dfmap "<id>" <size> <step>` | Writes a map of a density function into the world folder |
| `biomemap <size> <step>` | Writes a map of the biomes into the world folder |
