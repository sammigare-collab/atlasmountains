#!/bin/bash
# where the game finds strongholds and a few biomes from fixed places: must not change between openings
cd /home/claude/atlasmountains
for p in "0 100 0" "493 100 774" "-3000 100 2500" "4000 100 -3000" "-6000 100 -6000"; do
  python3 tools/dev/rcon.py "execute positioned $p run locate structure minecraft:stronghold" | tr -d '\n' | sed 's/§.//g' | grep -o 'The nearest.*' | cut -c1-110
done
for p in "493 100 774" "-3000 100 2500"; do
  python3 tools/dev/rcon.py "execute positioned $p run locate biome minecraft:jagged_peaks" | tr -d '\n' | sed 's/§.//g' | grep -o 'The nearest.*' | cut -c1-110
done
