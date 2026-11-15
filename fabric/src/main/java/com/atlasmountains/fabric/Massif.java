package com.atlasmountains.fabric;

import java.util.ArrayList;
import java.util.List;

/**
 * The shape of the mountains since 0.5.4: made by water, not drawn by hand.
 *
 * <h2>Why</h2>
 * Sammi on 0.5.3: "I don't think that's good enough". Every mountain up to then was one summit
 * with a crest, legs and scoops placed by hand and scratched by a light erosion: a cone with
 * decoration. On 14 Nov 2026 he was shown four versions in the preview tool (0.5.3; separate
 * peaks eroded hard; joined peaks rising while eroded; the same with ice) and chose the third,
 * to be judged in the game. Separate peaks came out as smooth cones on grass; ice turned each
 * group into a wall.
 *
 * <h2>What this does</h2>
 * Where peaks stand and how tall they are is unchanged (HornsFunction, PeakStrength). Each peak
 * gets a rough lump, wider than the gap to its neighbours, so the peaks of a group share a base
 * and meet at saddles. The lumps then rise slowly (fastest under the summits) while running
 * water cuts them: every step, each cell is lowered towards the cell it drains to, in proportion
 * to the square root of the area draining through it (stream power), and rock steeper than it
 * can stand slides. Big valleys cut deep and branch; where two valleys' walls meet a ridge is
 * left. Last, each mountain is scaled so its summit is its set height.
 * <p>
 * Towards a river each lump fades out as the 0.5.1 mountains did (over 0.7 of its own summit's
 * distance to the river), so rivers are where the water drains to.
 * <p>
 * Worked on an 8-block grid (the same shape as on 4 at a quarter of the time), over one of
 * ErodedPeaks' regions at a time, overlap included. Two neighbouring regions eroded separately
 * differ by 0.4 blocks on average and 5 at worst across the band where they are blended
 * (measured in the preview).
 */
final class Massif {
    // ---- the lumps ----
    /** A lump's radius in cells for a full-height peak (smaller peaks: down to 0.55 of it). */
    static double JOIN_REACH = 1.00;
    /** How concave a lump's sides are (1: a straight cone). */
    static double PROFILE = 1.35;
    /** How lumps combine where they overlap (higher: closer to the taller one alone). */
    static double SMOOTH_MAX = 4;
    /** How far a lump's outline wanders, as a share of its radius. */
    static double SHAPE_WARP = 0.30;
    /** Noise on the lumps (blocks, for ground ROUGH_FULL or more above the foot) and its length. 0.6.1: all three half as big again, with the mountains. */
    static double ROUGH = 0.28 * 180, ROUGH_FULL = 180, ROUGH_LENGTH = 165;
    // ---- rivers between peaks (0.5.6) ----
    // Up to 0.5.5 every mountain faded to nothing at every river. Where a river valley ran between
    // two peaks of a group, both then had to rise their whole height in the little room left and
    // were stretched into walls (Sammi, seed 6021084639467433954, between the summits at
    // -7025 5637 and -7068 5828: "the valley running through is forcing that side to be silly
    // steep"; measured 186 blocks in 90, 64 degrees). Offered three ways out he chose "burry it":
    // a river that runs between two peaks of a group is covered, and the peaks meet over it at a
    // saddle. A column counts as between two peaks when the second-nearest peak's lump reaches it
    // at BURY_FROM of that peak's height or more (fully at BURY_FULL). A river along the outside of
    // a group, where only one lump reaches, keeps its valley as before.
    // (BURY_FROM 0.15 and BURY_FULL 0.32, shares of the second peak's height, up to 0.6.0.)
    // 0.6.1: ONE RULE, IN BLOCKS. A river is covered where the mountains, left to themselves, would stand
    // COVER_FULL blocks or more above their base level over it, and keeps its valley where they would stand
    // under COVER_FROM; between the two the valley closes. It does not matter whether that height comes from
    // two peaks meeting or from the heart of one. Why: rivers in mountain country hold water now and the peaks
    // are half as big again, so most summits have a river within 100 blocks (on Sammi's seed
    // -5156097255193664704 half of them within 67). Up to 0.6.0 a lone peak beside a river was built to its full
    // height and had to rise all of it in the room between the river and its summit ("silly steep", 0.5.5). And
    // the old test by shares was never either on or off along a river inside a group of peaks: it left a slot 30
    // to 50 blocks deep and a few blocks wide all along the covered river (first builds of 0.6.1, at 4585 -1048).
    // So beside an open river a mountain is never more than about COVER_FULL high, and comes up to that gently.
    static double COVER_FROM = 70, COVER_FULL = 120;
    // ---- the water ----
    /** Blocks between samples. */
    static final int COARSE = 8;
    /** How hard water cuts, the exponent of drained area, and how strongly water keeps to the steepest way down. */
    static double K = 0.0625, M = 0.5, FLOW_POWER = 4;
    /** How far the lumps rise in all while they are cut, as a share of their own height, and in how many steps. */
    static double UPLIFT = 1.5;
    static int STEPS = 80;
    // ---- the rock ----
    /** The steepest rock can stand (52 degrees), and the steepest the ground at the foot can (37), meeting 42 blocks up. */
    static double TALUS = 1.30, TALUS_LOW = 0.75, TALUS_BAND = 63;   // the band 42 up to 0.6.0
    static int THERMAL_PASSES = 6;
    // ---- the faces (0.5.5) ----
    // Sammi's in-game screenshots of 0.5.4 (seed 6021084639467433954): the outlines and the groups
    // are made by the water, but below the summits nearly every face is one plain smooth slope
    // (two cones side by side at -6865 5832, a flat-faced horn at -6841 5586). The water of the big
    // shape only cuts where a large area drains, and the rock slides plane everything else to
    // one angle. So the faces get their own pass on the 4-block grid: a little noise, then water
    // run down them several times, each time cutting most where most has gathered, so runnels
    // deepen into gullies with ribs left between. The lowest part of a mountain is left alone.
    /** Noise put on the faces first, so the water has something to gather in (blocks, and its length). */
    static double FACE_NOISE = 4, FACE_NOISE_LENGTH = 36;
    /** How many times water is run down the faces, and how deep one run cuts where a full gully's worth has gathered. */
    static int GULLY_RUNS = 8;
    static double GULLY_CUT = 2.2;
    /** A gully is cut at full strength where this many 4-block samples drain through. */
    static double GULLY_FULL = 30;
    /** Faces are cut from this height above the foot, and fully from this. */
    static double GULLY_FROM = 10, GULLY_ABOVE = 40;   // 22 and 60 up to 0.5.13, when a mountain was only the part the mod adds
    /** The steepest a gully's wall may stand (62 degrees). */
    static double GULLY_WALL = 1.9;
    /** How strongly the faces' water keeps to the steepest way down. Lower than FLOW_POWER: at 4 some gullies ran dead straight along the grid. */
    static double GULLY_FLOW = 1.5;   // 1.6 up to 0.6.0: 1.5 is a multiply and a square root, and this is worked out ten million times a region
    /** How the cut grows with the water gathered (0.5: square root; 1: in proportion, so only real channels are cut and they are narrow). */
    static double GULLY_POWER = 0.5;
    /** How far the roughening noise is scaled up with a look's deeper carve (1: with it, as 0.5.11; 0: not at all). */
    static double NOISE_WITH_DEPTH = 1;

    private static final int[] DX = {1, 0, -1, 0, 1, -1, -1, 1}, DZ = {0, 1, 0, -1, 1, 1, -1, -1};
    private static final double SQRT2 = Math.sqrt(2), INV_SQRT2 = 1 / Math.sqrt(2);

