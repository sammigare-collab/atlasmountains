package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeometryTest {

    @Test
    void generationIsDeterministic() {
        MountainSettings s = new MountainSettings();
        RegionHeightmap a = new RegionPipeline(s, 42).eroded(1, -2);
        RegionHeightmap b = new RegionPipeline(s, 42).eroded(1, -2);
        assertArrayEquals(a.height, b.height);
    }

    @Test
    void summitIsAPointThatFallsAtTheFaceAngle() {
        MountainSettings s = new MountainSettings();
        HeightmapGenerator gen = new HeightmapGenerator(s, 7);
        RangeLayout.Sample sc = new RangeLayout.Sample();
        // summit 0 of range 0 sits on the spine at along = summitAlong(0, 0); find its world position by search
        double best = -1e9, bx = 0, bz = 0;
        for (int z = -3000; z <= 3000; z += 4)
            for (int x = -3000; x <= 3000; x += 4) {
                double h = gen.baseHeightAt(x, z, sc);
                if (h > best) { best = h; bx = x; bz = z; }
            }
        for (int dz = -4; dz <= 4; dz++)
            for (int dx = -4; dx <= 4; dx++) {
                double h = gen.baseHeightAt(bx + dx, bz + dz, sc);
                if (h > best) { best = h; bx += dx; bz += dz; }
            }
        double[] across = ((RangeLayout) gen.layout()).alongDirection();
        double cx = across[1], cz = -across[0];
        double drop3 = best - gen.baseHeightAt(bx + 3 * cx, bz + 3 * cz, sc);
        assertTrue(drop3 >= 2.0, "should fall at least 2 blocks over 3 across the range, fell " + drop3);
        int wide = 0;
        for (int d = -10; d <= 10; d++) if (gen.baseHeightAt(bx + d * cx, bz + d * cz, sc) >= best - 1) wide++;
        assertTrue(wide <= 4, "apex wider than 4 blocks: " + wide);
    }

    @Test
    void valleyFloorIsFlatAndRangesAreLinear() {
        MountainSettings s = new MountainSettings();
        RegionHeightmap m = new RegionPipeline(s, 3).eroded(0, 0);
        int flat = 0, total = m.size * m.size;
        float min = Float.MAX_VALUE, max = -Float.MAX_VALUE;
        for (float h : m.height) {
            if (h <= s.valleyFloorHeight + 2.5 && h >= s.valleyFloorHeight - s.lakeDepth - 0.5) flat++;
            min = Math.min(min, h); max = Math.max(max, h);
        }
        assertTrue(flat > total * 0.05, "expected a flat valley floor");
        assertTrue(max - min > 150, "relief too small: " + (max - min));
        assertTrue(max < s.summitHeight * 1.1 + s.detailAmplitude, "summits too high: " + max);
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
