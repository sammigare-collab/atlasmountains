package com.atlasmountains.core;

/**
 * Deterministic integer hashing and simple value noise. No state, no allocation.
 * <p>
 * Everything random in the generator (site positions, summit heights, warp, lakes) comes from
 * here, as a hash of the seed and coordinates. That is what makes generation order-independent:
 * a chunk generated first or last, on any thread, gets the same terrain.
 */
public final class Hash {
    private Hash() {}

    /** The SplitMix64 / MurmurHash3 finaliser: scrambles all 64 bits so nearby inputs give unrelated outputs. */
    public static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    /** Hash of a seed and one value. The constants are the golden-ratio and other odd 64-bit numbers, to separate arguments. */
    public static long hash(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    /** Hash of a seed and two values (for example a grid cell). Order matters: hash(s, a, b) != hash(s, b, a). */
    public static long hash(long seed, long a, long b) {
        return mix(hash(seed, a) ^ mix(b + 0x632BE59BD9B4E019L));
    }

    /** Hash of a seed and three values. */
    public static long hash(long seed, long a, long b, long c) {
        return mix(hash(seed, a, b) ^ mix(c + 0x1D8E4E27C47D124FL));
    }

    /** Uniform in [0,1): the top 53 bits as a double's mantissa. */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [-1,1). */
    public static double signed(long h) {
        return unit(h) * 2.0 - 1.0;
    }

    /** Smoothstep without clamping (callers pass 0..1), so value noise has no kinks at lattice points. */
    private static double smooth(double t) { return t * t * (3 - 2 * t); }

    /** 1D value noise in [-1,1], continuous, period 1 per lattice step. */
    public static double value1(long seed, double x) {
        double fx = Math.floor(x);
        long ix = (long) fx;
        double t = smooth(x - fx);
        double a = signed(hash(seed, ix));
        double b = signed(hash(seed, ix + 1));
        return a + (b - a) * t;
    }

    /**
     * 2D value noise in [-1,1]: random values at integer lattice points, blended with smoothstep
     * weights. Values cluster near 0 (the blend averages corners), so extremes are rare. Callers
     * divide coordinates by a wavelength to set the scale.
     */
    public static double value2(long seed, double x, double y) {
        double fx = Math.floor(x), fy = Math.floor(y);
        long ix = (long) fx, iy = (long) fy;
        double tx = smooth(x - fx), ty = smooth(y - fy);
        double a = signed(hash(seed, ix, iy));
        double b = signed(hash(seed, ix + 1, iy));
        double c = signed(hash(seed, ix, iy + 1));
        double d = signed(hash(seed, ix + 1, iy + 1));
        double ab = a + (b - a) * tx;
        double cd = c + (d - c) * tx;
        return ab + (cd - ab) * ty;
    }
}
