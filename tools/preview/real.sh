#!/bin/bash
# The maps named here are written by tools/dev/snap5.sh on the dev server; the /tmp paths are where they were on 15 Nov 2026.
# usage: real.sh <label> <world: coast|frozen|hot> [key=value ...]  : the preview on a real world's land and peaks
cd "$(dirname "$0")"; n=$1; w=$2; shift 2
case $w in
  coast)  SEED=-6677413799414465054; R="/tmp/b59,-1380,1750,1024"; X0=-1764; Z0=1366;;
  frozen) SEED=-3465664254209586682; R="/tmp/RF,-2880,1030,1536"; X0=-3264; Z0=646;;
  hot)    SEED=-5999978045571072206; R="/tmp/RH,4200,-650,1536"; X0=3816; Z0=-1034;;
esac
java -Xmx2g -cp classes com.atlasmountains.fabric.Preview $n $SEED $X0 $Z0 768 out/$n REAL=$R "$@" 2>&1 | grep -v JAVA_TOOL > out/$n.log; head -1 out/$n.log
