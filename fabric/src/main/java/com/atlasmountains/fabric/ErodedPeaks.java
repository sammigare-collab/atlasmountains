package com.atlasmountains.fabric;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The mountains as the game reads them: their height above vanilla's land at any column, and
 * what covers them (trees, open grass, rock, snow).
 * <p>
 * The shape itself is made by {@link Massif} since 0.5.4 (groups of peaks cut by running water).
 * 0.5.0 to 0.5.3 built it here, one hand-drawn mountain per peak (crest, cross profile, legs,
 * scoop, a shared swell) scratched by the old mod's droplet erosion; Sammi on 0.5.3: "I don't
 * think that's good enough". That code is in the repository's history (commit 7afdb25).
 * <p>
 * Erosion cannot be worked out one column at a time: it is run over a square region of the world
 * at once (1024 blocks, plus 256 of overlap on every side), the first time any column in it is
 * asked for, and the result is kept in memory. Regions hand over to each other across a
 * 128-block band so no seam shows. The result is the height of the mountains above the ground
 * ("relief"); HornsFunction puts it on top of vanilla's land.
 */
final class ErodedPeaks {
    private ErodedPeaks() {}

    // ---- the grid ----------------------------------------------------------------------------
    /** Blocks along one side of a region, blocks between samples, and samples of overlap on each side. */
    static final int REGION = 1024, SPACING = 4, OVERLAP = 64;
    static final int GRID = REGION / SPACING + 2 * OVERLAP;
    /** The overlap in blocks, and half the width of the band over which one region hands over to the next. */
    static final double PAD = OVERLAP * SPACING, BLEND = 64;
    /** Regions kept in memory (2.4 MB each); the least recently used are dropped beyond this. */
    static final int KEEP = 36;

    /** Fine noise added when the 4-block grid is read back, so faces are not glassy. */
    static final double FINE = 1.5, FINE_LENGTH = 7;
    /** SAMPLER since 0.5.11: see Massif.LOOK_CARVE. */
    static final String[] LOOK_NAMES = {"peak"};

    /**
     * Rivers. A mountain fades out towards a river so the river keeps its valley. The fade is
     * worked out for each mountain from how far its own summit is from the river: it is complete
     * within RIVER_SHARE of that distance (at most RIVER_FADE blocks, at least RIVER_FADE_MIN), so
     * the summit itself is never touched. It is applied before erosion, so the side facing the
     * river is carved like the rest. 0.5.0 faded every mountain over a flat 90 blocks, after
     * erosion: a summit nearer a river than that was squashed to a low mound of bare rock
     * (Sammi's screenshot at -9837 -2238, seed -3550034452994718772).
     */
    // 0.6.0: RIVER_SHARE 0.7 to 0.45 and RIVER_FADE 90 to 70. A mountain is now shaped whole, from the low ground up, and the
    // long fade took the whole side facing a river off it (Sammi's seed -3465664254209586682 at -2836 997).
    static double RIVER_FADE = 70, RIVER_FADE_MIN = 16, RIVER_SHARE = 0.45;

    /** One peak as HornsFunction places it: summit (in its warped coordinates), height, sampler look, cell. */
    record Peak(double x, double z, double h, int look, long ix, long iz) {}

    private static final class Region {
        float[] relief;   // blocks above the ground after erosion; null where the region has no mountains at all
        float[] carve;    // how far erosion lowered the ground (above zero in a gully)
        float[] buried;   // 1 where a river between two peaks of a group is covered (Massif.BURY_FROM)
        float[] up;       // how far up its mountain a column is: 0 at the base level, 1 at the summit (0.6.0)
        int originX, originZ;
        volatile long used;
    }

    private static final ConcurrentHashMap<Long, CompletableFuture<Region>> REGIONS = new ConcurrentHashMap<>();
    private static final java.util.concurrent.atomic.AtomicLong CLOCK = new java.util.concurrent.atomic.AtomicLong();
    private static volatile int generation;
    /** Forget every region: the world or the settings changed. */
    static void reset() { generation++; REGIONS.clear(); }

