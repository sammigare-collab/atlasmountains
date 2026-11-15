# Atlas Continents

A companion to Atlas Mountains, kept as a separate mod file. Minecraft 26.3, Fabric (needs Fabric API).
It has no code: only worldgen files, written by `build.py`.

What it does, and nothing else:

- Land comes in separate landmasses, about 10,000 blocks across, over about 30% of the world.
- Biomes are 2.5 times vanilla's size (halfway between vanilla and Large Biomes).
- A new world begins on land: the landmass pattern is lifted round 0 0.

It replaces three of vanilla's files (`overworld/continents`, `overworld/temperature`,
`overworld/vegetation`) and none of the files Atlas Mountains replaces. It leaves erosion alone, which
is what decides where mountain country is, so mountains are the size and spacing they are without it.

Build: `python3 build.py` writes `build/atlascontinents-<version>.jar` (outlines 'medium'); `python3 build.py
--outline=strong` writes the rougher one beside it. Both have the same mod id: install one, not both. The same file renamed to `.zip`
works as a plain data pack.

Measured on the dev server with Atlas Mountains 0.6.3 (seed 4381596988616472538), 0.5.1:

| outlines | land inside the shore | landmass, median across | islands under 2 km | mountain country, share of land |
|---|---|---|---|---|
| 0.5.0 (smooth) | 30.0% | 9,600 blocks | 94 | 3.0% |
| medium | 30.1% | 8,600 | 244 | 3.0% |
| strong | 30.2% | 7,400 | 402 | 3.1% |

(Sample 393,216 blocks square; mountain country over 65,536 square, 2.8% without this mod.) The erosion map is
identical with and without it at every setting. Spawn is on land.

Do NOT use Atlas Mountains with the older pack "NoCaves-LargerBiomes-HugeContinents-Standalone": that
pack replaces seven of the files Atlas Mountains needs and builds its own mountain ranges. Together the
mountains are not built and their rock and snow are painted on flat land.
