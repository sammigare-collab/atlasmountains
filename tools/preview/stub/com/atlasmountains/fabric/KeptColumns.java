package com.atlasmountains.fabric;

/** Stand-in for the preview: nothing is kept between runs here. */
final class KeptColumns {
    static boolean single() { return false; }
    static double[] kept(int x, int z) { return null; }
    static void keep(HornsFunction horns, int x, int z, double[] four, double cover) {}
}
