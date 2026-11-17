#!/bin/bash
# usage: snap5.sh <outdir> "<x y z>" : snapshot 1024 + step-4 maps of strength, land, river cap and temperature (for the preview's REAL mode)
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
pkill -f "fabric.dli"; sleep 3
rm -rf fabric/run/world $1; mkdir -p $1; rm -f $1/done
(nohup ./gradlew runServer --no-daemon -Dorg.gradle.parallel=false > /tmp/server.log 2>&1 &)
for i in $(seq 1 100); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains debug snapshot 1024" > $1/log 2>&1
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains debug peaks" >> $1/log 2>&1
for q in atlasmountains:overworld/strength atlasmountains:overworld/vanilla_offset atlasmountains:overworld/river_wall minecraft:overworld/temperature; do python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains debug dfmap \"$q\" 1536 4" >> $1/log 2>&1; sleep 45; done
cp fabric/run/world/atlasmountains/* $1/
grep -c -E "ERROR|Exception|OutOfMemory" /tmp/server.log >> $1/log
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 8; pkill -f "fabric.dli"
echo done > $1/done
