package com.atlasmountains.core;

/**
 * Where the mountains are, 0..1. Zones come from a value noise sampled in the range frame with
 * a much longer wavelength along the ranges than across them, so they are long and narrow and
 * aligned with the ranges inside them. A soft threshold gives the edge over which terrain,
 * biomes and surface blend from vanilla to ours. Multiplied by an inland ramp on the plate
 * continentalness ({@link PlateContinents}) so zones fade out at the coast instead of marching
 * into the sea.
 */
public final class MountainMask {

    private final MountainSettings s;
    private final SpineLayout layout;
    private final Continents continents;
    private final long seed;
    /** False makes every position count as inland (the "everywhere" preset and the heightmap diagnostics). */
    public volatile boolean fadeAtCoast = true;
    /**
     * (x, z) -> the world's erosion on vanilla's scale, supplied by the mod. When set (and the
     * layout has no zones of its own) the zones are vanilla's own mountain footprint: erosion
     * below {@link MountainSettings#erosionMountainBelow}, fading over erosionMaskBand.
     */
    private volatile java.util.function.DoubleBinaryOperator erosion;

    public void setErosion(java.util.function.DoubleBinaryOperator erosion) {
        this.erosion = erosion;
        for (Cache c : erosionCaches) java.util.Arrays.fill(c.keys, Long.MIN_VALUE);
    }
    public boolean hasErosion() { return erosion != null; }

    public MountainMask(MountainSettings settings, SpineLayout layout, Continents continents, long seed) {
        this.s = settings;
        this.layout = layout;
        this.continents = continents;
        this.seed = Hash.hash(seed, 1010);
    }

    public Continents continents() { return continents; }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * The zones alone, 0..1, no inland ramp: the layout's chains if it has them; else vanilla's
     * mountain footprint from the erosion field if the mod supplied one; else the stretched zone noise.
     */
    public double zone(double x, double z, double[] scratch) {
        double lz = layout.zone(x, z);
        if (erosion != null) {
            // vanilla's mountain footprint; with a chain layout the chains are what shows inside it
            // domain-warped so the biome edge wanders rather than following one smooth contour
            double w = s.footprintWarpWavelength, a = s.footprintWarpAmplitude;
            double wx = x + (Hash.value2(Hash.hash(seed, 71), x / w, z / w) - 0.5) * 2 * a;
            double wz = z + (Hash.value2(Hash.hash(seed, 72), x / w + 0.5, z / w) - 0.5) * 2 * a;
            double e = erosionAt(wx, wz);
            double footprint = smoothstep((s.erosionMountainBelow - e) / s.erosionMaskBand + 0.5);
            return Double.isNaN(lz) ? footprint : lz * footprint;
        }
        if (!Double.isNaN(lz)) return lz;
        // a footprint layout before the erosion field is installed (spawn search, structure placement): no mountains yet
        if (!(layout instanceof RangeLayout rl)) return 0;
        rl.toFrame(x, z, scratch);
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

    /** Inland ramp alone, 0 at sea and on the shore, 1 from {@code nearInlandDistance} blocks inland. */
    public double inland(double x, double z) {
        return smoothstep((continentalnessAt(x, z) - s.maskInlandStart) / (s.maskInlandFull - s.maskInlandStart));
    }

    public double mask(double x, double z, double[] scratch) {
        double m = zone(x, z, scratch);
        if (m <= 0 || !fadeAtCoast) return m;
        return m * inland(x, z);
    }

    // ---- coarse, cached continentalness ------------------------------------------------
    // The plate continentalness has a wavelength of kilometres and the mask is asked for per
    // block. Sample it on a grid and interpolate.
    private static final int GRID = 32;
    private static final int CACHE_BITS = 12;
    private static final class Cache {
        final long[] keys = new long[1 << CACHE_BITS];
        final double[] values = new double[1 << CACHE_BITS];
        Cache() { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }
    private final ThreadLocal<Cache> cache = ThreadLocal.withInitial(Cache::new);
    private final java.util.List<Cache> erosionCaches = java.util.Collections.synchronizedList(new java.util.ArrayList<>());
    private final ThreadLocal<Cache> erosionCache = ThreadLocal.withInitial(() -> { Cache c = new Cache(); erosionCaches.add(c); return c; });

    private double erosionGrid(int gx, int gz) {
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        Cache cc = erosionCache.get();
        int i = (int) (Hash.mix(key) & ((1 << CACHE_BITS) - 1));
        if (cc.keys[i] == key) return cc.values[i];
        double v = erosion.applyAsDouble(gx * (double) GRID, gz * (double) GRID);
        cc.keys[i] = key;
        cc.values[i] = v;
        return v;
    }

    /** Erosion at a block position, bilinear from a 32-block grid (the erosion noise varies over hundreds of blocks). */
    public double erosionAt(double x, double z) {
        double fx = x / GRID, fz = z / GRID;
        int gx = (int) Math.floor(fx), gz = (int) Math.floor(fz);
        double tx = fx - gx, tz = fz - gz;
        double a = erosionGrid(gx, gz), b = erosionGrid(gx + 1, gz);
        double d = erosionGrid(gx, gz + 1), e = erosionGrid(gx + 1, gz + 1);
        return (a + (b - a) * tx) * (1 - tz) + (d + (e - d) * tx) * tz;
    }

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

    /** Plate continentalness at a block position, bilinear from a 32-block grid. */
    public double continentalnessAt(double x, double z) {
        double fx = x / GRID, fz = z / GRID;
        int gx = (int) Math.floor(fx), gz = (int) Math.floor(fz);
        double tx = fx - gx, tz = fz - gz;
        double a = gridValue(gx, gz), b = gridValue(gx + 1, gz);
        double d = gridValue(gx, gz + 1), e = gridValue(gx + 1, gz + 1);
        return (a + (b - a) * tx) * (1 - tz) + (d + (e - d) * tx) * tz;
    }
}
