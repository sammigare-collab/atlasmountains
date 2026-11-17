#!/bin/bash
# usage: batch2.sh <tag> "<name|seed|x y z>" ... : a 1024 snapshot of real chunks for each -> /tmp/<tag>_<name>
cd /home/claude/atlasmountains
tag=$1; shift
rm -f /tmp/${tag}_done
for run in "$@"; do
  IFS='|' read name seed pos <<< "$run"
  sed -i "s/^level-seed=.*/level-seed=$seed/" fabric/run/server.properties
  python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 4
  pkill -9 -f "fabric.dli"; pkill -9 -f gradle; sleep 3
  /tmp/srv_start.sh > /dev/null 2>&1
  mkdir -p /tmp/${tag}_$name; rm -f /tmp/${tag}_$name/*
  python3 tools/dev/rcon.py "execute positioned $pos run atlasmountains debug snapshot 1024" > /tmp/${tag}_$name/log 2>&1
  python3 tools/dev/rcon.py "execute positioned $pos run atlasmountains debug peaks" >> /tmp/${tag}_$name/log 2>&1
  python3 tools/dev/rcon.py "execute positioned $pos run locate mountain tallest" >> /tmp/${tag}_$name/log 2>&1
  cp fabric/run/world/atlasmountains/snapshot_* /tmp/${tag}_$name/
  grep -v JAVA_TOOL /tmp/server.log | grep -E "ERROR|Exception|OutOfMemory" | head -5 > /tmp/${tag}_$name/errors
done
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 4; pkill -9 -f "fabric.dli"; pkill -9 -f gradle
echo done > /tmp/${tag}_done
