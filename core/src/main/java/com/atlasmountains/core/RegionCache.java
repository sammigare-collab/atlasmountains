package com.atlasmountains.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Step 8: eroded regions computed once, off the calling thread, cached in memory and on disk,
 * and sampled at block resolution with bicubic interpolation plus a small high-frequency noise.
 * Neighbouring regions are blended across their overlap so erosion differences do not show
 * as seams at region boundaries.
 *
 * Pure Java; the mod wraps this and the diagnostic tool can use it too.
 */
public final class RegionCache {

    private final MountainSettings s;
    private final long seed;
    private final RegionPipeline pipeline;
    private final Path diskDir;            // may be null: memory only
    private final Map<Long, CompletableFuture<RegionHeightmap>> regions = new ConcurrentHashMap<>();
    private final ExecutorService executor;
    private final long noiseSeed;
    private final Continents continents;
    private final MountainMask mask;
    private final ThreadLocal<double[]> maskScratch = ThreadLocal.withInitial(() -> new double[2]);
    public Consumer<String> log = msg -> {};

    /** Amplitude of the block-resolution noise that hides the 4-block grid. */
    public double upsampleNoiseAmplitude = 1.5;
    /** Wavelength of that noise, blocks. */
    public double upsampleNoiseWavelength = 7;