    /**
     * A heap of (height, cell) pairs, lowest height first, each packed into one long. 0.6.1: up to 0.6.0 a
     * java.util.PriorityQueue of two-element arrays did this job, one new array per cell per pass (three million
     * a region); with the mountains half as big again a region took over two seconds to make.
     */
    private static final class Heap {
        private long[] a;
        private int size;
        Heap(int room) { a = new long[Math.max(16, room)]; }
        boolean isEmpty() { return size == 0; }
        void clear() { size = 0; }
        static long pack(float v, int cell) {
            int b = Float.floatToIntBits(v);
            b ^= (b >> 31) & 0x7fffffff;                  // so that the bits sort as the numbers do, below zero too
            return ((long) b << 32) | (cell & 0xFFFFFFFFL);
        }
        void add(float v, int cell) {
            if (size == a.length) a = java.util.Arrays.copyOf(a, size * 2);
            long k = pack(v, cell);
            int i = size++;
            while (i > 0) {
                int up = (i - 1) >>> 1;
                if (a[up] <= k) break;
                a[i] = a[up]; i = up;
            }
            a[i] = k;
        }
        /** Takes the lowest out and returns its cell. */
        int poll() {
            long top = a[0], last = a[--size];
            int i = 0, half = size >>> 1;
            while (i < half) {
                int c = 2 * i + 1;
                if (c + 1 < size && a[c + 1] < a[c]) c++;
                if (last <= a[c]) break;
                a[i] = a[c]; i = c;
            }
            a[i] = last;
            return (int) top;
        }
        /** The height of the lowest, without taking it out. */
        float lowest() {
            int b = (int) (a[0] >> 32);
            b ^= (b >> 31) & 0x7fffffff;
            return Float.intBitsToFloat(b);
        }
    }

    private final int n;
    private final double sp = COARSE, ox, oz, cell;
    private final float[] h, base, bury;
    private final boolean[] outlet;
    private final List<ErodedPeaks.Peak> peaks = new ArrayList<>();
    /** Per peak: its base level (y), the y of its summit, and how high its summit stands above the blended base level there. */
    private double[] baseY, topY, target;

    // ---- 0.6.0: the whole mountain, from the low ground to the summit ----
    // Up to 0.5.13 a mountain was its own height (60 to 172 blocks) of carved rock set on top of
    // vanilla's land, and vanilla's land under mountain country is itself a smooth hill 50 to 80
    // blocks high. Measured on Sammi's seed -3465664254209586682 at -2840 998: the peak of his
    // screenshots stood 145 blocks above the plain, of which 63 were the mod's and 80 vanilla's
    // smooth hill. Every pass that carves (water, gullies, ridges) only ever had that cap to work
    // on, which is why the game showed smooth blobs with a rough tip while the preview tool, on
    // flat land, showed ridged massifs.
    // Now a mountain is shaped as one thing from the low ground round it (its "base") to its
    // summit: its lump is that tall (its "stature") and as wide as a mountain that tall, and is
    // eroded and carved as before. What is put on top of vanilla's land at a column is then
    // whatever of that shape stands above the land there: where vanilla's hill is higher than the
    // shape (a valley of the shape running over the hill), the hill shows and nothing is added.
    // Vanilla's land is still never lowered, and summits are where and as high as they were.
    /** A peak's base is the level this share of the land round it lies below (0.25: a quarter of it), ... */
    static double BASE_SHARE = 0.25;
    /** false: as up to 0.5.13 (a mountain is only its own height, set on the land), for comparing in the preview. */
    static boolean WHOLE = true;
    static boolean DEBUG = Boolean.getBoolean("atlasmountains.debug") || new java.io.File("/tmp/atlas_debug").exists();
    /** ... but never more than this far below the land at its summit (110 up to 0.6.0), and never below the sea. */
    static double BASE_DROP_MAX = 165, SEA = 63;
    /** The base level between peaks is blended over this many cells. */
    static double BASE_BLEND = 0.6;
    private static final java.util.concurrent.ConcurrentHashMap<Long, double[]> FOOT = new java.util.concurrent.ConcurrentHashMap<>();
    private static final double[] RING = {0.55, 0.8, 1.05};

    /** For a peak: {the land at its summit (y), its base level (y)}. Worked out once per peak and kept. */
    static double[] foot(HornsFunction horns, ErodedPeaks.Peak p) {
        long key = HornsFunction.cellKey(p.ix(), p.iz(), AtlasMountainsMod.seed(), Double.doubleToLongBits(horns.cell()));   // 0.6.1: see cellKey for the fault this had
        double[] f = FOOT.get(key);
        if (f != null) return f;
        boolean ready = PeakStrength.ready();
        double top = 128 + 128 * PeakStrength.landAt(p.ix(), p.iz(), (int) Math.floor(p.x()), (int) Math.floor(p.z()));
        double r = JOIN_REACH * horns.cell();
        double[] ring = new double[RING.length * 12];
        int k = 0;
        for (int j = 0; j < RING.length; j++) for (int a = 0; a < 12; a++) {
            double ang = (a + 0.33 * j) * Math.PI / 6;
            ring[k++] = 128 + 128 * PeakStrength.land((int) Math.floor(p.x() + RING[j] * r * Math.cos(ang)), (int) Math.floor(p.z() + RING[j] * r * Math.sin(ang)));
        }
        java.util.Arrays.sort(ring);
        double base = Math.min(top, Math.max(Math.max(SEA, top - BASE_DROP_MAX), ring[(int) (BASE_SHARE * (ring.length - 1))]));
        if (!WHOLE) base = top;
        f = new double[]{top, base};
        if (!ready) return f;       // see HornsFunction.heightOf: nothing is remembered from before the world's maps can be read
        if (FOOT.size() > 50000) FOOT.clear();
        FOOT.put(key, f);
        return f;
    }

    /** Forget every peak's foot: the world changed. */
    static void forget() { FOOT.clear(); }

    /** A peak's stature: from its base level to its summit, in blocks. */
    static double stature(HornsFunction horns, ErodedPeaks.Peak p) {
        double[] f = foot(horns, p);
        return p.h() + f[0] - f[1];
    }

    private static double radius(HornsFunction horns, ErodedPeaks.Peak p) {
        return JOIN_REACH * horns.cell() * (0.55 + 0.45 * Math.min(1, stature(horns, p) / horns.height()));
    }

    /**
     * How far a river is covered at a place this far from a peak's summit, going by that peak alone (as lumps()
     * decides it, without the wander of the outline or its neighbours' help): see COVER_FROM.
     * HornsFunction.heightOf asks.
     */
    static double coveredAt(HornsFunction horns, ErodedPeaks.Peak p, double distance) {
        double t = distance / radius(horns, p);
        if (t >= 1) return 0;
        return ramp((stature(horns, p) * Math.pow(1 - t, PROFILE) - COVER_FROM) / (COVER_FULL - COVER_FROM));
    }

    /** How far from its summit a peak's lump can reach, in blocks. */
    static double reach(HornsFunction horns, ErodedPeaks.Peak p) {
        return radius(horns, p) * (1 + SHAPE_WARP);
    }

