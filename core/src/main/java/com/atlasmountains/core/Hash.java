package com.atlasmountains.core;

/** Deterministic integer hashing and simple value noise. No state, no allocation. */
public final class Hash {
    private Hash() {}

    public static long mix(long h) {
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        h *= 0xc4ceb9fe1a85ec53L;
        h ^= h >>> 33;
        return h;
    }

    public static long hash(long seed, long a) {
        return mix(seed ^ mix(a + 0x9E3779B97F4A7C15L));
    }

    public static long hash(long seed, long a, long b) {
        return mix(hash(seed, a) ^ mix(b + 0x632BE59BD9B4E019L));
    }

    public static long hash(long seed, long a, long b, long c) {
        return mix(hash(seed, a, b) ^ mix(c + 0x1D8E4E27C47D124FL));
    }

    /** Uniform in [0,1). */
    public static double unit(long h) {
        return (h >>> 11) * 0x1.0p-53;
    }

    /** Uniform in [-1,1). */
    public static double signed(long h) {
        return unit(h) * 2.0 - 1.0;
    }

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

    /** 2D value noise in [-1,1]. */
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
