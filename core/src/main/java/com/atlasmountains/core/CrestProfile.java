package com.atlasmountains.core;

/**
 * Height along a ridge. A row of summits at jittered spacing, each with its own height, joined
 * by straight ridgelines that fall away from each apex at crestFallSlope. The crest at any point
 * is a smooth maximum of the nearby summit "tents": every apex stays a sharp point, saddles
 * between summits further apart than saddleRoundingMinGap are rounded by up to saddleRounding
 * blocks, and saddles between closer summits stay a sharp V.
 */
public final class CrestProfile {

    private final MountainSettings s;
    private final long seed;
    public CrestProfile(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
    }

    /** Position along the ridge of summit i on ridge k. */
    public double summitAlong(int ridge, long i) {
        double jitter = Hash.signed(Hash.hash(seed, 404, ridge, i)) * s.peakSpacingJitter * s.peakSpacing;
        return i * s.peakSpacing + jitter;
    }

    /** Height of summit i on ridge k. */
    public double summitHeight(int ridge, long i) {
        double var = Hash.signed(Hash.hash(seed, 505, ridge, i)) * s.peakHeightVariation;
        return s.summitHeight * (1 + var);
    }

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
    private double roundingAt(int ridge, double v) {
        long i = (long) Math.floor(v / s.peakSpacing);
        // find the interval [summit i, summit i+1] containing v (jitter can shift it by one)
        while (summitAlong(ridge, i) > v) i--;
        while (summitAlong(ridge, i + 1) <= v) i++;
        double a = summitAlong(ridge, i), b = summitAlong(ridge, i + 1);
        double kPrev = roundingForGap(a - summitAlong(ridge, i - 1));
        double kMid = roundingForGap(b - a);
        double kNext = roundingForGap(summitAlong(ridge, i + 2) - b);
        double kAtA = 0.5 * (kPrev + kMid), kAtB = 0.5 * (kMid + kNext);
        double mid = 0.5 * (a + b);
        if (v < mid) return kAtA + (kMid - kAtA) * (v - a) / (mid - a);
        return kMid + (kAtB - kMid) * (v - mid) / (b - mid);
    }

    /**
     * Crest height at position v along ridge k: the summit tents nearby, joined by a smooth
     * maximum whose rounding depends on how far apart the neighbouring summits are. Summits stay
     * sharp points (the abs() is never smoothed); only the saddle is rounded.
     */
    public double height(int ridge, double v) {
        long i0 = Math.round(v / s.peakSpacing);
        double best = -Double.MAX_VALUE;
        double[] tents = new double[5];
        int n = 0;
        for (long i = i0 - 2; i <= i0 + 2; i++) {
            double h = summitHeight(ridge, i) - s.crestFallSlope * Math.abs(v - summitAlong(ridge, i));
            tents[n++] = h;
            if (h > best) best = h;
        }
        double k = roundingAt(ridge, v);
        if (k < 1e-3) return best;
        // smooth maximum over all nearby tents. At a summit the others are far below, so this adds
        // only a small constant with almost no slope; the abs() apex stays sharp.
        double sum = 0;
        for (int j = 0; j < n; j++) sum += Math.exp((tents[j] - best) / k);
        return best + k * Math.log(sum);
    }
}
