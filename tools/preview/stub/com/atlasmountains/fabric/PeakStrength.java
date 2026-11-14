package com.atlasmountains.fabric;

/**
 * Preview stand-in for vanilla's land and mountain country. The real one reads vanilla's density
 * functions; here mountain country is a slow noise (some cells full strength, some none, so peaks
 * come in groups as they do in the game) and the land is a gently rolling plain round y 90.
 */
final class PeakStrength {
    static double COUNTRY_LENGTH = 1100, COUNTRY_BIAS = 0.55, COUNTRY_GAIN = 1.1;
    /** Share of cells that keep their peak (1: all). Below 1 gives tall peaks standing apart, as in sparse mountain country in the game. */
    static double THIN = 1;
    static boolean ready() { return true; }
    static double at(long ix, long iz, int x, int z) {
        if (HornsFunction.hash(ix, iz, AtlasMountainsMod.seed() + 9009) > THIN) return 0;
        double n = 0.65 * HornsFunction.valueNoise(x / COUNTRY_LENGTH, z / COUNTRY_LENGTH, AtlasMountainsMod.seed() + 9001)
                 + 0.35 * HornsFunction.valueNoise(x / (COUNTRY_LENGTH / 2.3), z / (COUNTRY_LENGTH / 2.3), AtlasMountainsMod.seed() + 9002);
        return Math.max(0, Math.min(1, COUNTRY_BIAS + COUNTRY_GAIN * n));
    }
    /** Ground height y at a column. */
    static double groundY(double x, double z) {
        return 90 + 6 * HornsFunction.valueNoise(x / 170.0, z / 170.0, AtlasMountainsMod.seed() + 9003)
                  + 2 * HornsFunction.valueNoise(x / 45.0, z / 45.0, AtlasMountainsMod.seed() + 9004);
    }
    static double land(int x, int z) { return (groundY(x, z) - 128) / 128.0; }
    static double landAt(long ix, long iz, int x, int z) { return land(x, z); }
    static double temperature(int x, int z) { return 0.0; }
}
