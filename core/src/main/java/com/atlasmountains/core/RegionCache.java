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
 * The terrain height Minecraft asks for, served fast.
 *
 * <h2>What it does</h2>
 * Erosion is far too slow to run per block, so the world is cut into square regions
 * ({@link MountainSettings#regionSizeBlocks} on a side). The first time any block of a region is
 * needed, the whole region is generated and eroded once on a background thread
 * ({@link RegionPipeline}), kept in memory, and written to disk under the world folder so the next
 * session loads it instead of eroding again. Heights are then read at block resolution by
 * bicubic interpolation of the 4-block sample grid ({@link RegionHeightmap#sample}), plus a small
 * high-frequency noise that hides the grid.
 *
 * <h2>Seams</h2>
 * Each region is eroded with an overlap of {@link MountainSettings#overlapSamples} samples on every
 * side. Two neighbouring regions therefore both cover the strip along their shared edge, and
 * erode it slightly differently (droplets near the edge come from different places). Inside
 * that strip the height is a weighted average of every region covering the point, the weight
 * rising from 0 at a region's padded edge to 1 a full overlap inside it, so one region's result
 * fades into the next and no step shows.
 *
 * <h2>The mask</h2>
 * {@link #mask} says how much of our terrain applies at a position: 1 inside vanilla's mountain
 * footprint, 0 outside, fading between (see {@link MountainMask}). {@link #maskedHeight} and
 * {@link #maskedBaseHeight} apply it; the "everywhere" preset sets it to 1 everywhere.
 *
 * <h2>Threads</h2>
 * Chunk generation calls this from many worker threads at once. Region futures live in a
 * concurrent map, so each region is eroded exactly once however many threads ask; the per-column
 * caches are thread-local, so they need no locking.
 *
 * Pure Java with no Minecraft classes: the mod wraps it, and the diagnostic tool and tests use it
 * directly.
 */
public final class RegionCache {

    private final MountainSettings s;
    private final long seed;
    private final RegionPipeline pipeline;
    private final Path diskDir;            // may be null: memory only (tests and the diagnostic tool)
    /** Regions by packed (rx, rz) key; a future so that a region being eroded is not started twice. */
    private final Map<Long, CompletableFuture<RegionHeightmap>> regions = new ConcurrentHashMap<>();
    /** Erosion workers: one fewer than the cores, so the server thread keeps one to itself. */
    private final ExecutorService executor;
    /** Seed of the fine noise that hides the sample grid. */
    private final long noiseSeed;
    private final Continents continents;
    private final MountainMask mask;
    /** The "everywhere" preset: no footprint and no coast, the mask is 1 everywhere. */
    private final boolean everywhere;
    /** Where progress messages go (region generated or loaded); the mod points it at its logger. */
    public Consumer<String> log = msg -> {};

    /**
     * Amplitude of the block-resolution noise that hides the 4-block grid, blocks either way.
     * Without it, long faces show the interpolation grid as faint regular terraces.
     */
    public double upsampleNoiseAmplitude = 1.5;
    /** Wavelength of that noise, blocks. */
    public double upsampleNoiseWavelength = 7;

    /** A cache for the mixed preset (mask from vanilla's mountain footprint and the coast). */
    public RegionCache(MountainSettings settings, long seed, Path diskDir) {
        this(settings, seed, diskDir, false);
    }

    /** @param everywhere true for the "everywhere" preset: the mask is 1 at every position. */
    public RegionCache(MountainSettings settings, long seed, Path diskDir, boolean everywhere) {
        this.everywhere = everywhere;
        this.s = settings;
        this.seed = seed;
        this.pipeline = new RegionPipeline(settings, seed);
        this.diskDir = diskDir;
        this.noiseSeed = Hash.hash(seed, 909);
        this.continents = Continents.create(settings, seed);
        this.mask = new MountainMask(settings, pipeline.generator().layout(), continents);
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
    /** The layout placing this cache's ridges. */
    public FieldLayout layout() { return pipeline.generator().layout(); }

    private final ThreadLocal<ColumnCache> maskColumnCache = ThreadLocal.withInitial(ColumnCache::new);

    /**
     * Mountain mask 0..1 at a block position (cheap, no region needed). Cached per block column,
     * because the mask is read for every block of a column by several density functions and rules.
     * The cache is direct-mapped: a column hashes to one slot, and a different column in the same
     * slot simply replaces it.
     */
    public double mask(double x, double z) {
        if (everywhere) return 1;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        long key = ((long) bx << 32) ^ (bz & 0xffffffffL);
        ColumnCache c = maskColumnCache.get();
        int i = (int) (Hash.mix(key) & ((1 << COLUMN_CACHE_BITS) - 1));
        if (c.keys[i] == key) return c.values[i];
        double v = mask.mask(bx, bz);
        c.keys[i] = key;
        c.values[i] = v;
        return v;
    }

    /**
     * True where this column is "ours": well inside the mask, or on our flank (the masked analytic
     * height stands above the valley floor by more than {@code aboveFloor} blocks). Used by the
     * surface rules and the biome source so the foot of an outer flank hands over to vanilla's
     * surface and biome.
     */
    public boolean onOurSlope(double x, double z, double aboveFloor, RidgeSample scratch) {
        if (mask(x, z) >= 0.5) return true;
        if (mask(x, z) <= 0) return false;
        return maskedBaseHeight(x, z, scratch) >= s.valleyFloorHeight + aboveFloor;
    }

    /**
     * Height with the relief scaled by the mountain mask, so at a zone edge the range tails off
     * into hills instead of standing as a cliff against vanilla terrain. Equals height() inside a zone.
     * <p>
     * Formula: {@code floor + m (h - floor) - (1 - m) maskSink}. At m = 1 that is h; at m = 0 it is
     * maskSink blocks under the valley floor, below vanilla's surface almost everywhere, so the
     * blend with vanilla (in the datapack) shows vanilla there. See {@link #maskedBaseHeight}.
     */
    public double maskedHeight(double x, double z) {
        double m = mask(x, z);
        if (m <= 0) return s.valleyFloorHeight - s.maskSink;
        double h = height(x, z);
        return s.valleyFloorHeight + m * (h - s.valleyFloorHeight) - (1 - m) * s.maskSink;
    }

    /**
     * Same for the analytic model. Both sink the result below the valley floor as the mask fades
     * (by maskSink at mask 0), so in the fade band our surface drops under vanilla's and vanilla
     * takes over by height; without this the valley-floor plate at y 64 stayed solid out to the
     * last quart with any mask at all and ended in a vertical face wherever vanilla was lower, such
     * as every shoreline.
     */
    public double maskedBaseHeight(double x, double z, RidgeSample scratch) {
        double m = mask(x, z);
        if (m <= 0) return s.valleyFloorHeight - s.maskSink;
        double h = generator().heightAt(x, z, scratch);
        return s.valleyFloorHeight + m * (h - s.valleyFloorHeight) - (1 - m) * s.maskSink;
    }

    /**
     * The height of this column's own summit ({@link HeightmapGenerator#peakHeightAt}), with the
     * relief scaled by the mask the same way as {@link #maskedHeight}, so it agrees with the
     * terrain at province edges where the mountains are scaled down.
     */
    public double maskedPeakHeight(double x, double z, RidgeSample scratch) {
        double m = mask(x, z);
        if (m <= 0) return s.valleyFloorHeight - s.maskSink;
        double h = generator().peakHeightAt(x, z, scratch);
        return s.valleyFloorHeight + m * (h - s.valleyFloorHeight) - (1 - m) * s.maskSink;
    }

    /** The un-eroded analytic generator (cheap, no region needed). */
    public HeightmapGenerator generator() { return pipeline.generator(); }

    /** Bumped whenever a change alters the generated terrain: 2 = junction summits, 3 = real edges outside the footprint (Nov 2026). */
    public static final int TERRAIN_VERSION = 6;

    /** Packs region coordinates into one map key: rx in the high 32 bits, rz in the low. */
    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    /** True if the region is ready (eroded or loaded), without starting it. */
    public boolean isCached(int rx, int rz) {
        CompletableFuture<RegionHeightmap> f = regions.get(key(rx, rz));
        return f != null && f.isDone();
    }

    /** The region, computing it (or loading it from disk) on first request. Blocks until ready. */
    public RegionHeightmap region(int rx, int rz) {
        return regions.computeIfAbsent(key(rx, rz), k -> CompletableFuture.supplyAsync(() -> load(rx, rz), executor)).join();
    }

    /** Start computing a region without waiting for it; a no-op if it is ready or already running. */
    public void prefetch(int rx, int rz) {
        regions.computeIfAbsent(key(rx, rz), k -> CompletableFuture.supplyAsync(() -> load(rx, rz), executor));
    }

    /** The disk cache file of a region: raw little-endian floats, the whole padded grid. */
    private Path file(int rx, int rz) {
        // TERRAIN_VERSION is in the name so a world never mixes regions eroded from an older
        // terrain model with the analytic model of a newer build (that showed as seams)
        return diskDir.resolve("r" + TERRAIN_VERSION + "." + rx + "." + rz + ".f32");
    }

    /**
     * Reads a region from disk if a file of the right size is there, otherwise erodes it and writes
     * the file. A file of the wrong size (written with different settings) is regenerated. The
     * file is written to a .tmp name and then moved into place, so a crash mid-write never leaves
     * a truncated file that would be loaded next time.
     */
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

    /** The region index containing a block coordinate (floor division, so negatives work). */
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

    /**
     * The overlap blend. Each of the 3x3 regions around the point is considered; a region counts
     * if its padded extent (the region plus the overlap on every side) contains the point, with a
     * weight that rises linearly from 0 at the padded edge to 1 one overlap inside it. The result
     * is the weighted mean, so across the overlap one region's erosion fades into the next.
     */
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
                // distance inside the padded extent, along the nearer edge of each axis
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
        if (wsum == 0) return region(rx, rz).sample(x, z);   // cannot happen with a positive overlap; kept as a safe fallback
        return sum / wsum;
    }

    /** Neighbouring regions start computing when the sampled position is within this many blocks of the edge. */
    public double prefetchMargin = 700;

    /** Two octaves of value noise (wavelength upsampleNoiseWavelength and 2.1 times finer), in -amp..amp. */
    private double fineNoise(double x, double z) {
        double f = 1.0 / upsampleNoiseWavelength;
        double n = Hash.value2(noiseSeed, x * f, z * f) * 0.65 + Hash.value2(noiseSeed + 1, x * f * 2.1, z * f * 2.1) * 0.35;
        return n * upsampleNoiseAmplitude;
    }

    /**
     * Slope in degrees at a block position, from the blended height over a 2-block baseline
     * (central differences in x and z). The gradient is scaled by the mask, because the masked
     * height's relief is scaled by it too, so the slope fades to 0 where vanilla takes over. The
     * fine noise is left out on purpose: it would make the rock/grass decision flicker block by block.
     * Used by the material rules to put rock on steep ground and to keep snow off steep faces.
     */
    public double slopeDegrees(double x, double z) {
        double m = mask(x, z);
        double dx = (baseHeight(x + 1, z) - baseHeight(x - 1, z)) / 2.0 * m;
        double dz = (baseHeight(x, z + 1) - baseHeight(x, z - 1)) / 2.0 * m;
        return Math.toDegrees(Math.atan(Math.sqrt(dx * dx + dz * dz)));
    }

    /** "rx,rz" of every region ready in memory, for the info command. */
    public List<String> cachedRegionNames() {
        List<String> out = new ArrayList<>();
        regions.forEach((k, f) -> { if (f.isDone()) out.add(((int) (k >> 32)) + "," + ((int) k.longValue())); });
        return out;
    }

    /** Stops the erosion workers; called when the server stops. Regions in progress are abandoned. */
    public void shutdown() {
        executor.shutdownNow();
    }
}
