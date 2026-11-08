package com.atlasmountains.core.erosion;

import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionHeightmap;

/**
 * Step 5: slope limiter. Wherever the drop to a neighbour exceeds the critical angle,
 * material moves downhill until it does not. The critical angle is thermalRockAngle above
 * the rock line and thermalFlankAngle below it, blended over thermalBlendBand.
 * Passes are Gauss-Seidel (in place), which converges faster than a buffered update.
 */
public final class ThermalErosion {

    private final MountainSettings s;
    private final double tanRock, tanFlank;

    public ThermalErosion(MountainSettings settings) {
        this.s = settings;
        this.tanRock = Math.tan(Math.toRadians(settings.thermalRockAngle));
        this.tanFlank = Math.tan(Math.toRadians(settings.thermalFlankAngle));
    }

    /** Critical slope (tan) at height h. */
    public double critical(double h) {
        double t = (h - s.rockLineHeight()) / s.thermalBlendBand + 0.5;
        t = Math.max(0, Math.min(1, t));
        t = t * t * (3 - 2 * t);
        return tanFlank + (tanRock - tanFlank) * t;
    }

    /** Runs the given number of passes. Returns the total material moved in the last pass (blocks of height). */
    public double run(RegionHeightmap m, int passes) {
        int n = m.size;
        float[] h = m.height;
        double straight = m.spacing, diag = m.spacing * Math.sqrt(2);
        int[] dx = {1, 0, -1, 0, 1, -1, -1, 1};
        int[] dz = {0, 1, 0, -1, 1, 1, -1, -1};
        double moved = 0;
        for (int p = 0; p < passes; p++) {
            moved = 0;
            for (int z = 1; z < n - 1; z++) {
                for (int x = 1; x < n - 1; x++) {
                    int i = z * n + x;
                    double hi = h[i];
                    double crit = critical(hi);
                    for (int k = 0; k < 8; k++) {
                        int j = (z + dz[k]) * n + (x + dx[k]);
                        double dist = k < 4 ? straight : diag;
                        double d = hi - h[j];
                        double maxDrop = crit * dist;
                        if (d > maxDrop) {
                            double delta = (d - maxDrop) * s.thermalRate;
                            hi -= delta;
                            h[j] += delta;
                            moved += delta;
                        }
                    }
                    h[i] = (float) hi;
                }
            }
        }
        return moved;
    }
}
