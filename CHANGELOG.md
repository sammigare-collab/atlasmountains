# Changelog

The short version. The full development notes, with measurements and the reasoning behind each
change, are in [docs/dev-notes/changelog-full.md](docs/dev-notes/changelog-full.md).

## 0.6.3 (November 2026)

- Fixed thin blades and towers of rock on mountains that stand over a river.

## 0.6.2

- Ranges now end: mountains fill about three fifths of mountain country instead of all of it, so
  a range is a few thousand blocks long.
- A world opens much faster the second time.

## 0.6.1

- Bigger, taller mountains (summits to about y 400).
- Mountains stand in all of vanilla's mountain country, not only its jagged peaks.
- Rivers in mountain country are cut down to water level, so they are no longer dry.

## 0.6.0

- Each mountain is shaped as a whole by erosion, from the valley floor to the summit.
- Snow and rock follow the shape: snow in the hollows, dark rock on ribs and cliffs.

## 0.5.x

- Mountains shaped by running water, with peaks in a group joined at a shared base.
- `/locate mountain` and `/locate mountain tallest`, answered like vanilla's `/locate`.
- Biome fixes at the feet of mountains (no jungle on the slopes, no stray snow patches).

## 0.4.x (from 11 November 2026)

- The mod was restarted from scratch. The custom generator and world type were removed. The mod
  now works through vanilla's own terrain settings, in a normal world.
- Build height raised from y 320 to y 512.
- Many experiments with the shape of the peaks.

## 0.3.0 and earlier

The original mod: a custom mountain generator with its own world type (0.1 and 0.2 for Minecraft
1.21.1, 0.3 for 26.3). That code is in the Git history only.
