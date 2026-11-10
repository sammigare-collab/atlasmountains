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
 * <p>
 * One droplet's life, step by step:
 * <ol>
 *   <li>Read the height and gradient under it, bilinearly from the four surrounding samples.</li>
 *   <li>Turn towards downhill, keeping {@code dropletInertia} of the old direction, and move one
 *       sample along the new direction.</li>
 *   <li>Compare the height there: a drop means it is running downhill. Its carrying capacity is
 *       drop x speed x water x {@code dropletCapacity}.</li>
 *   <li>If it carries more than that, or went uphill into a pit, it deposits; otherwise it
 *       erodes, taking material from a small round brush around it (not one sample, which would
 *       dig needle holes).</li>
 *   <li>Speed up by the drop (gravity), lose some water (evaporation), repeat until it leaves
 *       the grid, stops on flat ground or reaches {@code dropletMaxLifetime}.</li>
 * </ol>
 * Two limits keep it to the look wanted: erosion fades out below the rock line
 * ({@link #erodeWeight}), so gullies stay on the faces and the grass flanks stay smooth; and
 * nothing is ever dug below the lake bed depth, so droplets cannot cut holes through the floor.
 */
public final class HydraulicErosion {

    private final MountainSettings s;

    public HydraulicErosion(MountainSettings settings) {
        this.s = settings;
    }

    /**
     * 1 on the rock faces, 0 on the lower flanks, smooth in between: a smoothstep from
     * (rock line - hydraulicErodeBelowRockLine - hydraulicErodeBand) up to
     * (rock line - hydraulicErodeBelowRockLine).
     */
    private double erodeWeight(double h) {
        double top = s.rockLineHeight() - s.hydraulicErodeBelowRockLine;
        double t = (h - (top - s.hydraulicErodeBand)) / s.hydraulicErodeBand;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Runs dropletsPerRegion droplets over the region, in place. */
    public void run(RegionHeightmap m, long seed) {
        // seeded per region, so a region always erodes the same way
        Random rng = new Random(Hash.hash(seed, 808, m.regionX, m.regionZ));
        int n = m.size;
        float[] h = m.height;
        int r = s.dropletRadius;

        // precompute brush weights: a cone of radius r + 0.5 samples, normalised to sum to 1, so
        // the brush moves exactly the amount asked for
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
            // start anywhere except the outermost sample ring (the gradient needs a neighbour)
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

                // new direction: mostly downhill (-gradient), a little of the old direction
                dirX = dirX * s.dropletInertia - gx * (1 - s.dropletInertia);
                dirZ = dirZ * s.dropletInertia - gz * (1 - s.dropletInertia);
                double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (len < 1e-9) break;   // perfectly flat: nowhere to go
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
                    // erode: a fraction of the spare capacity, never more than the drop (which would
                    // dig the spot below where the droplet goes next) nor the per-step cap
                    double amount = Math.min(Math.min((capacity - sediment) * s.dropletErode, -deltaH), s.dropletMaxErodePerStep);
                    amount *= erodeWeight(height);
                    for (int bz = -r; bz <= r; bz++) {
                        for (int bx = -r; bx <= r; bx++) {
                            int cx = ix + bx, cz = iz + bz;
                            if (cx < 0 || cz < 0 || cx >= n || cz >= n) continue;
                            double w = amount * brush[(bz + r) * bw + (bx + r)];
                            int ci = cz * n + cx;
                            // never dig below the deepest lake bed
                            double take = Math.min(w, Math.max(0, h[ci] - s.valleyFloorHeight + s.lakeDepth));
                            h[ci] -= take;
                            sediment += take;
                        }
                    }
                }

                // v^2 = v0^2 + drop * gravity, capped
                speed = Math.min(s.dropletMaxSpeed, Math.sqrt(Math.max(0, speed * speed + (-deltaH) * s.dropletGravity)));
                water *= (1 - s.dropletEvaporate);
                px = nx; pz = nz;
            }
        }
    }
}