    public RegionCache(MountainSettings settings, long seed, Path diskDir) {
        this.s = settings;
        this.seed = seed;
        this.pipeline = new RegionPipeline(settings, seed);
        this.diskDir = diskDir;
        this.noiseSeed = Hash.hash(seed, 909);
        this.continents = Continents.create(settings, seed);
        this.mask = new MountainMask(settings, pipeline.generator().layout(), continents, seed);
        int threads = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        this.executor = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "atlasmountains-erosion");
            t.setDaemon(true);
            return t;
        });
    }

    public MountainSettings settings() { return s; }
    public long seed() { return seed; }
    public MountainMask mountainMask() { return mask; }
    public Continents continents() { return continents; }
    /** The chain layout, or null when this cache uses parallel spines. */
    public SpineLayout layout() { return pipeline.generator().layout(); }

    public ChainLayout chainLayout() { return pipeline.generator().layout() instanceof ChainLayout c ? c : null; }

    private final ThreadLocal<ColumnCache> maskColumnCache = ThreadLocal.withInitial(ColumnCache::new);

    /** Mountain mask 0..1 at a block position (cheap, no region needed). Cached per block column. */
    public double mask(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        ColumnCache c = maskColumnCache.get();
        int i = (int) (Hash.mix(key) & ((1 << COLUMN_CACHE_BITS) - 1));
        if (c.keys[i] == key) return c.values[i];
        double v = mask.mask(bx, bz, maskScratch.get());
        c.keys[i] = key;
        c.values[i] = v;
        return v;
    }

    private final ThreadLocal<ColumnCache> innerColumnCache = ThreadLocal.withInitial(ColumnCache::new);

    /**
     * Inner mask 0..1: the zone mask, further limited to the ground between a chain's spines
     * (valley floors). 0 under the outer flanks, where vanilla terrain carries on beneath ours.
     * Always equals the zone mask for the parallel layout.
     */
    public double innerMask(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        ColumnCache c = innerColumnCache.get();
        int i = (int) (Hash.mix(key) & ((1 << COLUMN_CACHE_BITS) - 1));
        if (c.keys[i] == key) return c.values[i];
        double m = mask(bx, bz);
        SpineLayout layout = pipeline.generator().layout();
        double v = m <= 0 ? 0
                 : layout instanceof ChainLayout chains ? m * chains.inner(bx, bz)
                 : layout instanceof RangeLayout rl ? m * rl.valley(bx, bz)
                 : m;
        c.keys[i] = key;
        c.values[i] = v;
        return v;
    }

    /**
     * True where this column is "ours": on the valley floor between spines (inner mask), or on
     * our flank (the masked analytic height stands above the valley floor by more than
     * {@code aboveFloor} blocks). Used by the surface rules and the biome source so the foot of
     * an outer flank hands over to vanilla's surface and biome.
     */
    public boolean onOurSlope(double x, double z, double aboveFloor, RangeLayout.Sample scratch) {
        if (innerMask(x, z) >= 0.5) return true;
        if (mask(x, z) <= 0) return false;
        return maskedBaseHeight(x, z, scratch) >= s.valleyFloorHeight + aboveFloor;
    }

    /**
     * Height with the relief scaled by the mountain mask, so at a zone edge the range tails off
     * into hills instead of standing as a cliff against vanilla terrain. Equals height() inside a zone.
     */
    public double maskedHeight(double x, double z) {
        double m = mask(x, z);
        if (m <= 0) return s.valleyFloorHeight;
        double h = height(x, z);
        return s.valleyFloorHeight + m * (h - s.valleyFloorHeight);
    }

    /** Same for the analytic model. */
    public double maskedBaseHeight(double x, double z, RangeLayout.Sample scratch) {
        double m = mask(x, z);
        if (m <= 0) return s.valleyFloorHeight;
        double h = generator().heightAt(x, z, scratch);
        return s.valleyFloorHeight + m * (h - s.valleyFloorHeight);
    }

    /** The un-eroded analytic generator (cheap, no region needed). */
    public HeightmapGenerator generator() { return pipeline.generator(); }

    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    public boolean isCached(int rx, int rz) {
        CompletableFuture<RegionHeightmap> f = regions.get(key(rx, rz));
        return f != null && f.isDone();
    }

    /** The region, computing it (or loading it from disk) on first request. Blocks until ready. */
    public RegionHeightmap region(int rx, int rz) {
        return regions.computeIfAbsent(key(rx, rz), k -> CompletableFuture.supplyAsync(() -> load(rx, rz), executor)).join();
    }

    /** Start computing without waiting. */
    public void prefetch(int rx, int rz) {
        regions.computeIfAbsent(key(rx, rz), k -> CompletableFuture.supplyAsync(() -> load(rx, rz), executor));
    }

    private Path file(int rx, int rz) {
        return diskDir.resolve("r." + rx + "." + rz + ".f32");
    }

    private RegionHeightmap load(int rx, int rz) {
        if (diskDir != null) {
            Path f = file(rx, rz);
            if (Files.exists(f)) {
                try {
                    RegionHeightmap m = new RegionHeightmap(s, rx, rz);
                    byte[] bytes = Files.readAllBytes(f);
                    if (bytes.length == m.height.length * 4) {
                        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(m.height);
                        log.accept("loaded region " + rx + "," + rz + " from disk");
                        return m;
                    }
                    log.accept("region file " + f + " has the wrong size, regenerating");
                } catch (IOException e) {
                    log.accept("could not read " + f + ": " + e + ", regenerating");
                }
            }
        }
        long t0 = System.nanoTime();
        RegionHeightmap m = pipeline.eroded(rx, rz);
        log.accept(String.format("generated region %d,%d in %.1f s", rx, rz, (System.nanoTime() - t0) / 1e9));
        if (diskDir != null) {
            try {
                Files.createDirectories(diskDir);
                ByteBuffer bb = ByteBuffer.allocate(m.height.length * 4).order(ByteOrder.LITTLE_ENDIAN);
                bb.asFloatBuffer().put(m.height);
                Path tmp = file(rx, rz).resolveSibling("r." + rx + "." + rz + ".tmp");
                Files.write(tmp, bb.array());
                Files.move(tmp, file(rx, rz), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.accept("could not write region cache: " + e);
            }
        }
        return m;
    }

    private int regionCoord(double v) {
        return (int) Math.floor(v / s.regionSizeBlocks);
    }

    /**
     * Blended, upsampled terrain height at a block position, including the fine noise.
     * Regions whose padded extent covers the point are weighted by how far inside that
     * padded extent the point lies, so the hand-over across the overlap is smooth.
     */
    public double height(double x, double z) {
        return baseHeight(x, z) + fineNoise(x, z);
    }

    /**
     * Per-thread direct-mapped cache of baseHeight by block column. Chunk generation asks for
     * the same column hundreds of times (once per y cell, plus aquifer and surface-rule
     * lookups), and vanilla's own caches either miss (cache_2d, because the block loop varies
     * x and z innermost) or quantise to 4 blocks (flat_cache).
     */
    private static final int COLUMN_CACHE_BITS = 12;
    private static final class ColumnCache {
        final long[] keys = new long[1 << COLUMN_CACHE_BITS];
        final double[] values = new double[1 << COLUMN_CACHE_BITS];
        ColumnCache() { java.util.Arrays.fill(keys, Long.MIN_VALUE); }
    }
    private final ThreadLocal<ColumnCache> columnCache = ThreadLocal.withInitial(ColumnCache::new);

    /** Blended, upsampled height without the fine noise (for slope and debug readouts). Cached per block column. */
    public double baseHeight(double x, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (bx == x && bz == z) {
            long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
            ColumnCache c = columnCache.get();
            int i = (int) (Hash.mix(key) & ((1 << COLUMN_CACHE_BITS) - 1));
            if (c.keys[i] == key) return c.values[i];
            double v = baseHeightUncached(x, z);
            c.keys[i] = key;
            c.values[i] = v;
            return v;
        }
        return baseHeightUncached(x, z);
    }

    private double baseHeightUncached(double x, double z) {
        int rx = regionCoord(x), rz = regionCoord(z);
        double pad = s.overlapSamples * (double) s.sampleSpacing;
        double size = s.regionSizeBlocks;
        double sum = 0, wsum = 0;
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                int cx = rx + dx, cz = rz + dz;
                double x0 = cx * size - pad, x1 = (cx + 1) * size + pad;
                double z0 = cz * size - pad, z1 = (cz + 1) * size + pad;
                if (x < x0 || x >= x1 || z < z0 || z >= z1) continue;
                double inX = Math.min(x - x0, x1 - x), inZ = Math.min(z - z0, z1 - z);
                double w = Math.min(1.0, Math.min(inX, inZ) / pad);
                if (w <= 0) continue;
                sum += w * region(cx, cz).sample(x, z);
                wsum += w;
            }
        }
        // start eroding the regions the player is heading for before they reach the overlap
        double margin = prefetchMargin;
        double lx = x - rx * size, lz = z - rz * size;
        if (lx < margin) prefetch(rx - 1, rz);
        if (lx > size - margin) prefetch(rx + 1, rz);
        if (lz < margin) prefetch(rx, rz - 1);
        if (lz > size - margin) prefetch(rx, rz + 1);
        if (wsum == 0) return region(rx, rz).sample(x, z);
        return sum / wsum;
    }

    /** Neighbouring regions start computing when the sampled position is within this many blocks of the edge. */
    public double prefetchMargin = 700;

    private double fineNoise(double x, double z) {
        double f = 1.0 / upsampleNoiseWavelength;
        double n = Hash.value2(noiseSeed, x * f, z * f) * 0.65 + Hash.value2(noiseSeed + 1, x * f * 2.1, z * f * 2.1) * 0.35;
        return n * upsampleNoiseAmplitude;
    }

    /** Slope in degrees at a block position, from the blended height over a 2-block baseline. */
    public double slopeDegrees(double x, double z) {
        double m = mask(x, z);
        double dx = (baseHeight(x + 1, z) - baseHeight(x - 1, z)) / 2.0 * m;
        double dz = (baseHeight(x, z + 1) - baseHeight(x, z - 1)) / 2.0 * m;
        return Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dz * dz)));
    }

    public List<String> cachedRegionNames() {
        List<String> out = new ArrayList<>();
        regions.forEach((k, f) -> { if (f.isDone()) out.add(((int) (k >> 32)) + "," + ((int) k.longValue())); });
        return out;
    }

    public void shutdown() {
        executor.shutdownNow();
    }
}
