package com.atlasmountains.core;

/**
 * Step 3a: height along a spine. A row of summits at jittered spacing, each with its own
 * height, joined by straight ridgelines that fall away from each apex at crestFallSlope.
 * The crest at any point is the highest of the nearby summit "tents" (abs-based, no
 * smoothing), so every apex is a point and every saddle is a sharp V in the ridgeline.
 */
public final class CrestProfile {

    private final MountainSettings s;
    private final long seed;

    public CrestProfile(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
    }

    /** Position along the spine of summit i on range k. */
    public double summitAlong(int range, long i) {
        double jitter = Hash.signed(Hash.hash(seed, 404, range, i)) * s.peakSpacingJitter * s.peakSpacing;
        return i * s.peakSpacing + jitter;
    }

    /** Height of summit i on range k. */
    public double summitHeight(int range, long i) {
        double var = Hash.signed(Hash.hash(seed, 505, range, i)) * s.peakHeightVariation;
        return s.summitHeight * (1 + var);
    }

    /**
     * Crest height at position v along range k: the two highest summit tents, joined by a
     * smooth maximum whose rounding depends on how far apart those two summits are.
     * Summits stay sharp points (the abs() is never smoothed); only the saddle is rounded,
     * and a saddle between two close summits stays a sharp V.
     */
    /** Rounding for the saddle between two summits this far apart. */
    private double roundingForGap(double gap) {
        double t = (gap - s.saddleRoundingMinGap) / (s.saddleRoundingMaxGap - s.saddleRoundingMinGap);
        t = Math.max(0, Math.min(1, t));
        return s.saddleRounding * t * t * (3 - 2 * t);
    }

    /**
     * Rounding at position v: each saddle (midpoint between summits i and i+1) has its own value
     * from the gap; at a summit the value is the mean of the two saddles either side; in between
     * it is interpolated linearly. Continuous everywhere, so the crest has no seams.
     */
    private double roundingAt(int range, double v) {
        long i = (long) Math.floor(v / s.peakSpacing);
        // find the interval [summit i, summit i+1] containing v (jitter can shift it by one)
        while (summitAlong(range, i) > v) i--;
        while (summitAlong(range, i + 1) <= v) i++;
        double a = summitAlong(range, i), b = summitAlong(range, i + 1);
        double kPrev = roundingForGap(a - summitAlong(range, i - 1));
        double kMid = roundingForGap(b - a);
        double kNext = roundingForGap(summitAlong(range, i + 2) - b);
        double kAtA = 0.5 * (kPrev + kMid), kAtB = 0.5 * (kMid + kNext);
        double mid = 0.5 * (a + b);
        if (v < mid) return kAtA + (kMid - kAtA) * (v - a) / (mid - a);
        return kMid + (kAtB - kMid) * (v - mid) / (b - mid);
    }

    public double height(int range, double v) {
        long i0 = Math.round(v / s.peakSpacing);
        double best = -Double.MAX_VALUE;
        double[] tents = new double[5];
        int n = 0;
        for (long i = i0 - 2; i <= i0 + 2; i++) {
            double h = summitHeight(range, i) - s.crestFallSlope * Math.abs(v - summitAlong(range, i));
            tents[n++] = h;
            if (h > best) best = h;
        }
        double k = roundingAt(range, v);
        if (k < 1e-3) return best;
        // smooth maximum over all nearby tents. At a summit the others are far below, so this adds
        // only a small constant with almost no slope; the abs() apex stays sharp.
        double sum = 0;
        for (int j = 0; j < n; j++) sum += Math.exp((tents[j] - best) / k);
        return best + k * Math.log(sum);
    }
}
