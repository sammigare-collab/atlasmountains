package com.atlasmountains.fabric;

/** Preview stand-in: no rivers (current stays null, so ErodedPeaks skips every river fade). */
record RiverWallFunction(double channel) {
    static volatile RiverWallFunction current;
}
