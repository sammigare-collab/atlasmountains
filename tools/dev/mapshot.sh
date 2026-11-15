#!/bin/bash
# usage: mapshot.sh <outdir> <seed> "<x y z>" <size> <step> : maps of the land and of the mountains' height, no chunks generated
cd /home/claude/atlasmountains
sed -i "s/^level-seed=.*/level-seed=$2/" fabric/run/server.properties
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 3; pkill -9 -f "fabric.dli"; pkill -9 -f gradle; sleep 2
/tmp/srv_start.sh
mkdir -p $1; rm -f $1/*
for q in atlasmountains:overworld/land atlasmountains:overworld/peak_height; do python3 tools/dev/rcon.py "execute positioned $3 run atlasmountains debug dfmap \"$q\" $4 $5" > /dev/null; done
for i in $(seq 1 90); do n=$(ls fabric/run/world/atlasmountains/ 2>/dev/null | grep -c "dfmap.*_$4_$5\.f32"); [ "$n" -ge 2 ] && break; sleep 5; done
cp fabric/run/world/atlasmountains/dfmap_* $1/
python3 tools/dev/rcon.py "execute positioned $3 run atlasmountains debug peaks" | tail -1 | cut -c1-200 > $1/peaks
grep -v JAVA_TOOL /tmp/server.log | grep -c -E "ERROR|Exception|OutOfMemory" > $1/errors
