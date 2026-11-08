package com.atlasmountains.core.erosion;

import com.atlasmountains.core.Hash;
import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionHeightmap;

import java.util.Random;

/**
 * Step 6: particle droplet erosion after Hans Beyer (2015). Each droplet starts at a random
 * sample, flows downhill with a little inertia, erodes where it is fast and under capacity,
 * deposits where it slows. Heights are in blocks and positions in samples; gradients are
 * taken per sample so the behaviour is the same whatever the sample spacing.
 * Deterministic for a given seed and region.
 */
public final class HydraulicErosion {

    private final MountainSettings s;

    public HydraulicErosion(MountainSettings settings) {
        this.s = settings;
    }

    /** 1 on the rock faces, 0 on the lower flanks, smooth in between. */
    private double erodeWeight(double h) {
        double top = s.rockLineHeight() - s.hydraulicErodeBelowRockLine;
        double t = (h - (top - s.hydraulicErodeBand)) / s.hydraulicErodeBand;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    public void run(RegionHeightmap m, long seed) {
        Random rng = new Random(Hash.hash(seed, 808, m.regionX, m.regionZ));
        int n = m.size;
        float[] h = m.height;
        int r = s.dropletRadius;

        // precompute brush weights
        int bw = 2 * r + 1;
        double[] brush = new double[bw * bw];
        double sum = 0;
        for (int bz = -r; bz <= r; bz++)
            for (int bx = -r; bx <= r; bx++) {
                double d = Math.sqrt(bx * bx + bz * bz);
                double w = Math.max(0, r + 0.5 - d);
                brush[(bz + r) * bw + (bx + r)] = w;
                sum += w;
            }
        for (int i = 0; i < brush.length; i++) brush[i] /= sum;

        for (int d = 0; d < s.dropletsPerRegion; d++) {
            double px = 1 + rng.nextDouble() * (n - 3);
            double pz = 1 + rng.nextDouble() * (n - 3);
            double dirX = 0, dirZ = 0;
            double speed = s.dropletInitialSpeed;
            double water = s.dropletInitialWater;
            double sediment = 0;

            for (int life = 0; life < s.dropletMaxLifetime; life++) {
                int ix = (int) px, iz = (int) pz;
                double fx = px - ix, fz = pz - iz;
                if (ix < 1 || iz < 1 || ix >= n - 2 || iz >= n - 2) break;

                // bilinear height and gradient at the droplet
                int i00 = iz * n + ix;
                double h00 = h[i00], h10 = h[i00 + 1], h01 = h[i00 + n], h11 = h[i00 + n + 1];
                double gx = ((h10 - h00) * (1 - fz) + (h11 - h01) * fz) / m.spacing; // blocks per block
                double gz = ((h01 - h00) * (1 - fx) + (h11 - h10) * fx) / m.spacing;
                double height = h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz;

                dirX = dirX * s.dropletInertia - gx * (1 - s.dropletInertia);
                dirZ = dirZ * s.dropletInertia - gz * (1 - s.dropletInertia);
                double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (len < 1e-9) break;
                dirX /= len; dirZ /= len;

                double nx = px + dirX, nz = pz + dirZ;
                int jx = (int) nx, jz = (int) nz;
                if (jx < 1 || jz < 1 || jx >= n - 2 || jz >= n - 2) break;
                double gfx = nx - jx, gfz = nz - jz;
                int j00 = jz * n + jx;
                double newHeight = h[j00] * (1 - gfx) * (1 - gfz) + h[j00 + 1] * gfx * (1 - gfz)
                        + h[j00 + n] * (1 - gfx) * gfz + h[j00 + n + 1] * gfx * gfz;
                double deltaH = newHeight - height;

                double capacity = Math.max(-deltaH, s.dropletMinSlopeCapacity) * speed * water * s.dropletCapacity;

                if (sediment > capacity || deltaH > 0) {
                    // deposit: fill the pit if we went uphill, else a fraction of the surplus
                    double amount = deltaH > 0 ? Math.min(deltaH, sediment) : (sediment - capacity) * s.dropletDeposit;
                    sediment -= amount;
                    h[i00] += amount * (1 - fx) * (1 - fz);
                    h[i00 + 1] += amount * fx * (1 - fz);
                    h[i00 + n] += amount * (1 - fx) * fz;
                    h[i00 + n + 1] += amount * fx * fz;
                } else {
                    double amount = Math.min(Math.min((capacity - sediment) * s.dropletErode, -deltaH), s.dropletMaxErodePerStep);
                    amount *= erodeWeight(height);
                    for (int bz = -r; bz <= r; bz++) {
                        for (int bx = -r; bx <= r; bx++) {
                            int cx = ix + bx, cz = iz + bz;
                            if (cx < 0 || cz < 0 || cx >= n || cz >= n) continue;
                            double w = amount * brush[(bz + r) * bw + (bx + r)];
                            int ci = cz * n + cx;
                            double take = Math.min(w, Math.max(0, h[ci] - s.valleyFloorHeight + s.lakeDepth));
                            h[ci] -= take;
                            sediment += take;
                        }
                    }
                }

                speed = Math.min(s.dropletMaxSpeed, Math.sqrt(Math.max(0, speed * speed + (-deltaH) * s.dropletGravity)));
                water *= (1 - s.dropletEvaporate);
                px = nx; pz = nz;
            }
        }
    }
}
