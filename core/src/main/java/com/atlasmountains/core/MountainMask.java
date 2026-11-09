package com.atlasmountains.core;

/**
 * Where the mountains are, 0..1: the field layout's zone (the cells inside vanilla's mountain
 * footprint plus a fade), multiplied by an inland ramp on the continentalness so mountains fade
 * out at the coast instead of marching into the sea.
 */
public final class MountainMask {

    private final MountainSettings s;
    private final FieldLayout layout;
    private final Continents continents;

    public MountainMask(MountainSettings settings, FieldLayout layout, Continents continents) {
        this.s = settings;
        this.layout = layout;
        this.continents = continents;
    }

    public Continents continents() { return continents; }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * The zone alone, 0..1, no inland ramp. 0 until the world's erosion noise is installed (spawn
     * search and structure placement can run before that): no mountains yet.
     */
    public double zone(double x, double z) {
        double lz = layout.zone(x, z);
        return Double.isNaN(lz) ? 0 : lz;
    }

    /** Inland ramp alone, 0 at sea and on the shore, 1 from {@code nearInlandDistance} blocks inland. */
    public double inland(double x, double z) {
        return smoothstep((continentalnessAt(x, z) - s.maskInlandStart) / (s.maskInlandFull - s.maskInlandStart));
    }

    public double mask(double x, double z) {
        double m = zone(x, z);
        if (m <= 0) return m;
        return m * inland(x, z);
    }

    // ---- coarse, cached continentalness ------------------------------------------------
    // The continentalness has a wavelength of kilometres and the mask is asked for per
    // block. Sample it on a grid and interpolate.
    private static final int GRID = 32;
    private static final int CACHE_BITS = 12;
    private static final class Cache {
        final long[] keys = new long[1 << CACHE_BITS];
        final double[] values = new double[1 << CACHE_BITS];
        Cache() { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);

    private double gridValue(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        Cache cc = cache.get();
        int i = (int) (Hash.mix(key) & ((1 << CACHE_BITS) - 1));
        if (cc.keys[i] == key) return cc.values[i];
        double v = continents.continentalness(gx * (double) GRID, gz * (double) GRID);
        cc.keys[i] = key;
        cc.values[i] = v;
        return v;
    }

    /** Continentalness at a block position, bilinear from a 32-block grid. */
    public double continentalnessAt(double x, double z) {
        double fx = x / GRID, fz = z / GRID;
        int gx = (int) Math.floor(fx), gz = (int) Math.floor(fz);
        double tx = fx - gx, tz = fz - gz;
        double a = gridValue(gx, gz), b = gridValue(gx + 1, gz);
        double d = gridValue(gx, gz + 1), e = gridValue(gx + 1, gz + 1);
        return (a + (b - a) * tx) * (1 - tz) + (d + (e - d) * tx) * tz;
    }
}