    private static long key(int rx, int rz) { return ((long) rx << 32) ^ (rz & 0xffffffffL); }

    /** The settings the regions in memory were made with. The game builds the same HornsFunction many times over, so this compares by value. */
    private static volatile HornsFunction builtFor;

    private static Region region(HornsFunction horns, int rx, int rz) {
        HornsFunction made = builtFor;
        if (made != horns) {
            if (made == null || !made.equals(horns)) reset();
            builtFor = horns;
        }
        long k = key(rx, rz);
        CompletableFuture<Region> f = REGIONS.get(k);
        if (f == null) {
            CompletableFuture<Region> mine = new CompletableFuture<>();
            f = REGIONS.putIfAbsent(k, mine);
            if (f == null) {
                f = mine;
                try {
                    long t0 = System.nanoTime();
                    Region r = build(horns, rx, rz);
                    r.used = CLOCK.incrementAndGet();      // or trim() below would take it for the oldest
                    lastBuildMillis = (System.nanoTime() - t0) / 1_000_000;
                    builds++;
                    if (r.relief != null) {
                        eroded++;
                        erodedMillis += lastBuildMillis;
                        if (lastBuildMillis > slowestBuildMillis) slowestBuildMillis = lastBuildMillis;
                    }
                    mine.complete(r);
                    trim();
                } catch (Throwable t) {
                    REGIONS.remove(k);
                    mine.completeExceptionally(t);
                    throw t;
                }
            }
        }
        Region r = f.join();
        r.used = CLOCK.incrementAndGet();
        return r;
    }

    /** For the debug command: how many regions have been eroded and how long the last took. */
    static volatile long lastBuildMillis, builds, eroded, erodedMillis, slowestBuildMillis;

    /** Drop the regions that have gone longest without being asked about, down to KEEP. (Empty ones cost nothing and do not count.) */
    private static void trim() {
        java.util.List<java.util.Map.Entry<Long, CompletableFuture<Region>>> full = new java.util.ArrayList<>();
        for (var e : REGIONS.entrySet()) {
            CompletableFuture<Region> f = e.getValue();
            if (f.isDone() && !f.isCompletedExceptionally() && f.join().relief != null) full.add(e);
        }
        if (full.size() <= KEEP) return;
        full.sort(java.util.Comparator.comparingLong(e -> e.getValue().join().used));
        for (int i = 0; i < full.size() - KEEP; i++) REGIONS.remove(full.get(i).getKey(), full.get(i).getValue());
        if (REGIONS.size() > 4000) REGIONS.clear();   // a very long journey: start again rather than grow for ever
    }

    // ---- reading it back ----------------------------------------------------------------------

    /**
     * The mountains at one column: out[0] the height above the ground in blocks (0 where there is
     * no mountain), out[1] how deep erosion cut here (above zero in a gully, below where it
     * dropped its load).
     */
    static void sample(HornsFunction horns, int x, int z, double[] out) {
        out[0] = 0; out[1] = 0;
        if (!PeakStrength.ready()) return;
        int rx = Math.floorDiv(x, REGION), rz = Math.floorDiv(z, REGION);
        // The game asks about single far-off columns all the time (finding the spawn, placing
        // strongholds, /locate). Eroding a whole region for each would cost seconds, so a region
        // that is not made yet is only made if some mountain could actually reach this column.
        if (!REGIONS.containsKey(key(rx, rz)) && !anyNear(horns, x, z)) return;
        double lx = x - (double) rx * REGION, lz = z - (double) rz * REGION;
        // Almost everywhere one region answers. Within BLEND of an edge the neighbour's answer is
        // mixed in, by a weight that goes from 0 to 1 across the band, so the two erosions meet.
        int nx = lx < BLEND ? -1 : lx >= REGION - BLEND ? 1 : 0, nz = lz < BLEND ? -1 : lz >= REGION - BLEND ? 1 : 0;
        if (nx == 0 && nz == 0) { read(region(horns, rx, rz), x, z, 1, out); finish(x, z, out); return; }
        double wx = nx == 0 ? 1 : nx < 0 ? (lx + BLEND) / (2 * BLEND) : (REGION - lx + BLEND) / (2 * BLEND);
        double wz = nz == 0 ? 1 : nz < 0 ? (lz + BLEND) / (2 * BLEND) : (REGION - lz + BLEND) / (2 * BLEND);
        read(region(horns, rx, rz), x, z, wx * wz, out);
        if (nx != 0) read(region(horns, rx + nx, rz), x, z, (1 - wx) * wz, out);
        if (nz != 0) read(region(horns, rx, rz + nz), x, z, wx * (1 - wz), out);
        if (nx != 0 && nz != 0) read(region(horns, rx + nx, rz + nz), x, z, (1 - wx) * (1 - wz), out);
        finish(x, z, out);
    }

