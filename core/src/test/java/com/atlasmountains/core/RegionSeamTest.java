package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Neighbouring regions are eroded separately; the blend across their overlap must not leave a step. */
class RegionSeamTest {

    @Test
    void noStepAcrossARegionEdge() {
        MountainSettings s = new MountainSettings();
        RegionCache cache = new RegionCache(s, 7, null);   // memory only; no erosion noise, so ridges everywhere
        try {
            double edge = s.regionSizeBlocks;              // the x = 2048 boundary between regions 0 and 1
            double worstSeam = 0, worstInterior = 0;
            for (int z = 600; z <= 1400; z += 4) {
                worstSeam = Math.max(worstSeam, Math.abs(cache.baseHeight(edge, z) - cache.baseHeight(edge - 1, z)));
                worstInterior = Math.max(worstInterior, Math.abs(cache.baseHeight(edge - 600, z) - cache.baseHeight(edge - 601, z)));
            }
            // one block step across the edge should be no worse than a block step on the steepest faces
            assertTrue(worstSeam <= Math.max(3.0, worstInterior * 1.25),
                    "step of " + worstSeam + " blocks across the region edge (steepest interior step " + worstInterior + ")");
        } finally {
            cache.shutdown();
        }
    }
}
