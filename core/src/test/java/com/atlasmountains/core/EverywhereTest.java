package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The "everywhere" preset: the whole world is mountains, so the mask is 1 and slopes are real. */
class EverywhereTest {

    @Test
    void maskIsOneAndSteepFacesReadAsSteep() {
        MountainSettings s = new MountainSettings();
        RegionCache cache = new RegionCache(s, 7, null, true);
        try {
            HeightmapGenerator gen = cache.generator();
            RidgeSample sc = new RidgeSample();
            // a column on a rock face: well above the rock line but below the summits
            double fx = 0, fz = 0;
            search:
            for (int z = 0; z < 2000; z += 8)
                for (int x = 0; x < 2000; x += 8) {
                    double h = gen.heightAt(x, z, sc);
                    if (h > s.rockLineHeight() + 20 && h < s.summitHeight - 20) { fx = x; fz = z; break search; }
                }
            assertEquals(1.0, cache.mask(fx, fz));
            // the rock rule in build_datapack.py is slope_above 35 degrees; rock faces are about 49
            assertTrue(cache.slopeDegrees(fx, fz) > 35, "rock face reads as " + cache.slopeDegrees(fx, fz) + " degrees");
        } finally {
            cache.shutdown();
        }
    }

    @Test
    void theMixedPresetStillHasNoMountainsBeforeTheErosionNoise() {
        RegionCache cache = new RegionCache(new MountainSettings(), 7, null, false);
        try {
            assertEquals(0.0, cache.mask(1000, 1000));
        } finally {
            cache.shutdown();
        }
    }
}
