#!/bin/bash
# usage: acdump.sh <jar> <seed> <outdir> : fresh world with that continents jar, dumps the overview and zoom maps
cd /home/claude/atlasmountains
rm -f fabric/run/mods/atlascontinents*.jar; cp "$1" fabric/run/mods/
sed -i "s/^level-seed=.*/level-seed=$2/" fabric/run/server.properties
python3 tools/dev/rcon.py stop >/dev/null 2>&1; sleep 2; pkill -9 -f "fabric.dli"; pkill -9 -f gradle; sleep 2
/tmp/srv_start.sh | cut -c1-90
R="python3 tools/dev/rcon.py"; rm -f fabric/run/world/atlasmountains/dfmap_*
for q in minecraft:overworld/continents atlasmountains:overworld/land atlasmountains:overworld/strength minecraft:overworld/temperature; do
  $R "execute positioned 0 100 0 run atlasmountains debug dfmap \"$q\" 262144 512" >/dev/null
  $R "execute positioned 0 100 0 run atlasmountains debug dfmap \"$q\" 65536 128" >/dev/null
done
for i in $(seq 1 80); do n=$(ls fabric/run/world/atlasmountains/ 2>/dev/null | grep -c "dfmap.*\.f32"); [ "$n" -ge 8 ] && break; sleep 3; done; sleep 2
mkdir -p $3; rm -f $3/*; cp fabric/run/world/atlasmountains/dfmap_* $3/
grep -v JAVA_TOOL /tmp/server.log | grep -c -E "ERROR|Exception" > $3/errors
python3 - "$3" <<'PY'
import gzip,sys,re
d=gzip.open('/home/claude/atlasmountains/fabric/run/world/level.dat','rb').read()
i=d.find(b'\x0b\x00\x03pos')
import struct
if i>0:
    n=struct.unpack('>i',d[i+6:i+10])[0]; print('spawn', struct.unpack('>%di'%n,d[i+10:i+10+4*n]))
PY
