package com.atlasmountains.fabric;

/** Preview stand-in: rivers are far away, unless REAL maps are loaded (then worked back from that world's river cap). */
final class RiverDistance {
    static final double REACH = 320;
    static double at(int x, int z, double channel) { return PeakStrength.riverDistance(x, z); }
}
