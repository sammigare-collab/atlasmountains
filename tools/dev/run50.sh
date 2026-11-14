#!/bin/bash
OUT=$1; SIZE=${2:-896}; POS=${3:-"6480 100 -6050"}
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
pkill -9 -f "fabric.dli"; pkill -9 -f runServer; sleep 2
rm -rf /tmp/chk/$OUT /tmp/chk/run50_done; mkdir -p /tmp/chk/$OUT
/tmp/chk/restart.sh > /tmp/chk/run50_build.log 2>&1
date +%s > /tmp/chk/$OUT/t0
python3 /tmp/chk/rcon.py "execute positioned $POS run atlasmountains snapshot $SIZE" > /tmp/chk/run50.log 2>&1
date +%s > /tmp/chk/$OUT/t1
for q in peak_height vanilla_offset; do python3 /tmp/chk/rcon.py "execute positioned $POS run atlasmountains dfmap \"atlasmountains:overworld/$q\" $SIZE 2" >> /tmp/chk/run50.log 2>&1; sleep 20; done
python3 /tmp/chk/rcon.py "execute positioned $POS run atlasmountains peaks" >> /tmp/chk/run50.log 2>&1
cp fabric/run/world/atlasmountains/* /tmp/chk/$OUT/
grep -c -E "ERROR|Exception|OutOfMemory" /tmp/server.log >> /tmp/chk/run50.log
echo done > /tmp/chk/run50_done
