#!/bin/bash
# usage: snap3.sh <outdir> "<x y z>" : snapshot 1024, biomemap, dfmaps
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
P="${4:-debug }"
pkill -f "fabric.dli"; sleep 3
rm -rf fabric/run/world $1; mkdir -p $1; rm -f $1/done
(nohup ./gradlew runServer --no-daemon -Dorg.gradle.parallel=false > /tmp/server.log 2>&1 &)
for i in $(seq 1 100); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains ${P}snapshot 1024" > $1/log 2>&1
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains ${P}biomemap 1024 4" >> $1/log 2>&1; sleep 25
for q in river_wall vanilla_offset peak_height peak_rocky strength; do python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains ${P}dfmap \"atlasmountains:overworld/$q\" 1024 4" >> $1/log 2>&1; sleep 20; done
cp fabric/run/world/atlasmountains/* $1/
grep -c -E "ERROR|Exception|OutOfMemory" /tmp/server.log >> $1/log
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 8; pkill -f "fabric.dli"
echo done > $1/done
