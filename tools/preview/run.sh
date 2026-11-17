#!/bin/bash
# usage: run.sh <label> <scene: dense|sparse|weak>   (weak: small peaks on big vanilla hills, the case the game often has) [key=value ...]   -> out/<label>_-900_-900.bin
cd "$(dirname "$0")"; n=$1; sc=$2; shift 2
if [ "$sc" = sparse ]; then S="COUNTRY_BIAS=1 THIN=0.3"; elif [ "$sc" = weak ]; then S="COUNTRY_BIAS=1 THIN=0.3 PEAK_SCALE=0.5 LAND_RISE=0 LAND_HILLS=25 DOME=80"; else S="COUNTRY_BIAS=0.8"; fi
java -Xmx2g -cp classes com.atlasmountains.fabric.Preview $n 5300383603861270436 -900 -900 768 out/$n $S "$@" 2>&1 | grep -v JAVA_TOOL > out/$n.log; head -2 out/$n.log
