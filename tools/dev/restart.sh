#!/bin/bash
# rebuild, wipe the test world, start the dev server, wait until it is up
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
python3 /tmp/chk/rcon.py stop >/dev/null 2>&1; sleep 8
pkill -f runServer; pkill -f "fabric.dli" ; sleep 2
./gradlew build --no-daemon -q 2>&1 | grep -v JAVA_TOOL | tail -20
rm -rf fabric/run/world
(nohup ./gradlew runServer --no-daemon > /tmp/server.log 2>&1 &)
for i in $(seq 1 60); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
grep -E "Done|FAILED" /tmp/server.log