    /**
     * Fills relief (blocks above the ground), carve (how deep the couloirs cut), buried (1 where a
     * river between two peaks is covered, see BURY_FROM) and up (how far up its mountain a column
     * is, 0 at the base level to 1 at the summit) for a square of size x size samples, spacing blocks apart, from (originX, originZ).
     * Returns false, leaving both untouched, if no peak reaches the square.
     */
    static boolean build(HornsFunction horns, long seed, int originX, int originZ, int size, int spacing, float[] relief, float[] carve, float[] buried, float[] up, float[] datum) {
        long t0 = System.nanoTime();
        Massif m = new Massif(horns, seed, originX, originZ, size * spacing);
        if (m.peaks.isEmpty()) return false;
        m.lumps(horns, seed);
        long t1 = System.nanoTime();
        m.erode();
        long t2 = System.nanoTime();
        double step = spacing / m.sp;
        for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) {
            int i = gz * size + gx;
            double v = Math.max(0, cubicAt(m.h, m.n, gx * step, gz * step));
            double b = Math.max(v, cubicAt(m.base, m.n, gx * step, gz * step));
            relief[i] = (float) v;
            carve[i] = 0;      // 0.6.0: only what the couloirs cut (gullies, ridges) counts, for the snow; up to 0.5.13 it began as lump minus eroded, above zero nearly everywhere
            buried[i] = (float) Math.max(0, Math.min(1, cubicAt(m.bury, m.n, gx * step, gz * step)));
        }
        // SAMPLER (0.5.11): each peak has one of three looks; what differs is blended smoothly between neighbouring peaks
        float[] ref = m.spread(size, spacing, -1, null), deep = m.spread(size, spacing, 0, LOOK_CARVE), sharp = m.spread(size, spacing, 0, LOOK_SHARP);
        for (int i = 0; i < relief.length; i++) {
            if (sharp[i] <= 1.001f || relief[i] <= 0 || relief[i] >= ref[i]) continue;
            // look C: the profile is made hollow (height as a share of the summit's, raised to a power): the
            // summit stays, the upper faces steepen, the top narrows, the lower slopes ease
            float was = relief[i];
            relief[i] = (float) (ref[i] * Math.pow(was / ref[i], sharp[i]));
            carve[i] += was - relief[i];
        }
        // Where vanilla's land still stands above the shape (the broad hollows between the ribs: the drape
        // only lifts the shape on average), the surface is the land's own smooth one. From BOWL_FROM of
        // the way up a mountain it is given a skin BOWL blocks thick before the faces are carved, so the
        // carving bites into it and what covers it is the mountain's own rock, grass and snow, not
        // vanilla's surface with its trees. Lower down the land shows as it is: wooded valley floors.
        float[] over = new float[relief.length], skin = new float[relief.length];
        if (WHOLE) for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) {
            int i = gz * size + gx;
            if (relief[i] <= 0) continue;
            over[i] = (float) m.overAt(originX + gx * spacing, originZ + gz * spacing);
            double t = Math.max(0, Math.min(1, (Math.max(relief[i], over[i]) / Math.max(20, ref[i]) - BOWL_FROM) / (BOWL_FULL - BOWL_FROM)));
            // only where the mountain itself has some height (a neighbouring hill of vanilla's under the mountain's outer foot is left alone)
            double own = m.own == null ? 0 : Math.max(0, Math.min(1, cubicAt(m.own, m.n, gx * step, gz * step)));
            skin[i] = (float) (t * t * (3 - 2 * t) * own);
            relief[i] = (float) Math.max(relief[i], over[i] + BOWL * skin[i]);
        }
        // where each summit really is on this grid (the highest sample within 30 blocks of where it is set), for the ridges to spare
        java.util.List<int[]> tops = new ArrayList<>();
        for (ErodedPeaks.Peak p : m.peaks) {
            int px = (int) Math.round((p.x() - originX) / spacing), pz = (int) Math.round((p.z() - originZ) / spacing), r = 30 / spacing, best = -1;
            for (int dz = -r; dz <= r; dz++) for (int dx = -r; dx <= r; dx++) {
                int xx = px + dx, zz = pz + dz;
                if (xx < 0 || zz < 0 || xx >= size || zz >= size) continue;
                if (best < 0 || relief[zz * size + xx] > relief[best]) best = zz * size + xx;
            }
            if (best >= 0 && relief[best] > 5) tops.add(new int[]{best % size, best / size});
        }
        float[] dbg = DEBUG ? relief.clone() : null;
        long t3 = System.nanoTime();
        if (GULLY_RUNS > 0) gullies(relief, carve, deep, tops, size, spacing, originX, originZ, seed);
        if (DEBUG) System.err.printf("MASSIF times: %d peaks; lumps %d ms, erosion %d, to the fine grid %d, faces %d%n", m.peaks.size(), (t1 - t0) / 1000000, (t2 - t1) / 1000000, (t3 - t2) / 1000000, (System.nanoTime() - t3) / 1000000);
        if (DEBUG) for (int[] t : tops) {
            int i = t[1] * size + t[0];
            System.err.printf("MASSIF top at %d %d: before carving %.0f, after %.0f, land over base %.0f, so above land %.0f; ref %.0f%n", originX + t[0] * spacing, originZ + t[1] * spacing, dbg[i], relief[i], over[i], relief[i] - over[i], ref[i]);
        }
        // 0.6.0: so far "relief" is the shape above the base level. What goes on top of vanilla's
        // land is what of it stands above the land.
        for (int i = 0; i < relief.length; i++) {
            up[i] = 0;
            if (relief[i] <= 0) continue;
            up[i] = (float) Math.min(1, Math.max(relief[i], over[i]) / Math.max(20, ref[i]));
            // whatever the carving took, SKIN blocks of the skin are left, so the hollow stays the mountain's own surface
            relief[i] = (float) Math.max(SKIN * skin[i], relief[i] - over[i]);
        }
        // 0.6.1: the base level, for covered rivers. Rivers in mountain country are cut down to water level now, far
        // below the mountains' base level, and the shape is measured from the base level: laid on a river's bed it
        // would sag into the valley it is meant to cover. HornsFunction fills the valley to this level, column by
        // column from the land's real height (a first try filled it here, from the land as this class knows it,
        // every 16 blocks: that left a groove along the river).
        for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) datum[gz * size + gx] = (float) m.datumAt(originX + gx * spacing, originZ + gz * spacing);
        return true;
    }
    /** See drape(). */
    static double COVER = 16, DRAPE_BLUR = 40, DRAPE_FROM = 3, DRAPE_FULL = 18, DRAPE_CLEAR = 70, OWN_FROM = 0.28, OWN_FULL = 0.6, RIVER_DRAPE = 40;
    /** See build(): the skin put over the land in the hollows, from this share of the way up a mountain, in full from this. */
    static double BOWL = 9, SKIN = 3, BOWL_FROM = 0.22, BOWL_FULL = 0.34;

    /** A smooth blur (a box of the given radius, three times over). */
    private static float[] blur(float[] src, int n, int radius) {
        float[] a = src.clone(), b = new float[src.length];
        for (int pass = 0; pass < 3; pass++) {
            for (int z = 0; z < n; z++) {                       // along x
                double sum = 0; int row = z * n;
                for (int x = -radius; x <= radius; x++) sum += a[row + Math.max(0, Math.min(n - 1, x))];
                for (int x = 0; x < n; x++) {
                    b[row + x] = (float) (sum / (2 * radius + 1));
                    sum += a[row + Math.min(n - 1, x + radius + 1)] - a[row + Math.max(0, x - radius)];
                }
            }
            for (int x = 0; x < n; x++) {                       // along z
                double sum = 0;
                for (int z = -radius; z <= radius; z++) sum += b[Math.max(0, Math.min(n - 1, z)) * n + x];
                for (int z = 0; z < n; z++) {
                    a[z * n + x] = (float) (sum / (2 * radius + 1));
                    sum += b[Math.min(n - 1, z + radius + 1) * n + x] - b[Math.max(0, z - radius) * n + x];
                }
            }
        }
        return a;
    }

    /** Blocks between the places the land is looked up. */
    static final int LAND_STEP = 16;

    // ---- SAMPLER (0.5.11): three looks, for Sammi to compare in the game ("im not sure if the mountain shape is right yet") ----
    // A as it was. B the faces carved about twice as deep (the "deep" carve he passed over in renders on 14 Nov, where
    // there was no sun or shadow to judge it by). C the same deep carve on a sharper mountain: hollow profile, steeper
    // upper faces, narrower top (aimed at the cones and humps).
    /** How much deeper than GULLY_CUT and FACE_NOISE each look's faces are carved, and the power of its profile (1: unchanged). */
    static final double[] LOOK_CARVE = {1.0}, LOOK_SHARP = {1.0};   // the 0.5.11 sampler (B: carve 2; C: carve 2, sharp 1.4) is over: he could not tell them apart
    /** For testing: a file /tmp/atlas_look holding 0, 1 or 2 gives every peak that look. */
    static final int FORCE_LOOK = forced();
    private static int forced() {
        try { return Integer.parseInt(java.nio.file.Files.readString(java.nio.file.Path.of("/tmp/atlas_look")).trim()); } catch (Exception e) { return -1; }
    }

    /** One number per peak (its set height if perLook is null, else perLook[its look]) spread smoothly over a fine grid. */
    private float[] spread(int size, int spacing, int unused, double[] perLook) {
        float[] out = new float[size * size];
        if (perLook != null && perLook.length == 1) { java.util.Arrays.fill(out, (float) perLook[0]); return out; }   // one look: the same everywhere
        double sig = 0.35 * cell;
        int np = peaks.size();
        double[] px = new double[np], pz = new double[np], pv = new double[np];
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            px[a] = p.x(); pz[a] = p.z();
            pv[a] = perLook == null ? target[a] : perLook[FORCE_LOOK >= 0 ? FORCE_LOOK : p.look()];
        }
        // worked out every other sample each way and read between: it is a smooth blend over a third of a cell
        int step = 2, m = (size - 1) / step + 2;
        float[] low = new float[m * m];
        for (int gz = 0; gz < m; gz++) for (int gx = 0; gx < m; gx++) {
            double x = ox + gx * step * spacing, z = oz + gz * step * spacing, sw = 1e-6, sv = 0;
            for (int a = 0; a < np; a++) {
                double dx = x - px[a], dz = z - pz[a];
                if (Math.abs(dx) > 4 * sig || Math.abs(dz) > 4 * sig) continue;
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; sv += w * pv[a];
            }
            low[gz * m + gx] = sw < 1e-5 ? (perLook == null ? 120f : 1f) : (float) (sv / sw);
        }
        for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) {
            int ix = gx / step, iz = gz / step, i = iz * m + ix;
            double tx = (gx - ix * step) / (double) step, tz = (gz - iz * step) / (double) step;
            out[gz * size + gx] = (float) (low[i] * (1 - tx) * (1 - tz) + low[i + 1] * tx * (1 - tz) + low[i + m] * (1 - tx) * tz + low[i + m + 1] * tx * tz);
        }
        return out;
    }

    /** The faces' own pass: see FACE_NOISE. Lowers relief, and adds what it cut to carve (the snow lies lower in gullies). */
    private static void gullies(float[] h, float[] carve, float[] deep, java.util.List<int[]> tops, int n, double sp, int originX, int originZ, long seed) {
        int N = n * n;
        float[] before = h.clone(), weight = new float[N];
        boolean[] out = new boolean[N];
        for (int z = 0; z < n; z++) for (int x = 0; x < n; x++) {
            int i = z * n + x;
            out[i] = h[i] <= 0.5f || x == 0 || z == 0 || x == n - 1 || z == n - 1;
            double t = Math.max(0, Math.min(1, (h[i] - GULLY_FROM) / (GULLY_ABOVE - GULLY_FROM)));
            weight[i] = (float) (t * t * (3 - 2 * t));
        }
        int[] order = new int[N];
        boolean[] seen = new boolean[N];
        float[] fill = new float[N];
        double[] area = new double[N], wts = new double[8];
        Heap q = new Heap(N / 4);
        for (int run = 0; run < GULLY_RUNS; run++) {
            java.util.Arrays.fill(seen, false);
            java.util.Arrays.fill(area, 0);
            // Only the mountains are worked through: the flat ground between them (every "out" cell that touches no
            // mountain) neither holds water nor passes any on.
            q.clear();
            for (int i = 0; i < N; i++) if (out[i]) {
                seen[i] = true; fill[i] = h[i];
                int x = i % n, z = i / n;
                boolean edge = false;
                for (int d = 0; d < 8 && !edge; d++) {
                    int xx = x + DX[d], zz = z + DZ[d];
                    edge = xx >= 0 && zz >= 0 && xx < n && zz < n && !out[zz * n + xx];
                }
                if (edge) q.add(h[i], i);
            }
            int k = 0;
            while (!q.isEmpty()) {
                int i = q.poll();
                order[k++] = i;
                int x = i % n, z = i / n;
                for (int d = 0; d < 8; d++) {
                    int xx = x + DX[d], zz = z + DZ[d];
                    if (xx < 0 || zz < 0 || xx >= n || zz >= n) continue;
                    int j = zz * n + xx;
                    if (seen[j]) continue;
                    seen[j] = true;
                    fill[j] = Math.max(h[j], fill[i] + 1e-3f);
                    q.add(fill[j], j);
                }
            }
            boolean fastFlow = GULLY_FLOW == 1.5, rootCut = GULLY_POWER == 0.5;
            for (int a = k - 1; a >= 0; a--) {
                int i = order[a];
                area[i] += 1;
                if (out[i]) continue;
                int x = i % n, z = i / n;
                double total = 0;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = fill[i] - fill[j];
                    if (drop <= 0) { wts[d] = 0; continue; }
                    if (d >= 4) drop *= INV_SQRT2;
                    wts[d] = (fastFlow ? drop * Math.sqrt(drop) : Math.pow(drop, GULLY_FLOW)) * (d < 4 ? 1 : INV_SQRT2);
                    total += wts[d];
                }
                if (total <= 0) continue;
                for (int d = 0; d < 8; d++) if (wts[d] > 0) area[(z + DZ[d]) * n + x + DX[d]] += area[i] * wts[d] / total;
            }
            for (int i = 0; i < N; i++) {
                if (out[i] || weight[i] <= 0) continue;
                // nothing where only the cell itself drains (crests and summits keep their height)
                double gathered = Math.max(0, area[i] - 1.5) / GULLY_FULL;
                double cut = GULLY_CUT * deep[i] * weight[i] * Math.min(1, rootCut ? Math.sqrt(gathered) : Math.pow(gathered, GULLY_POWER));
                if (run == 0) {
                    // the noise goes on with the first run, and not on crests and summits (where nothing drains), so no summit moves
                    double wx = originX + (i % n) * sp, wz = originZ + (i / n) * sp;
                    double nz = HornsFunction.valueNoise(wx / FACE_NOISE_LENGTH, wz / FACE_NOISE_LENGTH, seed + 620)
                            + 0.5 * HornsFunction.valueNoise(wx / (FACE_NOISE_LENGTH / 2.2), wz / (FACE_NOISE_LENGTH / 2.2), seed + 621);
                    cut -= FACE_NOISE * (1 + (deep[i] - 1) * NOISE_WITH_DEPTH) * weight[i] * nz / 1.5 * Math.min(1, (area[i] - 1) / 4);
                }
                h[i] = (float) Math.max(0.6, h[i] - cut);
            }
            // walls steeper than rock can stand fall in
            for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
                int i = z * n + x;
                double hi = h[i];
                if (out[i]) continue;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = hi - h[j], max = GULLY_WALL * sp * (d < 4 ? 1 : SQRT2);
                    if (drop > max && !out[j]) { double m = (drop - max) * 0.25; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
        if (RIDGE_DEPTH > 0) ridges(h, out, weight, area, tops, n, sp);
        for (int i = 0; i < N; i++) carve[i] += Math.max(0f, before[i] - h[i]);
    }

    // ---- 0.5.12: knife-edge ridges ----
    // Sammi's two reference pictures (a summit where thin sharp ridges meet, each running a long way
    // down; a row of horns with fluted faces) against the mod: "rounded shoulders, flat even faces, a
    // few wide soft grooves: everything rounded off, like it has been sanded" (his word: "fluffy").
    // He agreed, and that the fine detail of the pictures is out of reach: "I just want the feel".
    // The three looks of the 0.5.11 sampler only changed how deep the grooves were; he could not
    // tell them apart.
    // So, last of all: the lines where water gathers on a face (RIDGE_AREA samples or more draining
    // through) are cut down into couloirs, up to RIDGE_DEPTH blocks where RIDGE_FULL samples drain,
    // and the rock then rises from every couloir at one fixed angle (RIDGE_SLOPE) until it meets the
    // rock rising from the next. Where they meet is an edge, and nothing rounds it afterwards.
    // Ground the rising rock does not reach (crests, the summit) is left exactly as it was.
    // ---- 0.5.13: a crest for a top ----
    // Sammi, of the lone peaks of 0.5.12 (seed -3465664254209586682): "can the top not be a point and be a
    // small line, like a mini ridge?" Its length in blocks for a mountain 120 or more tall (shorter ones in
    // proportion), and how far it falls from the summit to its longer end, as a share of the height. Not
    // level: a "rectangular" level top was asked for and withdrawn in 0.4.
    static double CREST_MIN = 68, CREST_MAX = 120, CREST_FULL = 225, CREST_FALL = 0.04;   // first tried 22 to 50 falling 0.10: still read as a point. 45 to 80 for a mountain 150 tall up to 0.6.0
    static double RIDGE_DEPTH = 30, RIDGE_AREA = 8, RIDGE_FULL = 55, RIDGE_SLOPE = 1.9;

    private static void ridges(float[] h, boolean[] out, float[] weight, double[] area0, java.util.List<int[]> tops, int n, double sp) {
        int N = n * n;
        float[] e = h.clone();
        Heap q = new Heap(N / 8);
        // the water gathered, taken as the most of a sample and its neighbours: sample by sample it alternates, and the cut would too
        double[] area = new double[N];
        for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
            double mx = 0;
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) mx = Math.max(mx, area0[(z + dz) * n + x + dx]);
            area[z * n + x] = 0.5 * (mx + area0[z * n + x]);
        }
        for (int i = 0; i < N; i++) {
            if (out[i] || weight[i] <= 0 || area[i] < RIDGE_AREA) continue;
            double cut = RIDGE_DEPTH * weight[i] * Math.min(1, Math.sqrt((area[i] - RIDGE_AREA) / (RIDGE_FULL - RIDGE_AREA)));
            // never so deep that the rock rising from here would stand below a summit: summits keep their height exactly
            int x = i % n, z = i / n;
            for (int[] t : tops) {
                double dx = (t[0] - x) * sp, dz = (t[1] - z) * sp;
                if (Math.abs(dx) > 200 || Math.abs(dz) > 200) continue;
                cut = Math.min(cut, RIDGE_SLOPE * Math.sqrt(dx * dx + dz * dz) - (h[t[1] * n + t[0]] - h[i]) - 1);
            }
            if (cut < 0.5) continue;
            e[i] = (float) Math.max(0.6, h[i] - cut);
            q.add(e[i], i);
        }
        // the lowest surface that rises from the couloirs at RIDGE_SLOPE and nowhere stands above the ground as it was
        while (!q.isEmpty()) {
            float was = q.lowest();
            int i = q.poll();
            if (was > e[i]) continue;
            int x = i % n, z = i / n;
            for (int d = 0; d < 8; d++) {
                int xx = x + DX[d], zz = z + DZ[d];
                if (xx < 1 || zz < 1 || xx >= n - 1 || zz >= n - 1) continue;
                int j = zz * n + xx;
                if (out[j]) continue;
                float v = (float) (e[i] + RIDGE_SLOPE * sp * (d < 4 ? 1 : SQRT2));
                if (v < e[j] - 0.01f) { e[j] = v; q.add(v, j); }
            }
        }
        System.arraycopy(e, 0, h, 0, N);
    }

    private Massif(HornsFunction horns, long seed, int originX, int originZ, int span) {
        ox = originX; oz = originZ;
        cell = horns.cell();
        n = (int) Math.ceil(span / sp) + 1;
        h = new float[n * n]; base = new float[n * n]; bury = new float[n * n]; outlet = new boolean[n * n];
        long c0x = (long) Math.floor((ox - 8) / cell) - 2, c0z = (long) Math.floor((oz - 8) / cell) - 2;
        long c1x = (long) Math.floor((ox + n * sp + 8) / cell) + 2, c1z = (long) Math.floor((oz + n * sp + 8) / cell) + 2;
        for (long iz = c0z; iz <= c1z; iz++) for (long ix = c0x; ix <= c1x; ix++) {
            ErodedPeaks.Peak p = horns.peakOf(ix, iz, seed);
            if (p != null) peaks.add(p);
        }
    }

    private void lumps(HornsFunction horns, long seed) {
        int np = peaks.size();
        double[] radius = new double[np], fadeOf = new double[np];
        free = new float[n * n]; riverAt = new float[n * n];
        baseY = new double[np]; topY = new double[np]; target = new double[np];
        for (int a = 0; a < np; a++) {
            double[] f = foot(horns, peaks.get(a));
            baseY[a] = f[1]; topY[a] = f[0] + peaks.get(a).h();
            radius[a] = radius(horns, peaks.get(a));
        }
        // a summit stands its own height above the land under it, whatever the blended base is there
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            target[a] = p.h() + Math.max(0, topY[a] - p.h() - datumAt(p.x(), p.z()));
        }
        reachOf = radius;
        datumGrid = null;
        float[] dg = new float[n * n];
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) dg[gz * n + gx] = (float) datumExact(ox + gx * sp, oz + gz * sp);
        datumGrid = dg;
        if (WHOLE) lookUpLand();
        RiverWallFunction walls = RiverWallFunction.current;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double x = ox + gx * sp, z = oz + gz * sp;
            double wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
            double sum = 0, sumFree = 0, river = Double.NaN, datum = Double.NaN, landHere = Double.NaN, dry = Double.NaN;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double r = radius[a];
                double dx = wx - p.x(), dz = wz - p.z();
                if (Math.abs(dx) > 1.4 * r || Math.abs(dz) > 1.4 * r) continue;
                // the outline wanders: distance is stretched by a slow noise that depends on direction
                double ang = Math.atan2(dz, dx);
                double wob = 1 + SHAPE_WARP * (0.6 * HornsFunction.valueNoise(Math.cos(ang) * 1.6 + p.ix() * 7.1, Math.sin(ang) * 1.6 + p.iz() * 3.3, seed + 501)
                        + 0.4 * HornsFunction.valueNoise(Math.cos(ang) * 3.7 + p.ix() * 5.3, Math.sin(ang) * 3.7 + p.iz() * 9.1, seed + 502));
                // 0.5.13: the top is a short crest, not a point. Distance is taken to a line through
                // the summit (its direction and length the peak's own), and the crest falls a little
                // from the summit towards each end, further towards one than the other.
                double ca = Math.PI * HornsFunction.hash(p.ix(), p.iz(), seed + 540), cx = Math.cos(ca), cz = Math.sin(ca);
                double half = (CREST_MIN + (CREST_MAX - CREST_MIN) * HornsFunction.hash(p.ix(), p.iz(), seed + 541)) / 2 * Math.min(1, (topY[a] - baseY[a]) / CREST_FULL);
                double lean = 0.3 + 0.7 * HornsFunction.hash(p.ix(), p.iz(), seed + 542);            // one end is shorter
                double along = dx * cx + dz * cz, lim = along > 0 ? half : half * lean;
                double on = Math.max(-lim, Math.min(lim, along));
                double ex = dx - on * cx, ez = dz - on * cz;
                double t = Math.hypot(ex, ez) / (r * wob);
                if (t >= 1) continue;
                if (Double.isNaN(datum)) datum = datumAt(x, z);
                double share = Math.pow(1 - t, PROFILE) * (1 - CREST_FALL * Math.abs(on) / Math.max(1, half)), m = Math.max(0, topY[a] - datum) * share;
                if (WHOLE) {
                    if (Double.isNaN(dry)) dry = dryAt(x, z);
                    m *= dry;                              // no mountain where vanilla's own land is under water (SEA_NONE)
                }
                sumFree += Math.pow(m, SMOOTH_MAX);
                if (walls != null) {
                    // each mountain's own fade towards rivers, as in 0.5.1 (ErodedPeaks.RIVER_FADE)
                    if (Double.isNaN(river)) river = RiverDistance.at((int) Math.floor(x), (int) Math.floor(z), walls.channel());
                    if (fadeOf[a] == 0) fadeOf[a] = Math.max(ErodedPeaks.RIVER_FADE_MIN, Math.min(ErodedPeaks.RIVER_FADE,
                            ErodedPeaks.RIVER_SHARE * RiverDistance.at((int) Math.floor(p.x()), (int) Math.floor(p.z()), walls.channel())));
                    double f = river / fadeOf[a];
                    if (f < 1) {
                        f = f <= 0 ? 0 : f * f * (3 - 2 * f);
                        if (WHOLE) {
                            // 0.6.0: towards a river a mountain comes down to vanilla's LAND, not to nothing. (Fading to
                            // nothing, as 0.5.1 to 0.5.13 did, took the whole side facing a river off a mountain shaped
                            // whole and left vanilla's smooth hill there.) The fade is still complete well short of the
                            // summit (RIVER_SHARE of the summit's own distance to the river), so no summit is touched.
                            // A FIRST TRY capped a mountain at one block of height for each block from the river: summits
                            // near rivers moved 30 blocks or more and came out up to 23 lower (seed -5999978045571072206).
                            if (Double.isNaN(landHere)) landHere = overAt(x, z);
                            m = Math.min(m, landHere + Math.max(0, m - landHere) * f);
                        } else m *= f;
                    }
                }
                sum += Math.pow(m, SMOOTH_MAX);
            }
            // where the mountains would stand high over the river it is covered: no fade (COVER_FROM)
            free[gz * n + gx] = (float) Math.pow(sumFree, 1 / SMOOTH_MAX);
            riverAt[gz * n + gx] = Double.isNaN(river) ? 1e9f : (float) river;
            double b = ramp((Math.pow(sumFree, 1 / SMOOTH_MAX) - COVER_FROM) / (COVER_FULL - COVER_FROM));
            // 0.6.1: wherever two lumps overlap that far, river or no river (up to 0.6.0 only where a lump had been faded
            // towards a river). The valley of a covered river is filled to the base level by this share (see build()), and
            // the valley is wider than the fade: with the old test the fill stopped dead at the fade's edge.
            bury[gz * n + gx] = (float) b;
            sum += (sumFree - sum) * b;
            double v = Math.pow(sum, 1 / SMOOTH_MAX);
            if (v > 0.5) {
                double share = Math.min(1, v / ROUGH_FULL), noise = 0, amp = 1, freq = 1 / ROUGH_LENGTH, norm = 0;
                for (int o = 0; o < 4; o++) { noise += amp * HornsFunction.valueNoise(x * freq, z * freq, seed + 610 + 13L * o); norm += amp; amp *= 0.5; freq *= 2.1; }
                v = Math.max(0, v + ROUGH * share * noise / norm);
            }
            int i = gz * n + gx;
            base[i] = (float) v;
            outlet[i] = v <= 0.5 || gx == 0 || gz == 0 || gx == n - 1 || gz == n - 1;
        }
    }

    private double[] reachOf;
    private float[] land, wetLand, own, free, riverAt;   // free: the lumps before any fade towards rivers; riverAt: blocks to the nearest river; wetLand: vanilla's own land (y), before the mod's river cut
    private int ln;

    private static double ramp(double t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** Looks up vanilla's land every LAND_STEP blocks wherever a lump stands (it is vanilla's smooth target height, and looking it up is slow). */
    private void lookUpLand() {
        ln = (int) Math.ceil((n - 1) * sp / LAND_STEP) + 4;
        land = new float[ln * ln]; wetLand = new float[ln * ln];
        java.util.Arrays.fill(wetLand, 200f);            // where nothing is looked up: dry
        double[] both = new double[2];
        boolean[] need = new boolean[ln * ln];
        for (int a = 0; a < peaks.size(); a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            double r = 1.45 * reachOf[a];
            int x0 = (int) Math.floor((p.x() - r - ox) / LAND_STEP), x1 = (int) Math.ceil((p.x() + r - ox) / LAND_STEP) + 3;
            int z0 = (int) Math.floor((p.z() - r - oz) / LAND_STEP), z1 = (int) Math.ceil((p.z() + r - oz) / LAND_STEP) + 3;
            for (int zz = Math.max(0, z0); zz <= Math.min(ln - 1, z1); zz++) for (int xx = Math.max(0, x0); xx <= Math.min(ln - 1, x1); xx++) need[zz * ln + xx] = true;
        }
        for (int lz = 0; lz < ln; lz++) for (int lx = 0; lx < ln; lx++) if (need[lz * ln + lx]) {
            PeakStrength.lands((int) ox + (lx - 1) * LAND_STEP, (int) oz + (lz - 1) * LAND_STEP, both);
            wetLand[lz * ln + lx] = (float) (128 + 128 * both[0]);
            land[lz * ln + lx] = (float) (128 + 128 * both[1]);
        }
    }

    // ---- 0.6.1: no mountain where vanilla's own land is under water ----
    // The sea, a lake, and every river vanilla itself runs at water level (all rivers outside mountain country).
    // With the peaks half as big again and mountain country reaching to within 60 blocks of the coast, the lumps
    // ran 150 to 200 blocks out into the sea (first builds of 0.6.1: the frozen island at -2880 1030 on seed
    // -3465664254209586682 came out twice its size, and on the coast seed a river that held water in 0.6.0 was
    // buried under a massif). A lump is nothing where vanilla's own land is at SEA_NONE or lower and whole where
    // it is at SEA_FULL or higher. It is vanilla's land BEFORE the mod's river cut that counts: the valleys
    // vanilla lifts dry in mountain country are not "water" for this, and those are the ones a mountain may cover.
    static double SEA_NONE = 54, SEA_FULL = 68;

    /** How far a place is dry land by vanilla's own reckoning: 0 under water, 1 on land. 1 beyond the map of the land. */
    private double dryAt(double x, double z) {
        double gx = (x - ox) / LAND_STEP + 1, gz = (z - oz) / LAND_STEP + 1;
        if (wetLand == null || gx < 1 || gz < 1 || gx > ln - 3 || gz > ln - 3) return 1;
        return ramp((cubicAt(wetLand, ln, gx, gz) - SEA_NONE) / (SEA_FULL - SEA_NONE));
    }

    /** How far the land stands above the base level at a place (0 where it is below it). */
    private double overAt(double x, double z) { return Math.max(0, gapAt(x, z)); }

    /** The land's height less the base level at a place: below zero where the land lies under it (a river, the sea). 0 beyond the map of the land. */
    private double gapAt(double x, double z) {
        double gx = (x - ox) / LAND_STEP + 1, gz = (z - oz) / LAND_STEP + 1;
        if (gx < 1 || gz < 1 || gx > ln - 3 || gz > ln - 3) return 0;
        return cubicAt(land, ln, gx, gz) - datumAt(x, z);
    }

    /**
     * The drape. Vanilla's hill under a peak is often higher than the eroded shape over much of
     * the mountain (a first try without this, on real chunks at -2840 998 of Sammi's seed
     * -3465664254209586682: carved ridges ran out to either side and the whole front was vanilla's
     * smooth dome). The land cannot be lowered, so the shape is lifted, smoothly, until on average
     * it lies COVER blocks above the land; the couloirs cut afterwards then run down to the land
     * and the ribs stand out of it. The lift is worked out from the shape and the land both
     * blurred over DRAPE_BLUR blocks, so what the water carved is kept; it fades to nothing
     * towards the foot of the mountain (where the shape is under DRAPE_FULL blocks high), so land
     * beyond the mountain is left alone; and ground already standing well clear of the land is
     * lifted less. It comes before the summits are set to their heights, so it cannot move them.
     */
    private void drape() {
        float[] gap = new float[h.length], over = new float[h.length];
        for (int i = 0; i < h.length; i++) {
            if (base[i] <= 0) continue;
            over[i] = (float) overAt(ox + (i % n) * sp, oz + (i / n) * sp);
            gap[i] = over[i] - h[i];
        }
        int radius = Math.max(1, (int) Math.round(DRAPE_BLUR / sp));
        float[] blurGap = blur(gap, n, radius), blurShape = blur(free, n, radius), blurOver = blur(over, n, radius);
        own = new float[h.length];
        for (int i = 0; i < h.length; i++) {
            if (outlet[i] || h[i] <= 0) continue;
            double x = blurGap[i] + COVER, lift = 0.5 * (x + Math.sqrt(x * x + 36));        // a soft "above zero"
            // "the mountain's own ground": where its shape has some height, and at least a fair share of the
            // land's own height above the base level. (Without the second, the thin outer foot of a mountain
            // was lifted over the top of a neighbouring hill of vanilla's 80 blocks high and skinned it in rock.)
            // (Judged by the lump as it was before its fade towards rivers: the side of a mountain that
            // faces a river is its own ground too. There the lift itself fades out, within RIVER_DRAPE
            // blocks of the river, so the river keeps its banks.)
            double f = ramp((blurShape[i] - DRAPE_FROM) / (DRAPE_FULL - DRAPE_FROM)) * ramp((blurShape[i] / (blurOver[i] + 8) - OWN_FROM) / (OWN_FULL - OWN_FROM))
                    * Math.max(bury[i], ramp(riverAt[i] / RIVER_DRAPE));     // 0.6.1: not where the river is covered
            own[i] = (float) f;
            double clear = ramp((h[i] - over[i] - COVER) / DRAPE_CLEAR);
            h[i] += (float) (lift * f * (1 - clear));
        }
    }

    /** The base level at a place: the peaks' own base levels, blended. */
    private double datumAt(double x, double z) {
        // 0.6.1: read from the coarse grid where it has been worked out (it is asked for at every fine sample, and
        // working it out afresh each time was a tenth of a region's time). It is a smooth blend over 0.6 of a cell.
        if (datumGrid != null) {
            double gx = (x - ox) / sp, gz = (z - oz) / sp;
            if (gx >= 0 && gz >= 0 && gx <= n - 1 && gz <= n - 1) {
                int ix = Math.min(n - 2, (int) gx), iz = Math.min(n - 2, (int) gz), i = iz * n + ix;
                double tx = gx - ix, tz = gz - iz;
                return datumGrid[i] * (1 - tx) * (1 - tz) + datumGrid[i + 1] * tx * (1 - tz) + datumGrid[i + n] * (1 - tx) * tz + datumGrid[i + n + 1] * tx * tz;
            }
        }
        return datumExact(x, z);
    }

    private float[] datumGrid;

    private double datumExact(double x, double z) {
        double sig = BASE_BLEND * cell, sw = 0, sv = 0, nearest = Double.MAX_VALUE, nearestBase = 0;
        for (int a = 0; a < baseY.length; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            double dx = x - p.x(), dz = z - p.z(), d2 = dx * dx + dz * dz;
            if (d2 < nearest) { nearest = d2; nearestBase = baseY[a]; }
            if (d2 > 36 * sig * sig) continue;
            double w = Math.exp(-d2 / (2 * sig * sig));
            sw += w; sv += w * baseY[a];
        }
        return sw < 1e-9 ? nearestBase : sv / sw;
    }

    private void erode() {
        // The range rises slowly (fastest under the summits) while the water cuts it; it starts as a low rough plain.
        for (int i = 0; i < h.length; i++) h[i] = outlet[i] ? 0 : (float) (0.03 * base[i]);
        for (int s = 0; s < STEPS; s++) {
            for (int i = 0; i < h.length; i++) if (!outlet[i]) h[i] += (float) (UPLIFT * base[i] / STEPS);
            water(); thermal(THERMAL_PASSES);
        }
        if (WHOLE && COVER > 0) drape();
        // Three times: the ratios are blended between neighbouring peaks, and since 0.6.0 they differ more from
        // peak to peak (one held down by a river beside one that is not), so once left summits up to 23 blocks
        // short of their set heights (seed -5999978045571072206, 4098 -732: y 283 in 0.5.13, 260 after one pass).
        rescale(); rescale();
        rescale();
        thermal(2);
    }

    /** See rescale(): how far inside the square a summit must be to have any say (blocks), and a full say. */
    static double EDGE_NONE = 24, EDGE_FULL = 160;

    /** Scales each mountain so its top is its set height again (ratios blended smoothly between peaks). */
    private void rescale() {
        int np = peaks.size();
        double[] ratio = new double[np], trust = new double[np];
        int reach = (int) Math.ceil(30 / sp);
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            int gx = (int) Math.round((p.x() - ox) / sp), gz = (int) Math.round((p.z() - oz) / sp);
            // 0.6.1: a peak whose summit is outside the square being worked on, or close to its edge, has no top to
            // measure here (the edge is where the water runs off, so the ground is cut low there). It used to count
            // with a ratio of 1 or of whatever its stump gave, which threw off its neighbours' flanks: the region
            // next door, where the same peak stands whole, scaled them differently, and the two disagreed by 10
            // blocks or more over a tenth of the band where they are blended. Such a peak now has no say.
            double inside = Math.min(Math.min(p.x() - ox, ox + (n - 1) * sp - p.x()), Math.min(p.z() - oz, oz + (n - 1) * sp - p.z()));
            trust[a] = ramp((inside - EDGE_NONE) / (EDGE_FULL - EDGE_NONE));
            double top = 0;
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                int xx = gx + dx, zz = gz + dz;
                if (xx >= 0 && zz >= 0 && xx < n && zz < n) top = Math.max(top, h[zz * n + xx]);
            }
            ratio[a] = top < 5 ? 1 : Math.max(0.4, Math.min(4, target[a] / top));
            if (DEBUG && top >= 5) System.err.printf("MASSIF peak %d %d: own height %.0f, stature %.0f, target %.0f, eroded to %.0f, ratio %.2f%n", (int) p.x(), (int) p.z(), p.h(), topY[a] - baseY[a], target[a], top, ratio[a]);
        }
        double sig = 0.35 * cell;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            int i = gz * n + gx;
            if (h[i] <= 0) continue;
            double x = ox + gx * sp, z = oz + gz * sp, sw = 1e-4, sr = 1e-4;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double dx = x - p.x(), dz = z - p.z();
                if (Math.abs(dx) > 4 * sig || Math.abs(dz) > 4 * sig) continue;
                double w = trust[a] * Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; sr += w * ratio[a];
            }
            h[i] = (float) (h[i] * sr / sw);
        }
    }


    /**
     * One step of running water. A flood from the outlets (flat ground, rivers, the edge) gives
     * the order to work in and fills pits; water is shared out over every lower neighbour by
     * slope, so valleys are not held to eight directions; each cell is then cut towards its
     * steepest lower neighbour, lowest cells first, so one pass is enough however big the step.
     */
    private void water() {
        int N = n * n;
        // (working arrays kept between the 80 steps; as in gullies(), flat ground that touches no mountain is left out)
        if (wRec == null) { wRec = new int[N]; wOrder = new int[N]; wSeen = new boolean[N]; wFill = new float[N]; wArea = new double[N]; wHeap = new Heap(N / 4); }
        int[] rec = wRec, order = wOrder;
        boolean[] seen = wSeen;
        float[] fill = wFill;
        Heap q = wHeap;
        q.clear();
        java.util.Arrays.fill(seen, false);
        if (wEdge == null) {
            int[] list = new int[N]; int m = 0;
            for (int i = 0; i < N; i++) if (outlet[i]) {
                int x = i % n, z = i / n;
                boolean edge = false;
                for (int d = 0; d < 8 && !edge; d++) {
                    int xx = x + DX[d], zz = z + DZ[d];
                    edge = xx >= 0 && zz >= 0 && xx < n && zz < n && !outlet[zz * n + xx];
                }
                if (edge) list[m++] = i;
            }
            wEdge = java.util.Arrays.copyOf(list, m);
        }
        for (int i = 0; i < N; i++) if (outlet[i]) { seen[i] = true; rec[i] = i; fill[i] = h[i]; }
        for (int i : wEdge) q.add(h[i], i);
        int k = 0;
        while (!q.isEmpty()) {
            int i = q.poll();
            order[k++] = i;
            int x = i % n, z = i / n;
            for (int d = 0; d < 8; d++) {
                int xx = x + DX[d], zz = z + DZ[d];
                if (xx < 0 || zz < 0 || xx >= n || zz >= n) continue;
                int j = zz * n + xx;
                if (seen[j]) continue;
                seen[j] = true; rec[j] = i;
                fill[j] = Math.max(h[j], fill[i] + 1e-3f);
                q.add(fill[j], j);
            }
        }
        double[] area = wArea, wts = new double[8];
        java.util.Arrays.fill(area, 0);
        boolean fourth = FLOW_POWER == 4, root = M == 0.5;
        for (int a = k - 1; a >= 0; a--) {
            int i = order[a];
            area[i] += sp * sp;
            if (outlet[i]) continue;
            int x = i % n, z = i / n;
            double total = 0, steep = -1;
            for (int d = 0; d < 8; d++) {
                int xx = x + DX[d], zz = z + DZ[d];
                wts[d] = 0;
                if (xx < 0 || zz < 0 || xx >= n || zz >= n) continue;
                int j = zz * n + xx;
                double drop = fill[i] - fill[j];
                if (drop <= 0) continue;
                if (d >= 4) drop *= INV_SQRT2;
                if (drop > steep) { steep = drop; rec[i] = j; }
                wts[d] = (fourth ? (drop * drop) * (drop * drop) : Math.pow(drop, FLOW_POWER)) * (d < 4 ? 1 : INV_SQRT2);
                total += wts[d];
            }
            if (total <= 0) { area[rec[i]] += area[i]; continue; }
            for (int d = 0; d < 8; d++) if (wts[d] > 0) area[(z + DZ[d]) * n + x + DX[d]] += area[i] * wts[d] / total;
        }
        for (int a = 0; a < k; a++) {
            int i = order[a], r = rec[i];
            if (r == i || outlet[i]) continue;
            int dx = i % n - r % n, dz = i / n - r / n;
            double f = K * (root ? Math.sqrt(area[i]) : Math.pow(area[i], M)) / (sp * (dx != 0 && dz != 0 ? SQRT2 : 1));
            double nh = (h[i] + f * Math.max(h[r], fill[r])) / (1 + f);
            if (nh < h[i]) h[i] = (float) Math.max(0, nh);
        }
    }

    private int[] wRec, wOrder, wEdge;
    private boolean[] wSeen;
    private float[] wFill;
    private double[] wArea;
    private Heap wHeap;

    /** Rock slides: wherever the ground is steeper than it can stand, some of it moves down. */
    private void thermal(int passes) {
        for (int p = 0; p < passes; p++) {
            for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
                int i = z * n + x;
                double hi = h[i];
                if (hi <= 0.5) continue;
                double crit = hi > TALUS_BAND ? TALUS : TALUS_LOW + (TALUS - TALUS_LOW) * hi / TALUS_BAND;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = hi - h[j], max = crit * sp * (d < 4 ? 1 : SQRT2);
                    if (drop > max) { double m = (drop - max) * 0.25; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
    }

    private static double cubicAt(float[] f, int m, double gx, double gz) {
        int ix = Math.max(1, Math.min(m - 3, (int) Math.floor(gx))), iz = Math.max(1, Math.min(m - 3, (int) Math.floor(gz)));
        double tx = gx - ix, tz = gz - iz, a = 0, b = 0, c = 0, d = 0;
        for (int j = -1; j <= 2; j++) {
            int r = (iz + j) * m + ix;
            double v = cubic(f[r - 1], f[r], f[r + 1], f[r + 2], tx);
            if (j == -1) a = v; else if (j == 0) b = v; else if (j == 1) c = v; else d = v;
        }
        return cubic(a, b, c, d, tz);
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    // last in the file, so it runs after every setting above has its own value
    static {
        // for testing: a file /tmp/atlas_set of NAME=number lines sets any of the settings above
        try {
            for (String line : java.nio.file.Files.readAllLines(java.nio.file.Path.of("/tmp/atlas_set"))) {
                String[] kv = line.trim().split("=");
                if (kv.length != 2) continue;
                var f = Massif.class.getDeclaredField(kv[0]);
                if (f.getType() == int.class) f.setInt(null, Integer.parseInt(kv[1])); else f.setDouble(null, Double.parseDouble(kv[1]));
            }
        } catch (Exception ignored) { }
    }
}
