package com.atlasmountains.core;

/**
 * A continentalness field on vanilla's scale: -1 deep ocean .. about 0.7 deep inland. Shoreline at about -0.15.
 * <p>
 * Continentalness is vanilla's "how far from the sea" value. Its terrain splines, biome table
 * and spawn search all read it, so anything that replaces it must use the same scale. The mixed
 * preset replaces vanilla's with {@link NoiseContinents} for bigger, more organic landmasses.
 */
public interface Continents {

    /** Continentalness at a block position. */
    double continentalness(double x, double z);

    /**
     * Piecewise-linear map from signed distance to the shoreline (blocks, + inland) to vanilla's
     * continentalness: deep ocean, ocean, coast band, near inland, far inland, so beaches and the
     * mountain coast fade are a fixed number of blocks wide.
     */
    static double fromDistance(MountainSettings s, double sd) {
        double[] d = {-s.deepOceanDistance, -s.oceanDistance, -s.coastHalfWidth, 0, s.coastHalfWidth, s.nearInlandDistance, s.farInlandDistance, 2.5 * s.farInlandDistance};
        double[] c = {-1.0, -0.5, -0.25, -0.15, -0.05, 0.05, 0.45, 0.7};   // continentalness at each distance in d
        if (sd <= d[0]) return c[0];
        for (int i = 1; i < d.length; i++) {
            if (sd <= d[i]) {
                double t = (sd - d[i - 1]) / (d[i] - d[i - 1]);
                return c[i - 1] + (c[i] - c[i - 1]) * t;
            }
        }
        return c[c.length - 1];
    }

    /** The continents used by the mod. A factory so the implementation can change in one place. */
    static Continents create(MountainSettings s, long seed) {
        return new NoiseContinents(s, seed);
    }
}
