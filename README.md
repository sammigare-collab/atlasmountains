# Atlas Mountains

A Fabric mod for Minecraft 26.3 that overhauls **vanilla's own mountains**.

Built entirely by prompting Claude.

The mod was restarted from scratch on 11 November 2026. The earlier versions (0.1 to 0.3) replaced
vanilla's mountains with a custom terrain generator and their own world type; that code is in the
Git history and on the `main` branch up to v0.2. This version has no generator of its own. It is
a data pack in a jar: it changes the three settings vanilla's terrain reads to shape high ground,
so the mountains are still vanilla's, in vanilla's places, with vanilla's biomes, rivers, caves
and structures, only reshaped. It applies to the normal world type; there is nothing to pick.

## What it changes

Only where vanilla's ground is already high (about y 143 and up):

- **Ridge lines.** The height gets wandering crest lines with even, straight-sided faces, plus
  smaller side ridges, instead of a blobby mass.
- **Cleaner faces.** The ground follows those crests more tightly, so fewer blobs and overhangs.
- **Fewer random spikes.** Vanilla's peak spikiness is turned down.

Everything is set by a handful of numbers at the top of `tools/build_overhaul.py`.

## Building

    ./gradlew build                      # the jar is in fabric/build/libs
    python3 tools/extract_vanilla.py     # copies vanilla's worldgen JSON out of the game jar (not committed)
    python3 tools/build_overhaul.py      # regenerates the data pack under fabric/src/main/resources/data

Use a new world: chunks that already exist keep their old shape.
