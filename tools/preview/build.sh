#!/bin/sh
# Compiles the preview: the mod's own ErodedPeaks.java, Massif.java and SnowLine.java, unchanged, against the stand-ins in stub/.
cd "$(dirname "$0")"
SRC=../../fabric/src/main/java/com/atlasmountains/fabric
rm -rf classes && mkdir classes
javac -d classes $(find stub src -name '*.java') $SRC/ErodedPeaks.java $SRC/Massif.java $SRC/SnowLine.java $SRC/Surface.java
