package com.atlasmountains.core;

import java.util.function.DoubleBinaryOperator;

/**
 * Where the mountains are, 0..1. Zones come from a value noise sampled in the range frame with
 * a much longer wavelength along the ranges than across them, so they are long and narrow and
 * aligned with the ranges inside them. A soft threshold gives the edge over which terrain,
 * biomes and surface blend from vanilla to ours. Multiplied by an inland ramp on vanilla
 * continentalness (supplied by the mod; absent in the diagnostic tool) so zones fade out at the
 * coast instead of marching into the sea.
 */
public final class MountainMask {

    private final MountainSettings s;
    private final RangeLayout layout;
    private final long seed;
    /** (x, z) -> vanilla continentalness. Null means "always inland". */
    public volatile DoubleBinaryOperator continentalness;

    public MountainMask(MountainSettings settings, RangeLayout layout, long seed) {
        this.s = settings;
        this.layout = layout;
        this.seed = Hash.hash(seed, 1010);
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** The zone noise alone, 0..1, no inland ramp. */
    public double zone(double x, double z, double[] scratch) {
        layout.toFrame(x, z, scratch);
        double u = scratch[0] / s.maskWavelengthAcross, v = scratch[1] / s.maskWavelengthAlong;
        double sum = 0, amp = 1, norm = 0;
        for (int o = 0; o < s.maskOctaves; o++) {
            sum += amp * Hash.value2(Hash.hash(seed, o), u, v);
            norm += amp;
            amp *= 0.5;
            u *= 2; v *= 2;
        }
        double n = sum / norm;
        return smoothstep((n - s.maskThreshold) / s.maskEdgeWidth + 0.5);
    }

    public double mask(double x, double z, double[] scratch) {
        double m = zone(x, z, scratch);
        if (m <= 0) return 0;
        DoubleBinaryOperator c = continentalness;
        if (c == null) return m;
        double inland = smoothstep((continentalnessAt(x, z, c) - s.maskInlandStart) / (s.maskInlandFull - s.maskInlandStart));
        return m * inland;
    }

    // ---- coarse, cached continentalness ------------------------------------------------
    // Vanilla's continentalness is an expensive multi-octave noise with a wavelength of
    // kilometres, and the mask is asked for per block. Sample it on a grid and interpolate.
    private static final int GRID = 32;
    private static final int CACHE_BITS = 12;
    private static final class Cache {
        final long[] keys = new long[1 << CACHE_BITS];
        final double[] values = new double[1 << CACHE_BITS];
        Cache() { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    private double gridValue(int gx, int gz, DoubleBinaryOperator c) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        Cache cc = cache.get();
        int i = (int) (Hash.mix(key) & ((1 << CACHE_BITS) - 1));
        if (cc.keys[i] == key) return cc.values[i];
        double v = c.applyAsDouble(gx * (double) GRID, gz * (double) GRID);
        cc.keys[i] = key;
        cc.values[i] = v;
        return v;
    }

    private double continentalnessAt(double x, double z, DoubleBinaryOperator c) {
        double fx = x / GRID, fz = z / GRID;
        int gx = (int) Math.floor(fx), gz = (int) Math.floor(fz);
        double tx = fx - gx, tz = fz - gz;
        double a = gridValue(gx, gz, c), b = gridValue(gx + 1, gz, c);
        double d = gridValue(gx, gz + 1, c), e = gridValue(gx + 1, gz + 1, c);
        return (a + (b - a) * tx) * (1 - tz) + (d + (e - d) * tx) * tz;
    }
}
