#!/bin/bash
# usage: batch.sh <tag>  : real-chunk snapshots on four of Sammi's seeds -> /tmp/<tag>_{frozen,hot,snowy,coast}
cd /home/claude/atlasmountains
rm -f /tmp/$1_done
for run in "frozen|-3465664254209586682|-2880 100 1030" "hot|-5999978045571072206|4200 100 -650" "snowy|6021084639467433954|-7500 100 5600" "coast|-6677413799414465054|-1380 100 1750"; do
  IFS='|' read name seed pos <<< "$run"
  sed -i "s/^level-seed=.*/level-seed=$seed/" fabric/run/server.properties
  for try in 1 2; do
    pkill -f "fabric.dli"; pkill -f runServer; sleep 6
    /tmp/snap2.sh /tmp/$1_$name "$pos" > /dev/null 2>&1
    ls /tmp/$1_$name/snapshot_*.bin > /dev/null 2>&1 && break
  done
  grep -v JAVA_TOOL /tmp/server.log | grep -E "ERROR|Exception|OutOfMemory" | head -5 > /tmp/$1_$name/errors
done
echo done > /tmp/$1_done
