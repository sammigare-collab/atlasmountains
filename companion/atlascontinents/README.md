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

Build: `python3 build.py` writes `build/atlascontinents-<version>.jar`. The same file renamed to `.zip`
works as a plain data pack.

Measured on the dev server with Atlas Mountains 0.6.3 (seed 4381596988616472538): land inside the shore
line on 30.0% of a sample 393,216 blocks square; landmasses a median 9,600 blocks across; mountain
country on 3.0% of the land (2.8% without this mod); the erosion map identical with and without it;
spawn on land at -16 16.

Do NOT use Atlas Mountains with the older pack "NoCaves-LargerBiomes-HugeContinents-Standalone": that
pack replaces seven of the files Atlas Mountains needs and builds its own mountain ranges. Together the
mountains are not built and their rock and snow are painted on flat land.
