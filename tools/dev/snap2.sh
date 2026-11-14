#!/bin/bash
# usage: snap2.sh <outdir> "<x y z>" [dfmaps]
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
rm -rf fabric/run/world $1; mkdir -p $1; rm -f $1/done
(nohup ./gradlew runServer --no-daemon -Dorg.gradle.parallel=false > /tmp/server.log 2>&1 &)
for i in $(seq 1 100); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains snapshot 1024" > $1/log 2>&1
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains peaks" >> $1/log 2>&1
if [ -n "$3" ]; then for q in river_wall vanilla_offset peak_height; do python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains dfmap \"atlasmountains:overworld/$q\" 1024 4" >> $1/log 2>&1; sleep 25; done; fi
cp fabric/run/world/atlasmountains/* $1/
grep -c -E "ERROR|Exception|OutOfMemory" /tmp/server.log >> $1/log
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 8; pkill -f "fabric.dli"
echo done > $1/done
