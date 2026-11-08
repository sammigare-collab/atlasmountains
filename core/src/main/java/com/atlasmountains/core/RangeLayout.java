package com.atlasmountains.core;

/**
 * Step 2: range spines. Ranges are gently curved, roughly parallel polylines.
 * The world is rotated by a seed-chosen angle into (u, v) coordinates: v runs along the
 * ranges, u across them. Spine k sits at u = k * spacing + jitter_k + curve_k(v).
 * For any block position this gives the nearest spine, the signed distance to it
 * and the position along it.
 */
public final class RangeLayout {

    public static final class Sample {
        /** Index of the nearest spine. */
        public int range;
        /** Distance to the nearest spine, in blocks, always >= 0. */
        public double distance;
        /** Position along the nearest spine, in blocks. */
        public double along;
        /** Distance to the second nearest spine (the other side of the valley). */
        public double distance2;
    }

    private final MountainSettings s;
    private final long seed;
    private final double cos, sin;

    public RangeLayout(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
        double angle = Hash.unit(Hash.hash(seed, 101)) * Math.PI; // 0..180 degrees
        this.cos = Math.cos(angle);
        this.sin = Math.sin(angle);
    }

    /** World (x, z) to the range frame: result[0] = u across the ranges, result[1] = v along them. */
    public void toFrame(double x, double z, double[] out) {
        out[0] = x * cos + z * sin;
        out[1] = -x * sin + z * cos;
    }

    /** Direction the ranges run, as a unit vector in world (x, z). */
    public double[] alongDirection() { return new double[] {-sin, cos}; }

    /** Per-spine constant offset. */
    private double offset(int k) {
        return Hash.signed(Hash.hash(seed, 202, k)) * s.rangeSpacingJitter;
    }

    /** Gentle curve of spine k at position v along it, and its derivative du/dv. */
    private double curve(int k, double v) {
        return Hash.value1(Hash.hash(seed, 303, k), v / s.spineCurveWavelength) * s.spineCurveAmplitude;
    }

    private double curveSlope(int k, double v) {
        double h = 2.0;
        return (curve(k, v + h) - curve(k, v - h)) / (2 * h);
    }

    /** Spine k's u coordinate at v. */
    public double spineU(int k, double v) {
        return k * s.rangeSpacing + offset(k) + curve(k, v);
    }

    public void sample(double x, double z, Sample out) {
        // rotate world -> (u across, v along)
        double u = x * cos + z * sin;
        double v = -x * sin + z * cos;
        int k0 = (int) Math.round(u / s.rangeSpacing);
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bestK = k0;
        double bestAlong = v;
        for (int k = k0 - 1; k <= k0 + 1; k++) {
            double du = u - spineU(k, v);
            double slope = curveSlope(k, v);
            double q = 1 + slope * slope;
            double dist = Math.abs(du) / Math.sqrt(q); // perpendicular distance to the locally tilted spine
            if (dist < best) {
                second = best;
                best = dist;
                bestK = k;
                // foot of the perpendicular on the spine, so "along" is orthogonal to "distance"
                bestAlong = v + slope * du / q;
            } else if (dist < second) {
                second = dist;
            }
        }
        out.range = bestK;
        out.distance = best;
        out.distance2 = second;
        out.along = bestAlong;
    }
}
