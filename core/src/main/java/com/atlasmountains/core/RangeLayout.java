package com.atlasmountains.core;

/**
 * Step 2: range spines. Ranges are gently curved, roughly parallel polylines.
 * The world is rotated by a seed-chosen angle into (u, v) coordinates: v runs along the
 * ranges, u across them. Spine k sits at u = k * spacing + jitter_k + curve_k(v).
 * For any block position this gives the nearest spine, the signed distance to it
 * and the position along it.
 */
public final class RangeLayout implements SpineLayout {

    public static final class Sample {
        /** Scratch array for layouts that batch per-chain work. */
        public double[] scratch = new double[4];
        /** Relief multiplier for the crest at this column (1 unless the layout sets it). */
        public double scale = 1.0;
        /** Index of the nearest spine. */
        public int range;
        /** Distance to the nearest spine, in blocks, always >= 0. */
        public double distance;
        /** Position along the nearest spine, in blocks. */
        public double along;
        /** Distance to the second nearest spine (the other side of the valley). */
        public double distance2;
        /** Index of that second spine, and the position along it. */
        public int range2;
        public double along2;
    }

    private final MountainSettings s;
    private final long seed;
    private final double cos, sin;

    public RangeLayout(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
        double angle = angle(seed);
        this.cos = Math.cos(angle);
        this.sin = Math.sin(angle);
    }

    /** The direction the ranges run, radians from +x, 0..180 degrees; the stretched erosion uses the same one. */
    public static double angle(long seed) {
        return Hash.unit(Hash.hash(seed, 101)) * Math.PI;
    }

    /** World (x, z) to the range frame: result[0] = u across the ranges, result[1] = v along them. */
    public void toFrame(double x, double z, double[] out) {
        out[0] = x * cos + z * sin;
        out[1] = -x * sin + z * cos;
    }

    /** Direction the ranges run, as a unit vector in world (x, z). */
    public double[] alongDirection() { return new double[] {-sin, cos}; }

    /**
     * Per-spine constant offset. Spines come in pairs (2j, 2j+1): a "tight" pair is pulled
     * together by pairPull so the two ranges meet back to back in a col; otherwise the pair
     * keeps the mean spacing. The gap between pairs widens by the same amount, and only some
     * of those wide gaps carry the flat valley floor (see {@link #valley}).
     */
    private double offset(int k) {
        double jitter = Hash.signed(Hash.hash(seed, 202, k)) * s.rangeSpacingJitter;
        int pair = Math.floorDiv(k, 2);
        boolean tight = Hash.unit(Hash.hash(seed, 212, pair)) < s.pairTightChance;
        if (!tight) return jitter;
        return jitter + (Math.floorMod(k, 2) == 0 ? s.pairPull : -s.pairPull);
    }

    /**
     * 1 where the gap this position lies in is a valley (flat floor and lakes are ours there),
     * 0 where vanilla terrain fills the gap instead. Decided per gap by a coin, so the little
     * valleys are occasional. Gaps inside a tight pair are never valleys (the flanks meet).
     */
    public double valley(double x, double z) {
        double u = x * cos + z * sin, v = -x * sin + z * cos;
        int k = (int) Math.floor(u / s.rangeSpacing);            // spine k is the one at or below u
        while (spineU(k, v) > u) k--;
        while (spineU(k + 1, v) <= u) k++;
        int pair = Math.floorDiv(k, 2);
        boolean insidePair = Math.floorMod(k, 2) == 0;             // gap between 2j and 2j+1
        if (insidePair && Hash.unit(Hash.hash(seed, 212, pair)) < s.pairTightChance) return 0;
        return Hash.unit(Hash.hash(seed, 223, k)) < s.valleyChance ? 1 : 0;
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

    @Override
    public void sample(double x, double z, Sample out) {
        // rotate world -> (u across, v along)
        double u = x * cos + z * sin;
        double v = -x * sin + z * cos;
        int k0 = (int) Math.round(u / s.rangeSpacing);
        double best = Double.MAX_VALUE, second = Double.MAX_VALUE;
        int bestK = k0, secondK = k0;
        double bestAlong = v, secondAlong = v;
        for (int k = k0 - 1; k <= k0 + 1; k++) {
            double du = u - spineU(k, v);
            double slope = curveSlope(k, v);
            double q = 1 + slope * slope;
            double dist = Math.abs(du) / Math.sqrt(q); // perpendicular distance to the locally tilted spine
            // foot of the perpendicular on the spine, so "along" is orthogonal to "distance"
            double along = v + slope * du / q;
            if (dist < best) {
                second = best; secondK = bestK; secondAlong = bestAlong;
                best = dist; bestK = k; bestAlong = along;
            } else if (dist < second) {
                second = dist; secondK = k; secondAlong = along;
            }
        }
        out.range = bestK;
        out.distance = best;
        out.along = bestAlong;
        out.range2 = secondK;
        out.distance2 = second;
        out.along2 = secondAlong;
    }
}
