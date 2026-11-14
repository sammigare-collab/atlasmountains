package com.atlasmountains.fabric;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The shape of the mountains since 0.5.0: the old mod's mountain (0.1 to 0.3, the one that needed
 * its own world type) on the spots where this mod puts its peaks.
 *
 * <h2>Why</h2>
 * Sammi, 13 Nov 2026, on in-game screenshots of 0.4.13 from above: "these feel too geometric and
 * far away from my reference images", then "scrap this newer one as the mountains looked better on
 * the old mountains, we just need to find a way to get them to generate singularly like these", in
 * vanilla world generation as a one-for-one replacement of vanilla's mountains. Every 0.4 build
 * made a peak out of flat planes; the old mod made a ridge with a crest and two faces and then ran
 * an erosion simulation over it (thermal collapse, then thousands of water droplets carving
 * gullies), which is where its look came from. Its ridge layouts (rings, then a ridge skeleton)
 * are what he rejected, not its mountains.
 *
 * <h2>What this does</h2>
 * Where peaks stand, their spacing and their heights are still decided by {@link HornsFunction}
 * and {@link PeakStrength}, unchanged. Each peak is now one stand-alone mountain of the old design:
 * <ul>
 *   <li>a short straight crest through the summit, turned any way, with the main summit on it and
 *       lower summits along it if it is long enough, falling away from each summit as the old
 *       crest did. (He asked for the top to be "like rectangular rather than one block" and took
 *       it back ten minutes later: "ignore the rectangular look". A level, square-ended top is
 *       not built.)</li>
 *   <li>the old cross profile off the crest: a rock face at 53 degrees for the upper half, a
 *       concave flank below that runs out to nothing. The ends of the crest are rounded, as the
 *       ends of a ridge were in the old mod;</li>
 *   <li>two or three legs (side ridges with small notched peaks) running out from the main summit;</li>
 *   <li>the old detail noise, then the old erosion: thermal, droplets, valley fill, thermal.</li>
 * </ul>
 * The old design was one size (168 blocks of relief). Here it is scaled as a whole, across and up,
 * to each peak's own height, so a small peak is a small copy with the same angles.
 * <p>
 * Erosion cannot be worked out one column at a time: it is run over a square region of the world
 * at once (1024 blocks, plus 256 of overlap on every side so droplets cross the edges), the first
 * time any column in it is asked for, and the result is kept in memory. Regions hand over to each
 * other across a 128-block band so no seam shows. The result is the height of the mountains above
 * the ground ("relief"); HornsFunction puts it on top of vanilla's land.
 *
 * <h2>Sampler</h2>
 * 0.5.0 is a sampler build: every peak gets one of three crest lengths ({@link #CREST_HALF}), for
 * Sammi to compare in the game. {@code /atlasmountains peaks} lists the nearest of each.
 */
final class ErodedPeaks {
    private ErodedPeaks() {}

    // ---- the grid ----------------------------------------------------------------------------
    /** Blocks along one side of a region, blocks between samples, and samples of overlap on each side. */
    static final int REGION = 1024, SPACING = 4, OVERLAP = 64;
    static final int GRID = REGION / SPACING + 2 * OVERLAP;
    /** The overlap in blocks, and half the width of the band over which one region hands over to the next. */
    static final double PAD = OVERLAP * SPACING, BLEND = 64;
    /** Regions kept in memory (1.2 MB each); the least recently used are dropped beyond this. */
    static final int KEEP = 36;

    // ---- the old design, in its own units (MountainSettings of 0.3.0) -------------------------
    /** The old valley floor (y) and the old summit's relief above it. Every length below is for a peak this tall. */
    static final double FLOOR = 64, RELIEF = 168;
    /** The rock face's slope, the share of the relief that is rock face, the flank's slope at its top and its concavity. */
    static final double FACE_SLOPE = 1.35, ROCK_FRACTION = 0.5, FLANK_TOP_SLOPE = 0.90, FLANK_CONCAVITY = 1.7;
    static final double ROCK_LINE = RELIEF * (1 - ROCK_FRACTION);                       // relief at which the rock face begins
    static final double ROCK_RUN = (RELIEF - ROCK_LINE) / FACE_SLOPE;                   // how far out the rock face reaches
    static final double FLANK_LENGTH = FLANK_CONCAVITY * ROCK_LINE / FLANK_TOP_SLOPE;
    static final double HALF_WIDTH = ROCK_RUN + FLANK_LENGTH;                           // crest to the foot of the flank: 221
    /** Summits along a crest: spacing, how much that varies, and how fast the crest falls between them. */
    static final double PEAK_SPACING = 72, PEAK_SPACING_JITTER = 0.25, CREST_FALL = 0.75;
    /** Legs (the old "spurs"): see {@link #legs}. */
    static final double LEG_ANGLE_MIN = 15, LEG_ANGLE_MAX = 50, LEG_FALL = 0.5, LEG_SIDE = 1.1, LEG_PEAKS = 60,
            LEG_NOTCH = 14, LEG_FADE_FROM = 0.8, LEG_REACH = 1.3, LEG_BEND = 0.3;
    /** Detail noise laid on before erosion so the droplets have something to follow. */
    static final double DETAIL = 9.0, DETAIL_LENGTH = 44, DETAIL_POWER = 2.0;   // 5.5 in the old mod and in 0.5.0/0.5.1; see LEGS
    static final int DETAIL_OCTAVES = 3;
    /** Thermal erosion: the steepest rock and flank can stand (as slopes), the band between them, and how fast it works. */
    static final double THERMAL_ROCK = Math.sqrt(1.25 * 1.25 + 0.75 * 0.75), THERMAL_FLANK = Math.tan(Math.toRadians(36)),
            THERMAL_BAND = 30, THERMAL_RATE = 0.25;
    static final int THERMAL_PASSES = 40;
    /**
     * Droplet erosion (after Hans Beyer), the old mod's numbers, except the count: the old mod
     * dropped 50 000 on 640 x 640 samples of solid ridges. Here they are dropped on the mountains
     * only (not wasted on the flat ground between), three times as thickly: the first test of
     * 0.5.0 at the old rate left smooth cones with gullies too faint to see.
     */
    static final double DROPLETS_PER_SAMPLE = 4.5 * 50_000.0 / (640 * 640), ERODE_BELOW_ROCK = 8, ERODE_BAND = 16,
            INERTIA = 0.1, CAPACITY = 2.5, MIN_SLOPE_CAPACITY = 0.01, DEPOSIT = 0.3, ERODE = 0.3, EVAPORATE = 0.015,
            GRAVITY = 0.5, MAX_SPEED = 3, MAX_ERODE = 0.4, FLOOR_DIG = 5, FAN = 2.0;
    static final int DROPLET_LIFE = 120;
    /** Fine noise added when the 4-block grid is read back, so faces are not glassy. */
    static final double FINE = 1.5, FINE_LENGTH = 7;

    // ---- new in 0.5.0: one mountain per peak ---------------------------------------------------
    /** SAMPLER: half the length of the crest for a full-size peak, for looks 0, 1, 2 (a 120-block peak has 0.7 of this). */
    static final double[] CREST_HALF = {8, 30, 60};
    /**
     * SAMPLER since 0.5.3: how deeply the faces are scooped out between the legs, for looks 0, 1, 2
     * (blocks, for a full-size peak). A real face between two ridges is a hollow bowl; these were
     * rounded cones with scratches on them. The crest length (the 0.5.0 sampler, never chosen
     * between) is now drawn separately for each peak and not labelled.
     */
    static final double[] SCOOP = {14, 22, 30};
    static final String[] LOOK_NAMES = {"S light scoop", "M medium scoop", "L deep scoop"};
    /** A face is scooped to full depth this far from the nearest leg (blocks, full-size peak). */
    static final double SCOOP_WIDTH = 45;
    /**
     * The shared base (0.5.3): peaks in a group stand on one raised swell of ground and meet at
     * saddles, instead of each standing alone on the flat. Every peak lifts the ground round it
     * by SWELL of its height, fading over SWELL_SIGMA cells; where peaks are close the lifts add
     * up, never beyond SWELL_CAP of the nearest peak's height. Summits are not raised.
     */
    static final double SWELL = 0.25, SWELL_SIGMA = 0.5, SWELL_CUT = 2.0, SWELL_CAP = 0.40;
    /** The lesser summits along a crest are this share of the main one, at least and at most. */
    static final double LESSER_MIN = 0.90, LESSER_MAX = 0.99;
    /** How many legs a mountain of each look has at least; half of them have one more. The near-point of look S is a horn: three or four aretes. */
    static final int[] LEGS = {4, 3, 3};
    // 0.5.2: Sammi in the game on 0.5.0 and 0.5.1: "sometimes nice and angularly eroded, sometimes too
    // soft". The soft ones in his screenshots (plain smooth cones) were not all one sampler look,
    // so every mountain gets more structure: one more leg each (was 3, 2, 2), stronger detail
    // noise before erosion (9 for 5.5), half as many droplets again and a deeper bite per step
    // (0.4 for 0.3).
    /**
     * Erosion treats the top three quarters of a mountain as rock (steep ground stands, droplets
     * dig). The old mod drew that line half-way up, where its grass began; these mountains are
     * bare rock nearly to the foot, and with the line half-way the legs slumped into soft
     * shoulders below it.
     */
    static final double ERODED_FRACTION = 0.75;
    /**
     * Extra height built in to make up for what erosion takes off a summit: none. 0.5.0 and 0.5.1
     * had 19 here by mistake: Claude compared the summits with the height the /atlasmountains
     * peaks command teleports to, which is 20 blocks above the summit, and "corrected" a loss
     * that was not there, so every mountain in those two builds stood about 20 blocks taller
     * than its set height. Measured again for 0.5.2 against the real summit heights.
     */
    static final double SUMMIT_LOSS = 0.0;
    /** How far the main summit may sit from the middle of its crest, as a share of the half-length. */
    static final double SUMMIT_OFF = 0.6;

    /**
     * Rivers. A mountain fades out towards a river so the river keeps its valley. The fade is
     * worked out for each mountain from how far its own summit is from the river: it is complete
     * within RIVER_SHARE of that distance (at most RIVER_FADE blocks, at least RIVER_FADE_MIN), so
     * the summit itself is never touched. It is applied before erosion, so the side facing the
     * river is carved like the rest. 0.5.0 faded every mountain over a flat 90 blocks, after
     * erosion: a summit nearer a river than that was squashed to a low mound of bare rock
     * (Sammi's screenshot at -9837 -2238, seed -3550034452994718772).
     */
    static final double RIVER_FADE = 90, RIVER_FADE_MIN = 16, RIVER_SHARE = 0.7;

    /** One peak as HornsFunction places it: summit (in its warped coordinates), height, sampler look, cell. */
    record Peak(double x, double z, double h, int look, long ix, long iz) {}

    private static final class Region {
        float[] relief;   // blocks above the ground after erosion; null where the region has no mountains at all
        float[] carve;    // how far erosion lowered the ground (above zero in a gully)
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

    /** Whether any mountain could reach this column (by the box each one fits in, with 16 blocks to spare). */
    private static boolean anyNear(HornsFunction horns, int x, int z) {
        long seed = AtlasMountainsMod.seed();
        double cell = horns.cell(), wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
        long cx = (long) Math.floor(wx / cell), cz = (long) Math.floor(wz / cell);
        for (long jz = cz - 2; jz <= cz + 2; jz++) {
            for (long jx = cx - 2; jx <= cx + 2; jx++) {
                Peak p = horns.peakOf(jx, jz, seed);
                if (p == null) continue;
                double reach = Math.max(boxOf(p), 2.9 * SWELL_SIGMA * cell) + 16;   // the mountain itself or the swell round it
                if (Math.abs(wx - p.x) <= reach && Math.abs(wz - p.z) <= reach) return true;
            }
        }
        return false;
    }

    /** Half the side of the square a mountain fits in. */
    private static double boxOf(Peak p) {
        return (LEG_REACH * HALF_WIDTH + CREST_HALF[2] * (1 + SUMMIT_OFF)) * (p.h + SUMMIT_LOSS) / RELIEF;
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
    static final double GRASS_SLOPE = 0.85, TREES_UP = 0.12, GRASS_UP = 0.24, PATCH_UP = 0.34;
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
        double tall = SnowLine.peakHeight(horns, x, z);
        if (!(tall > 20)) tall = 120;
        long seed = AtlasMountainsMod.seed();
        double up = relief / tall + 0.06 * HornsFunction.valueNoise(x / 37.0, z / 37.0, seed + 771);
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

    // ---- one mountain -------------------------------------------------------------------------

    /** The old cross profile: the height {@code d} out from a crest of relief {@code c} (both for a full-size peak). */
    private static double cross(double c, double d) {
        if (c <= ROCK_LINE) {
            // a crest below the rock line (the low end of a long crest) is all flank, in proportion
            double len = Math.max(1.0, FLANK_LENGTH * c / ROCK_LINE);
            double t = d / len;
            return t >= 1 || c <= 0 ? 0 : c * Math.pow(1 - t, FLANK_CONCAVITY);
        }
        double rockRun = (c - ROCK_LINE) / FACE_SLOPE;
        if (d <= rockRun) return c - FACE_SLOPE * d;
        // the flank always ends the same distance from the crest, as in the old mod
        double t = (d - rockRun) / Math.max(1.0, HALF_WIDTH - rockRun);
        return t >= 1 ? 0 : ROCK_LINE * Math.pow(1 - t, FLANK_CONCAVITY);
    }

    /**
     * The crest's relief at {@code a} along it (0 at the main summit; the crest runs from -lo to hi).
     * The main summit is the full height; every PEAK_SPACING or so along the crest there is a
     * lesser one; between them the crest falls at CREST_FALL, as in the old mod.
     */
    private static double crest(Peak p, double a, double lo, double hi, long seed) {
        double best = RELIEF - CREST_FALL * Math.abs(a);
        for (int side = -1; side <= 1; side += 2) {
            double at = 0;
            for (int i = 1; i <= 3; i++) {
                at += PEAK_SPACING * (1 + PEAK_SPACING_JITTER * (2 * HornsFunction.hash(p.ix, p.iz, seed + 50 + 10 * i + side) - 1));
                if (at > (side < 0 ? lo : hi)) break;
                double top = RELIEF * (LESSER_MIN + (LESSER_MAX - LESSER_MIN) * HornsFunction.hash(p.ix, p.iz, seed + 55 + 10 * i + side));
                best = Math.max(best, top - CREST_FALL * Math.abs(a - side * at));
            }
        }
        return best;
    }

    /**
     * The old mod's legs: side ridges that leave the main summit at 15 to 50 degrees off straight
     * down the face, leaning one way or the other, bending a little as they go. A leg's crest
     * falls at LEG_FALL, gentler than the face, so it stands out from the face as an arete with
     * small notched peaks, and it fades out a little beyond the foot of the flank. Two or three to
     * a mountain, at least one on each side.
     */
    private static double legs(Peak p, int crest, double a, double v, double d, long seed, double[] nearest) {
        nearest[0] = 1e9;
        double reach = LEG_REACH * HALF_WIDTH, fadeFrom = LEG_FADE_FROM * HALF_WIDTH;
        if (d >= reach) return 0;
        double fade = 1;
        if (d > fadeFrom) { double t = (d - fadeFrom) / (reach - fadeFrom); fade = 1 - t * t * (3 - 2 * t); }
        int count = LEGS[crest] + (HornsFunction.hash(p.ix, p.iz, seed + 70) < 0.5 ? 1 : 0);
        int first = HornsFunction.hash(p.ix, p.iz, seed + 71) < 0.5 ? 1 : -1;
        double best = 0;
        for (int j = 0; j < count; j++) {
            long salt = seed + 80 + 7L * j;
            int side = j % 2 == 0 ? first : -first;
            double deg = LEG_ANGLE_MIN + HornsFunction.hash(p.ix, p.iz, salt) * (LEG_ANGLE_MAX - LEG_ANGLE_MIN);
            // the third leg leans the other way from the first so the two on one side spread apart
            double lean = j >= 2 ? -Math.signum(HornsFunction.hash(p.ix, p.iz, seed + 80 + 7L * (j - 2) + 1) - 0.5) : Math.signum(HornsFunction.hash(p.ix, p.iz, salt + 1) - 0.5);
            double ang = Math.toRadians(lean < 0 ? -deg : deg), sn = Math.sin(ang), cs = Math.cos(ang);
            double vv = v * side;
            double run = a * sn + vv * cs;
            if (run < 0) continue;
            double bend = (2 * HornsFunction.hash(p.ix, p.iz, salt + 2) - 1) * LEG_BEND * run * run / reach;
            double w = Math.abs(a * cs - vv * sn - bend);
            if (w < nearest[0]) nearest[0] = w;
            double period = LEG_PEAKS * (0.8 + 0.4 * HornsFunction.hash(p.ix, p.iz, salt + 3));
            double notch = LEG_NOTCH * 0.5 * (1 - Math.cos(2 * Math.PI * run / period));
            double hh = (RELIEF - LEG_FALL * run - notch - LEG_SIDE * w) * fade;
            if (hh > best) best = hh;
        }
        return best;
    }

    private static final ThreadLocal<double[]> NEAREST = ThreadLocal.withInitial(() -> new double[1]);

    /** One mountain's height above the ground, before erosion, at (dx, dz) from its summit; blocks. */
    static double mountain(Peak p, double dx, double dz, long seed) {
        double k = (p.h + SUMMIT_LOSS) / RELIEF;               // the whole design is scaled to this peak
        double reach = boxOf(p);
        if (Math.abs(dx) > reach || Math.abs(dz) > reach) return 0;
        double turn = Math.PI * HornsFunction.hash(p.ix, p.iz, seed + 40);
        double cs = Math.cos(turn), sn = Math.sin(turn);
        double a = (dx * cs + dz * sn) / k, v = (dz * cs - dx * sn) / k;
        double off = SUMMIT_OFF * (2 * HornsFunction.hash(p.ix, p.iz, seed + 41) - 1);
        int crestLook = (int) Math.floor(HornsFunction.hash(p.ix, p.iz, seed + 15) * CREST_HALF.length);
        double half = CREST_HALF[crestLook], lo = half * (1 + off), hi = half * (1 - off);
        double past = a < -lo ? -lo - a : a > hi ? a - hi : 0;
        double d = past <= 0 ? Math.abs(v) : Math.hypot(v, past);   // a rounded end, as a ridge's end was in the old mod
        double c = crest(p, Math.max(-lo, Math.min(hi, a)), lo, hi, seed);
        double h = cross(c, d);
        double[] nearest = NEAREST.get();
        double leg = legs(p, crestLook, a, v, d, seed, nearest);
        // the scoop: away from the legs the face is lowered, most half-way up, nothing at the summit or the foot
        double share = Math.max(0, Math.min(1, h / RELIEF));
        double t = Math.min(1, nearest[0] / SCOOP_WIDTH);
        h = Math.max(0, h - SCOOP[p.look] * t * t * (3 - 2 * t) * 4 * share * (1 - share));
        return Math.max(h, leg) * k;
    }

    private static double ridged(double x, double z, long seed) {
        double sum = 0, amp = 1, norm = 0, freq = 1.0 / DETAIL_LENGTH;
        for (int o = 0; o < DETAIL_OCTAVES; o++) {
            sum += amp * (1 - Math.abs(HornsFunction.valueNoise(x * freq, z * freq, seed + 606 + 31L * o)));
            norm += amp; amp *= 0.5; freq *= 2;
        }
        return sum / norm;
    }

    // ---- one region ---------------------------------------------------------------------------

    private static Region build(HornsFunction horns, int rx, int rz) {
        long seed = AtlasMountainsMod.seed();
        Region r = new Region();
        r.originX = rx * REGION - OVERLAP * SPACING;
        r.originZ = rz * REGION - OVERLAP * SPACING;
        int n = GRID;
        double cell = horns.cell(), sigma = SWELL_SIGMA * cell;
        // every peak that could reach into the region: the cells under it and two more each way
        long c0x = (long) Math.floor((r.originX - 8.0) / cell) - 2, c0z = (long) Math.floor((r.originZ - 8.0) / cell) - 2;
        long c1x = (long) Math.floor((r.originX + (double) n * SPACING + 8.0) / cell) + 2, c1z = (long) Math.floor((r.originZ + (double) n * SPACING + 8.0) / cell) + 2;
        int cw = (int) (c1x - c0x + 1), ch = (int) (c1z - c0z + 1);
        Peak[] peaks = new Peak[cw * ch];
        boolean any = false;
        for (int j = 0; j < ch; j++) for (int i = 0; i < cw; i++) {
            peaks[j * cw + i] = horns.peakOf(c0x + i, c0z + j, seed);
            any |= peaks[j * cw + i] != null;
        }
        if (!any) return r;                       // no mountains here: all zeros, nothing to erode

        RiverWallFunction walls = RiverWallFunction.current;
        double[] fadeOf = new double[cw * ch];     // each mountain's own fade width, worked out when first needed
        float[] h = new float[n * n], before = new float[n * n], rock = new float[n * n];
        boolean[] floor = new boolean[n * n];
        for (int gz = 0; gz < n; gz++) {
            double z = r.originZ + gz * (double) SPACING;
            for (int gx = 0; gx < n; gx++) {
                double x = r.originX + gx * (double) SPACING;
                double wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
                long cx = (long) Math.floor(wx / cell), cz = (long) Math.floor(wz / cell);
                double best = 0, owner = RELIEF, river = Double.NaN, swell = 0, strongest = 0, swellOwner = RELIEF;
                for (long jz = cz - 2; jz <= cz + 2; jz++) {
                    if (jz < c0z || jz > c1z) continue;
                    for (long jx = cx - 2; jx <= cx + 2; jx++) {
                        if (jx < c0x || jx > c1x) continue;
                        int pi = (int) (jz - c0z) * cw + (int) (jx - c0x);
                        Peak p = peaks[pi];
                        if (p == null) continue;
                        double ddx = wx - p.x, ddz = wz - p.z;
                        double lift = SWELL * p.h * Math.exp(-(ddx * ddx + ddz * ddz) / (2 * sigma * sigma));
                        swell += lift;
                        if (lift > strongest) { strongest = lift; swellOwner = p.h; }
                        double m = mountain(p, ddx, ddz, seed);
                        if (m <= best) continue;
                        if (walls != null) {
                            if (Double.isNaN(river)) river = RiverDistance.at((int) Math.floor(x), (int) Math.floor(z), walls.channel());
                            if (fadeOf[pi] == 0) fadeOf[pi] = Math.max(RIVER_FADE_MIN, Math.min(RIVER_FADE,
                                    RIVER_SHARE * RiverDistance.at((int) Math.floor(p.x()), (int) Math.floor(p.z()), walls.channel())));
                            double t = river / fadeOf[pi];
                            if (t < 1) m *= t <= 0 ? 0 : t * t * (3 - 2 * t);
                        }
                        if (m > best) { best = m; owner = p.h; }
                    }
                }
                swell = Math.min(swell - SWELL_CUT, SWELL_CAP * swellOwner);
                if (swell > best && walls != null) {
                    if (Double.isNaN(river)) river = RiverDistance.at((int) Math.floor(x), (int) Math.floor(z), walls.channel());
                    double t = river / RIVER_FADE;
                    if (t < 1) swell *= t <= 0 ? 0 : t * t * (3 - 2 * t);
                }
                if (swell > best) { best = swell; owner = swellOwner; }
                int i = gz * n + gx;
                floor[i] = best <= 1e-6;
                if (!floor[i]) {
                    // detail noise, growing with height so the lower flank stays smooth
                    double w = Math.pow(Math.min(1, best / owner), DETAIL_POWER);
                    best += DETAIL * (ridged(x, z, seed) - 0.5) * w;
                }
                h[i] = (float) (FLOOR + best);
                rock[i] = (float) (FLOOR + owner * (1 - ERODED_FRACTION));   // this mountain's own rock line, for the erosion
            }
        }
        System.arraycopy(h, 0, before, 0, h.length);
        thermal(h, rock, THERMAL_PASSES);
        droplets(h, rock, floor, new Random(seed * 0x9E3779B97F4A7C15L + rx * 341873128712L + rz * 132897987541L + 808));
        fill(h, floor);
        thermal(h, rock, THERMAL_PASSES);
        fill(h, floor);
        r.relief = new float[n * n];
        r.carve = new float[n * n];
        for (int i = 0; i < h.length; i++) {
            r.relief[i] = Math.max(0f, h[i] - (float) FLOOR);
            r.carve[i] = before[i] - h[i];
        }
        return r;
    }

    /** The flat ground between mountains is put back: erosion leaves it lumpy with sediment (at most a 2-block fan stays). */
    private static void fill(float[] h, boolean[] floor) {
        for (int i = 0; i < h.length; i++) {
            if (!floor[i]) continue;
            if (h[i] < FLOOR) h[i] = (float) FLOOR;
            if (h[i] > FLOOR + FAN) h[i] = (float) (FLOOR + FAN);
        }
    }

    /** Thermal erosion: wherever the ground is steeper than rock (above the rock line) or scree (below it) can stand, some of it slides down. */
    private static void thermal(float[] h, float[] rock, int passes) {
        int n = GRID;
        double straight = SPACING, diag = SPACING * Math.sqrt(2);
        int[] dx = {1, 0, -1, 0, 1, -1, -1, 1}, dz = {0, 1, 0, -1, 1, 1, -1, -1};
        for (int p = 0; p < passes; p++) {
            for (int z = 1; z < n - 1; z++) {
                for (int x = 1; x < n - 1; x++) {
                    int i = z * n + x;
                    double hi = h[i];
                    if (hi <= FLOOR + 1e-3) continue;      // flat ground has nothing to shed
                    double t = (hi - rock[i]) / THERMAL_BAND + 0.5;
                    t = Math.max(0, Math.min(1, t));
                    t = t * t * (3 - 2 * t);
                    double crit = THERMAL_FLANK + (THERMAL_ROCK - THERMAL_FLANK) * t;
                    for (int k = 0; k < 8; k++) {
                        int j = (z + dz[k]) * n + (x + dx[k]);
                        double d = hi - h[j], maxDrop = crit * (k < 4 ? straight : diag);
                        if (d > maxDrop) {
                            double delta = (d - maxDrop) * THERMAL_RATE;
                            hi -= delta;
                            h[j] += delta;
                        }
                    }
                    h[i] = (float) hi;
                }
            }
        }
    }

    /**
     * Droplet erosion, the old mod's (after Hans Beyer's "Implementation of a method for hydraulic
     * erosion", 2015). A droplet lands at a random point, runs downhill, picks rock up where it is
     * fast and steep and drops it where it slows. Thousands of them carve the branching gullies on
     * the rock faces. They only dig on the rock half of a mountain, so the flanks stay smooth.
     */
    private static void droplets(float[] h, float[] rock, boolean[] floor, Random rng) {
        int n = GRID;
        int mountain = 0;
        for (boolean f : floor) if (!f) mountain++;
        int count = (int) Math.round(DROPLETS_PER_SAMPLE * mountain);
        double[] brush = new double[9];
        double sum = 0;
        for (int bz = -1; bz <= 1; bz++) for (int bx = -1; bx <= 1; bx++) {
            double w = Math.max(0, 1.5 - Math.sqrt(bx * bx + bz * bz));
            brush[(bz + 1) * 3 + (bx + 1)] = w; sum += w;
        }
        for (int i = 0; i < 9; i++) brush[i] /= sum;
        for (int d = 0; d < count; d++) {
            double px = 0, pz = 0;
            for (int tries = 0; tries < 12; tries++) {       // land on a mountain
                px = 1 + rng.nextDouble() * (n - 3); pz = 1 + rng.nextDouble() * (n - 3);
                if (!floor[(int) pz * n + (int) px]) break;
            }
            double dirX = 0, dirZ = 0, speed = 1, water = 1, sediment = 0;
            for (int life = 0; life < DROPLET_LIFE; life++) {
                int ix = (int) px, iz = (int) pz;
                double fx = px - ix, fz = pz - iz;
                if (ix < 1 || iz < 1 || ix >= n - 2 || iz >= n - 2) break;
                int i00 = iz * n + ix;
                double h00 = h[i00], h10 = h[i00 + 1], h01 = h[i00 + n], h11 = h[i00 + n + 1];
                double gx = ((h10 - h00) * (1 - fz) + (h11 - h01) * fz) / SPACING;
                double gz = ((h01 - h00) * (1 - fx) + (h11 - h10) * fx) / SPACING;
                double height = h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz;
                dirX = dirX * INERTIA - gx * (1 - INERTIA);
                dirZ = dirZ * INERTIA - gz * (1 - INERTIA);
                double len = Math.sqrt(dirX * dirX + dirZ * dirZ);
                if (len < 1e-9) break;
                dirX /= len; dirZ /= len;
                double nx = px + dirX, nz = pz + dirZ;
                int jx = (int) nx, jz = (int) nz;
                if (jx < 1 || jz < 1 || jx >= n - 2 || jz >= n - 2) break;
                double gfx = nx - jx, gfz = nz - jz;
                int j00 = jz * n + jx;
                double newHeight = h[j00] * (1 - gfx) * (1 - gfz) + h[j00 + 1] * gfx * (1 - gfz) + h[j00 + n] * (1 - gfx) * gfz + h[j00 + n + 1] * gfx * gfz;
                double deltaH = newHeight - height;
                double capacity = Math.max(-deltaH, MIN_SLOPE_CAPACITY) * speed * water * CAPACITY;
                if (sediment > capacity || deltaH > 0) {
                    double amount = deltaH > 0 ? Math.min(deltaH, sediment) : (sediment - capacity) * DEPOSIT;
                    sediment -= amount;
                    h[i00] += (float) (amount * (1 - fx) * (1 - fz));
                    h[i00 + 1] += (float) (amount * fx * (1 - fz));
                    h[i00 + n] += (float) (amount * (1 - fx) * fz);
                    h[i00 + n + 1] += (float) (amount * fx * fz);
                } else {
                    double amount = Math.min(Math.min((capacity - sediment) * ERODE, -deltaH), MAX_ERODE);
                    // only on the rock part of this mountain, easing in over a band below its rock line
                    double t = (height - (rock[i00] - ERODE_BELOW_ROCK - ERODE_BAND)) / ERODE_BAND;
                    t = Math.max(0, Math.min(1, t));
                    amount *= t * t * (3 - 2 * t);
                    if (amount > 0) {
                        for (int bz = -1; bz <= 1; bz++) for (int bx = -1; bx <= 1; bx++) {
                            int ci = (iz + bz) * n + ix + bx;
                            double take = Math.min(amount * brush[(bz + 1) * 3 + (bx + 1)], Math.max(0, h[ci] - FLOOR + FLOOR_DIG));
                            h[ci] -= (float) take;
                            sediment += take;
                        }
                    }
                }
                speed = Math.min(MAX_SPEED, Math.sqrt(Math.max(0, speed * speed - deltaH * GRAVITY)));
                water *= 1 - EVAPORATE;
                px = nx; pz = nz;
            }
        }
    }
}
