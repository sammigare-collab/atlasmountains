#!/bin/bash
# start the dev server on a fresh world and wait until it is up
cd /home/claude/atlasmountains
export JAVA_HOME=/opt/jdk25 PATH=/opt/jdk25/bin:$PATH
pkill -9 -f "fabric.dli"; sleep 5
rm -rf fabric/run/world
(nohup ./gradlew runServer --no-daemon -Dorg.gradle.parallel=false > /tmp/server.log 2>&1 &)
for i in $(seq 1 110); do sleep 5; grep -q -E "Done \(|FAILED" /tmp/server.log 2>/dev/null && break; done
grep -E "Done \(|FAILED" /tmp/server.log | tail -2