    /** How far a river at this column is covered by the mountains: 0 not at all (the river keeps its valley), 1 fully. Call after sample(). */
    static double buried(HornsFunction horns, int x, int z) {
        int rx = Math.floorDiv(x, REGION), rz = Math.floorDiv(z, REGION);
        if (!REGIONS.containsKey(key(rx, rz))) return 0;
        Region r = region(horns, rx, rz);
        if (r.buried == null) return 0;
        double gx = (x - r.originX) / (double) SPACING, gz = (z - r.originZ) / (double) SPACING;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        if (ix < 0 || iz < 0 || ix > GRID - 2 || iz > GRID - 2) return 0;
        double tx = gx - ix, tz = gz - iz;       // smoothly between samples: read to the nearest one, the ground stepped every 4 blocks
        float[] b = r.buried;
        int i = iz * GRID + ix;
        return b[i] * (1 - tx) * (1 - tz) + b[i + 1] * tx * (1 - tz) + b[i + GRID] * (1 - tx) * tz + b[i + GRID + 1] * tx * tz;
    }

    /** How far up its mountain a column is: 0 at the mountain's base level, 1 at its summit. Call after sample(). */
    static double up(HornsFunction horns, int x, int z) {
        int rx = Math.floorDiv(x, REGION), rz = Math.floorDiv(z, REGION);
        if (!REGIONS.containsKey(key(rx, rz))) return 0;
        Region r = region(horns, rx, rz);
        if (r.up == null) return 0;
        double gx = (x - r.originX) / (double) SPACING, gz = (z - r.originZ) / (double) SPACING;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        if (ix < 0 || iz < 0 || ix > GRID - 2 || iz > GRID - 2) return 0;
        double tx = gx - ix, tz = gz - iz;
        float[] b = r.up;
        int i = iz * GRID + ix;
        return b[i] * (1 - tx) * (1 - tz) + b[i + 1] * tx * (1 - tz) + b[i + GRID] * (1 - tx) * tz + b[i + GRID + 1] * tx * tz;
    }

    /** Whether any mountain could reach this column (by the reach of its lump, with 16 blocks to spare). */
    private static boolean anyNear(HornsFunction horns, int x, int z) {
        long seed = AtlasMountainsMod.seed();
        double cell = horns.cell(), wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
        long cx = (long) Math.floor(wx / cell), cz = (long) Math.floor(wz / cell);
        for (long jz = cz - 2; jz <= cz + 2; jz++) {
            for (long jx = cx - 2; jx <= cx + 2; jx++) {
                Peak p = horns.peakOf(jx, jz, seed);
                if (p == null) continue;
                double reach = Massif.reach(horns, p) + 16;
                if (Math.abs(wx - p.x) <= reach && Math.abs(wz - p.z) <= reach) return true;
            }
        }
        return false;
    }

