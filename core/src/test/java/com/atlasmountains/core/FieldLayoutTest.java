package com.atlasmountains.core;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The layout both presets ship with. */
class FieldLayoutTest {

    private static final long SEED = 12345;

    @Test
    void heightScaleStaysInsideItsDocumentedRange() {
        MountainSettings s = new MountainSettings();
        FieldLayout f = new FieldLayout(s, SEED);
        double lo = s.fieldHeightBoost * (1 - s.fieldHeightVariation);
        double hi = s.fieldHeightBoost * (1 + s.fieldHeightVariation);
        Random r = new Random(1);
        double sum = 0;
        int n = 50_000;
        for (int i = 0; i < n; i++) {
            double h = f.heightScale((r.nextDouble() - 0.5) * 1e6, (r.nextDouble() - 0.5) * 1e6);
            assertTrue(h >= lo - 1e-9 && h <= hi + 1e-9, "height scale " + h + " outside " + lo + ".." + hi);
            sum += h;
        }
        // the noise is centred on 0, so the scale is centred on the boost
        assertEquals(s.fieldHeightBoost, sum / n, 0.03);
    }

    @Test
    void sampleIsDeterministic() {
        MountainSettings s = new MountainSettings();
        FieldLayout a = new FieldLayout(s, SEED), b = new FieldLayout(s, SEED);
        RangeLayout.Sample sa = new RangeLayout.Sample(), sb = new RangeLayout.Sample();
        Random r = new Random(2);
        for (int i = 0; i < 2000; i++) {
            double x = (r.nextDouble() - 0.5) * 50_000, z = (r.nextDouble() - 0.5) * 50_000;
            a.sample(x, z, sa);
            b.sample(x, z, sb);
            assertEquals(sa.distance, sb.distance);
            assertEquals(sa.along, sb.along);
            assertEquals(sa.range, sb.range);
        }
    }

    @Test
    void ridgesAreAboutOneCellApart() {
        // every column is within one cell of a ridge, so no point is further than a cell from one
        MountainSettings s = new MountainSettings();
        FieldLayout f = new FieldLayout(s, SEED);
        RangeLayout.Sample sc = new RangeLayout.Sample();
        Random r = new Random(3);
        double max = 0;
        for (int i = 0; i < 20_000; i++) {
            f.sample((r.nextDouble() - 0.5) * 100_000, (r.nextDouble() - 0.5) * 100_000, sc);
            assertTrue(sc.distance >= 0 && sc.distance2 >= sc.distance, "nearest ridge must come first");
            max = Math.max(max, sc.distance);
        }
        assertTrue(max < s.fieldCellSize, "a point " + max + " blocks from any ridge");
    }

    @Test
    void zoneFollowsTheErosionFootprint() {
        MountainSettings s = new MountainSettings();
        FieldLayout f = new FieldLayout(s, SEED);
        assertTrue(Double.isNaN(f.zone(100, 100)), "no zone before the erosion noise is installed");

        f.setErosion((x, z) -> -1.0);   // far below the threshold: every cell is mountain
        for (int i = 0; i < 100; i++) assertEquals(1.0, f.zone(i * 997.0, i * -613.0));

        f.setErosion((x, z) -> 1.0);    // far above: no cell is mountain
        for (int i = 0; i < 100; i++) assertEquals(0.0, f.zone(i * 997.0, i * -613.0));
    }

    @Test
    void zoneFadesOutsideAMountainProvince() {
        // mountains for x < 0 only: the mask is 1 deep inside, 0 far outside, and in between at the edge
        MountainSettings s = new MountainSettings();
        FieldLayout f = new FieldLayout(s, SEED);
        f.setErosion((x, z) -> x < 0 ? -1.0 : 1.0);
        double reach = s.fieldCellSize * 2 + s.halfWidth() + s.fieldEdgeFade + Math.abs(s.fieldWarpShift) + 2 * s.fieldWarpAmplitude;
        boolean sawEdge = false;
        for (int z = -5000; z <= 5000; z += 250) {
            assertEquals(1.0, f.zone(-reach, z), "deep inside at z " + z);
            assertEquals(0.0, f.zone(reach, z), "far outside at z " + z);
            for (int x = (int) -reach; x <= reach; x += 8) {
                double m = f.zone(x, z);
                assertTrue(m >= 0 && m <= 1);
                if (m > 0 && m < 1) sawEdge = true;
            }
        }
        assertTrue(sawEdge, "the edge should fade, not cut");
    }

    @Test
    void maskIsZeroAtSeaAndEqualsTheZoneFarInland() {
        MountainSettings s = new MountainSettings();
        FieldLayout f = new FieldLayout(s, SEED);
        f.setErosion((x, z) -> -1.0);
        double[] scratch = new double[2];
        MountainMask sea = new MountainMask(s, f, (x, z) -> -0.8, SEED);
        MountainMask inland = new MountainMask(s, f, (x, z) -> 0.6, SEED);
        for (int i = 0; i < 50; i++) {
            double x = i * 1301.0, z = i * -877.0;
            assertEquals(0.0, sea.mask(x, z, scratch));
            assertEquals(f.zone(x, z), inland.mask(x, z, scratch));
        }
    }
}
