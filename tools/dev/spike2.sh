#!/bin/bash
# usage: spike2.sh <outdir> <seed> "<snap x y z>" "<dfmap x y z or none>"
cd /home/claude/atlasmountains
rm -f $1_done; mkdir -p $1; rm -f $1/*
sed -i "s/^level-seed=.*/level-seed=$2/" fabric/run/server.properties
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 3; pkill -9 -f "fabric.dli"; pkill -9 -f gradle; sleep 2
/tmp/srv_start.sh > $1/start.log 2>&1
if [ "$4" != "none" ]; then
  for q in atlasmountains:overworld/peak_height atlasmountains:overworld/land; do python3 tools/dev/rcon.py "execute positioned $4 run atlasmountains debug dfmap \"$q\" 256 1" >/dev/null; done
  sleep 8; cp fabric/run/world/atlasmountains/dfmap_*_256_1.f32 $1/
fi
python3 tools/dev/rcon.py "execute positioned $3 run atlasmountains debug snapshot 1024" > $1/log 2>&1
cp fabric/run/world/atlasmountains/snapshot_* $1/
grep -v JAVA_TOOL /tmp/server.log | grep -c -E "ERROR|Exception|OutOfMemory" > $1/errors
echo done > $1_done