    // ---- grass on the gentle lower slopes (0.5.2) ------------------------------------------------
    // Sammi: "the flats should be grass and go up the mountain a bit where it's less steep, but
    // natural". Low on a mountain, ground that is gentle enough keeps vanilla's own surface and
    // biome (grass, trees); the steeper it is and the higher up, the less. At the foot, ground up
    // to GRASS_SLOPE is grass; the slope allowed shrinks to nothing GRASS_UP of the way up the
    // mountain. Two slow noises make the edge wander.
    // 0.5.3, bands as on a real mountain (Sammi: "more realistic"): trees only on the lowest part
    // (vanilla's own biome), then open grass with no trees, then grass in patches among the stone,
    // then rock. 0.5.2 had one band, vanilla's biome up to 38% of the height: a strip of trees ran
    // most of the way up a rib and a wood sat half-way up between two peaks.
    // 0.5.7: GRASS_SLOPE 0.85 to 1.1. The feet of the water-made mountains are steeper than the old flanks
    // (37 degrees and up), so nearly all of the foot counted as too steep and rock met forest at a hard line.
    // 0.6.0: the bands are shares of the WHOLE mountain now (base level to summit) and reach higher: TREES_UP 0.12 to 0.16,
    // GRASS_UP 0.24 to 0.36, PATCH_UP 0.34 to 0.50; GRASS_SLOPE 1.1 to 1.2.
    static double GRASS_SLOPE = 1.2, TREES_UP = 0.16, GRASS_UP = 0.36, PATCH_UP = 0.50;
    private static final ThreadLocal<double[]> ROCKY = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0, 0});

    /** 1 where a mountain's surface is bare rock at this column, 0 where it is grass (or there is no mountain). Remembers the last column per thread. */
    static double rocky(HornsFunction horns, int x, int z) { return Math.min(1, cover(horns, x, z)); }

    /** What covers a mountain at this column: 0 vanilla's own surface and biome (trees), 1 open grass with no trees, 2 rock. Remembers the last column per thread. */
    static double cover(HornsFunction horns, int x, int z) {
        double[] c = ROCKY.get();
        if (c[0] == x && c[1] == z && c[3] == System.identityHashCode(horns) && c[4] == AtlasMountainsMod.seed()) return c[2];
        double v = rockyUncached(horns, x, z);
        c[0] = x; c[1] = z; c[2] = v; c[3] = System.identityHashCode(horns); c[4] = AtlasMountainsMod.seed();
        return v;
    }

    private static final ThreadLocal<double[]> ROCKY_OUT = ThreadLocal.withInitial(() -> new double[2]);

    private static double rockyUncached(HornsFunction horns, int x, int z) {
        double[] o = ROCKY_OUT.get();
        sample(horns, x, z, o);
        double relief = o[0];
        if (relief < 2) return 0;
        long seed = AtlasMountainsMod.seed();
        // 0.6.0: how far up the whole mountain (base level to summit), not up the part the mod adds
        double up = up(horns, x, z) + 0.06 * HornsFunction.valueNoise(x / 37.0, z / 37.0, seed + 771);
        if (up >= PATCH_UP) return 2;
        sample(horns, x + 3, z, o); double e = o[0];
        sample(horns, x - 3, z, o); double w = o[0];
        sample(horns, x, z + 3, o); double s = o[0];
        sample(horns, x, z - 3, o); double n = o[0];
        double slope = Math.hypot(e - w, s - n) / 6.0;
        double limit = GRASS_SLOPE * (1 - up / PATCH_UP) + 0.12 * HornsFunction.valueNoise(x / 19.0, z / 19.0, seed + 772);
        if (slope >= limit) return 2;                 // too steep for anything to hold: rock, even low down
        if (up < TREES_UP) return 0;
        if (up < GRASS_UP) return 1;
        return HornsFunction.valueNoise(x / 6.0, z / 6.0, seed + 773) > 2 * (up - GRASS_UP) / (PATCH_UP - GRASS_UP) - 1 ? 1 : 2;
    }

    private static final int[] RING_X = {20, 14, 0, -14, -20, -14, 0, 14}, RING_Z = {0, 14, 20, 14, 0, -14, -20, -14};

    /**
     * How the snow line shifts at a column (blocks; above zero means snow lies lower here). Real
     * snow gathers in hollows and blows and slides off steep ribs; 0.5.1 and 0.5.2 had it the
     * other way round (white along the ribs, bare in the hollows between: Sammi's screenshot at
     * 163 331 -523), because one level line was compared with each column's own height. Here the
     * line is compared with the ground 20 blocks round about instead: a hollow counts as being as
     * high as its rim. Ground steeper than about 50 degrees loses up to 7 blocks.
     */
    static double snowShift(HornsFunction horns, int x, int z) {
        double[] o = ROCKY_OUT.get();
        sample(horns, x, z, o); double here = o[0];
        double ring = 0;
        for (int i = 0; i < 8; i++) { sample(horns, x + RING_X[i], z + RING_Z[i], o); ring += o[0]; }
        sample(horns, x + 3, z, o); double e = o[0];
        sample(horns, x - 3, z, o); double w = o[0];
        sample(horns, x, z + 3, o); double s = o[0];
        sample(horns, x, z - 3, o); double n = o[0];
        double t = Math.max(0, Math.min(1, (Math.hypot(e - w, s - n) / 6.0 - 1.15) / 0.5));
        // A first try took up to 10 blocks off every rib and summit as well and 12 off steep ground:
        // the caps shrank to a few patches. Ribs now lose 3 at most and steep ground 7.
        return Math.max(-3, Math.min(28, ring / 8 - here)) - 7 * t * t * (3 - 2 * t);
    }

    private static void finish(int x, int z, double[] out) {
        if (out[0] <= 0) { out[0] = 0; return; }
        // fine noise, fading in over the first few blocks of height so the foot stays clean
        long seed = AtlasMountainsMod.seed();
        out[0] = Math.max(0, out[0] + FINE * 0.5 * HornsFunction.valueNoise(x / FINE_LENGTH, z / FINE_LENGTH, seed + 909) * Math.min(1, out[0] / 8));
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        // Catmull-Rom: passes through the samples, so summits are not flattened
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    private static void read(Region r, int x, int z, double weight, double[] out) {
        double gx = (x - r.originX) / (double) SPACING, gz = (z - r.originZ) / (double) SPACING;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        if (r.relief == null || ix < 1 || iz < 1 || ix > GRID - 3 || iz > GRID - 3) return;
        double tx = gx - ix, tz = gz - iz;
        float[] h = r.relief;
        double a = 0, b = 0, c = 0, d = 0;
        for (int j = -1; j <= 2; j++) {
            int row = (iz + j) * GRID + ix;
            double v = cubic(h[row - 1], h[row], h[row + 1], h[row + 2], tx);
            if (j == -1) a = v; else if (j == 0) b = v; else if (j == 1) c = v; else d = v;
        }
        out[0] += weight * cubic(a, b, c, d, tz);
        float[] cv = r.carve;
        int i = iz * GRID + ix;
        out[1] += weight * (cv[i] * (1 - tx) * (1 - tz) + cv[i + 1] * tx * (1 - tz) + cv[i + GRID] * (1 - tx) * tz + cv[i + GRID + 1] * tx * tz);
    }

    // ---- one region ---------------------------------------------------------------------------

    private static Region build(HornsFunction horns, int rx, int rz) {
        Region r = new Region();
        r.originX = rx * REGION - OVERLAP * SPACING;
        r.originZ = rz * REGION - OVERLAP * SPACING;
        float[] relief = new float[GRID * GRID], carve = new float[GRID * GRID], buried = new float[GRID * GRID], up = new float[GRID * GRID];
        if (Massif.build(horns, AtlasMountainsMod.seed(), r.originX, r.originZ, GRID, SPACING, relief, carve, buried, up)) {
            r.relief = relief;
            r.carve = carve;
            r.buried = buried;
            r.up = up;
        }
        return r;                                  // no mountains here: relief stays null, nothing was eroded
    }
}
