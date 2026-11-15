#!/bin/bash
# usage: snap4.sh <outdir> "<x y z>" : snapshot 512 and step-1 maps of the mountain's height and the land
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
pkill -f "fabric.dli"; sleep 3
rm -rf fabric/run/world $1; mkdir -p $1; rm -f $1/done
(nohup ./gradlew runServer --no-daemon -Dorg.gradle.parallel=false > /tmp/server.log 2>&1 &)
for i in $(seq 1 100); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains debug snapshot 512" > $1/log 2>&1
for q in peak_height vanilla_offset river_wall; do python3 tools/dev/rcon.py "execute positioned $2 run atlasmountains debug dfmap \"atlasmountains:overworld/$q\" 512 1" >> $1/log 2>&1; sleep 30; done
cp fabric/run/world/atlasmountains/* $1/
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 8; pkill -f "fabric.dli"
echo done > $1/done
