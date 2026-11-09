package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The mountain design itself: sharp summits, flat valley floors, heights inside the tuned range. */
class GeometryTest {

    @Test
    void generationIsDeterministic() {
        MountainSettings s = new MountainSettings();
        RegionHeightmap a = new RegionPipeline(s, 42).eroded(1, -2);
        RegionHeightmap b = new RegionPipeline(s, 42).eroded(1, -2);
        assertArrayEquals(a.height, b.height);
    }

    @Test
    void summitIsAPointThatFallsAwayInEveryDirection() {
        MountainSettings s = new MountainSettings();
        HeightmapGenerator gen = new HeightmapGenerator(s, 7);
        RidgeSample sc = new RidgeSample();
        double best = -1e9, bx = 0, bz = 0;
        for (int z = -1500; z <= 1500; z += 4)
            for (int x = -1500; x <= 1500; x += 4) {
                double h = gen.baseHeightAt(x, z, sc);
                if (h > best) { best = h; bx = x; bz = z; }
            }
        for (int dz = -4; dz <= 4; dz++)
            for (int dx = -4; dx <= 4; dx++) {
                double h = gen.baseHeightAt(bx + dx, bz + dz, sc);
                if (h > best) { best = h; bx += dx; bz += dz; }
            }
        for (int k = 0; k < 8; k++) {
            double a = k * Math.PI / 4, cx = Math.cos(a), cz = Math.sin(a);
            double drop3 = best - gen.baseHeightAt(bx + 3 * cx, bz + 3 * cz, sc);
            // along a ridge the crest falls at crestFallSlope (0.6), less a little saddle rounding;
            // a rounded dome would barely fall at all over 3 blocks
            assertTrue(drop3 >= 1.2, "should fall at least 1.2 blocks over 3 in direction " + k + ", fell " + drop3);
            int wide = 0;
            for (int d = -10; d <= 10; d++) if (gen.baseHeightAt(bx + d * cx, bz + d * cz, sc) >= best - 1) wide++;
            assertTrue(wide <= 5, "apex wider than 5 blocks in direction " + k + ": " + wide);
        }
    }

    @Test
    void valleyFloorIsFlatAndHeightsStayInRange() {
        MountainSettings s = new MountainSettings();
        RegionHeightmap m = new RegionPipeline(s, 3).eroded(0, 0);
        int flat = 0, total = m.size * m.size;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float h : m.height) {
            if (h <= s.valleyFloorHeight + 2.5 && h >= s.valleyFloorHeight - s.lakeDepth - 0.5) flat++;
            min = Math.min(min, h); max = Math.max(max, h);
        }
        // the field layout fills a province with ranges back to back, so valley floors are small
        // and occasional (under 1% of a region); this only checks that they exist and stay flat
        assertTrue(flat > total * 0.002, "expected some flat valley floor, got " + (100.0 * flat / total) + "%");
        assertTrue(max - min > 100, "relief too small: " + (max - min));
        double tallest = s.valleyFloorHeight + s.relief() * s.fieldHeightBoost * (1 + s.fieldHeightVariation)
                * (1 + s.peakHeightVariation) + s.detailAmplitude;
        assertTrue(max <= tallest, "summits above the tallest the settings allow (" + tallest + "): " + max);
        assertTrue(min >= s.valleyFloorHeight - s.lakeDepth - 1, "below the lake beds: " + min);
    }

    @Test
    void settingsOverridesByName() {
        MountainSettings s = new MountainSettings();
        SettingsOverrides.apply(s, "peakSpacing=250");
        SettingsOverrides.apply(s, "dropletsPerRegion=10");
        assertEquals(250, s.peakSpacing);
        assertEquals(10, s.dropletsPerRegion);
        assertThrows(IllegalArgumentException.class, () -> SettingsOverrides.apply(s, "nope=1"));
    }
}
