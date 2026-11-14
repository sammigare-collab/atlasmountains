package com.atlasmountains.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

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
    /** Noise on the lumps (blocks, for ground 120 or more above the foot) and its length. */
    static double ROUGH = 0.28 * 120, ROUGH_LENGTH = 110;
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
    static double BURY_FROM = 0.15, BURY_FULL = 0.32;
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
    static double TALUS = 1.30, TALUS_LOW = 0.75, TALUS_BAND = 42;
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
    static double GULLY_FROM = 22, GULLY_ABOVE = 60;
    /** The steepest a gully's wall may stand (62 degrees). */
    static double GULLY_WALL = 1.9;
    /** How strongly the faces' water keeps to the steepest way down. Lower than FLOW_POWER: at 4 some gullies ran dead straight along the grid. */
    static double GULLY_FLOW = 1.6;

    private static final int[] DX = {1, 0, -1, 0, 1, -1, -1, 1}, DZ = {0, 1, 0, -1, 1, 1, -1, -1};

    private final int n;
    private final double sp = COARSE, ox, oz, cell;
    private final float[] h, base, bury;
    private final boolean[] outlet;
    private final List<ErodedPeaks.Peak> peaks = new ArrayList<>();

    /** How far from its summit a peak's lump can reach, in blocks. */
    static double reach(HornsFunction horns, ErodedPeaks.Peak p) {
        return JOIN_REACH * horns.cell() * (0.55 + 0.45 * p.h() / horns.height()) * (1 + SHAPE_WARP);
    }

    /**
     * Fills relief (blocks above the ground), carve (how far below its lump the ground ended up)
     * and buried (1 where a river between two peaks is covered, see BURY_FROM) for a square of size x size samples, spacing blocks apart, from (originX, originZ).
     * Returns false, leaving both untouched, if no peak reaches the square.
     */
    static boolean build(HornsFunction horns, long seed, int originX, int originZ, int size, int spacing, float[] relief, float[] carve, float[] buried) {
        Massif m = new Massif(horns, seed, originX, originZ, size * spacing);
        if (m.peaks.isEmpty()) return false;
        m.lumps(horns, seed);
        m.erode();
        double step = spacing / m.sp;
        for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) {
            int i = gz * size + gx;
            double v = Math.max(0, cubicAt(m.h, m.n, gx * step, gz * step));
            double b = Math.max(v, cubicAt(m.base, m.n, gx * step, gz * step));
            relief[i] = (float) v;
            carve[i] = (float) (b - v);
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
        if (GULLY_RUNS > 0) gullies(relief, carve, deep, size, spacing, originX, originZ, seed);
        return true;
    }

    // ---- SAMPLER (0.5.11): three looks, for Sammi to compare in the game ("im not sure if the mountain shape is right yet") ----
    // A as it was. B the faces carved about twice as deep (the "deep" carve he passed over in renders on 14 Nov, where
    // there was no sun or shadow to judge it by). C the same deep carve on a sharper mountain: hollow profile, steeper
    // upper faces, narrower top (aimed at the cones and humps).
    /** How much deeper than GULLY_CUT and FACE_NOISE each look's faces are carved, and the power of its profile (1: unchanged). */
    static final double[] LOOK_CARVE = {1.0, 2.0, 2.0}, LOOK_SHARP = {1.0, 1.0, 1.4};
    /** For testing: a file /tmp/atlas_look holding 0, 1 or 2 gives every peak that look. */
    static final int FORCE_LOOK = forced();
    private static int forced() {
        try { return Integer.parseInt(java.nio.file.Files.readString(java.nio.file.Path.of("/tmp/atlas_look")).trim()); } catch (Exception e) { return -1; }
    }

    /** One number per peak (its set height if perLook is null, else perLook[its look]) spread smoothly over a fine grid. */
    private float[] spread(int size, int spacing, int unused, double[] perLook) {
        float[] out = new float[size * size];
        double sig = 0.35 * cell;
        for (int gz = 0; gz < size; gz++) for (int gx = 0; gx < size; gx++) {
            double x = ox + gx * spacing, z = oz + gz * spacing, sw = 1e-6, sv = 0;
            for (ErodedPeaks.Peak p : peaks) {
                double dx = x - p.x(), dz = z - p.z();
                if (Math.abs(dx) > 4 * sig || Math.abs(dz) > 4 * sig) continue;
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; sv += w * (perLook == null ? p.h() : perLook[FORCE_LOOK >= 0 ? FORCE_LOOK : p.look()]);
            }
            out[gz * size + gx] = sw < 1e-5 ? (perLook == null ? 120f : 1f) : (float) (sv / sw);
        }
        return out;
    }

    /** The faces' own pass: see FACE_NOISE. Lowers relief, and adds what it cut to carve (the snow lies lower in gullies). */
    private static void gullies(float[] h, float[] carve, float[] deep, int n, double sp, int originX, int originZ, long seed) {
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
        for (int run = 0; run < GULLY_RUNS; run++) {
            java.util.Arrays.fill(seen, false);
            java.util.Arrays.fill(area, 0);
            PriorityQueue<long[]> q = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[0]), Float.intBitsToFloat((int) b[0])));
            for (int i = 0; i < N; i++) if (out[i]) { seen[i] = true; fill[i] = h[i]; q.add(new long[]{Float.floatToIntBits(h[i]), i}); }
            int k = 0;
            while (!q.isEmpty()) {
                int i = (int) q.poll()[1];
                order[k++] = i;
                int x = i % n, z = i / n;
                for (int d = 0; d < 8; d++) {
                    int xx = x + DX[d], zz = z + DZ[d];
                    if (xx < 0 || zz < 0 || xx >= n || zz >= n) continue;
                    int j = zz * n + xx;
                    if (seen[j]) continue;
                    seen[j] = true;
                    fill[j] = Math.max(h[j], fill[i] + 1e-3f);
                    q.add(new long[]{Float.floatToIntBits(fill[j]), j});
                }
            }
            for (int a = k - 1; a >= 0; a--) {
                int i = order[a];
                area[i] += 1;
                if (out[i]) continue;
                int x = i % n, z = i / n;
                double total = 0;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = (fill[i] - fill[j]) / (d < 4 ? 1 : Math.sqrt(2));
                    wts[d] = drop <= 0 ? 0 : Math.pow(drop, GULLY_FLOW) * (d < 4 ? 1 : 1 / Math.sqrt(2));
                    total += wts[d];
                }
                if (total <= 0) continue;
                for (int d = 0; d < 8; d++) if (wts[d] > 0) area[(z + DZ[d]) * n + x + DX[d]] += area[i] * wts[d] / total;
            }
            for (int i = 0; i < N; i++) {
                if (out[i] || weight[i] <= 0) continue;
                // nothing where only the cell itself drains (crests and summits keep their height)
                double cut = GULLY_CUT * deep[i] * weight[i] * Math.min(1, Math.sqrt(Math.max(0, area[i] - 1.5) / GULLY_FULL));
                if (run == 0) {
                    // the noise goes on with the first run, and not on crests and summits (where nothing drains), so no summit moves
                    double wx = originX + (i % n) * sp, wz = originZ + (i / n) * sp;
                    double nz = HornsFunction.valueNoise(wx / FACE_NOISE_LENGTH, wz / FACE_NOISE_LENGTH, seed + 620)
                            + 0.5 * HornsFunction.valueNoise(wx / (FACE_NOISE_LENGTH / 2.2), wz / (FACE_NOISE_LENGTH / 2.2), seed + 621);
                    cut -= FACE_NOISE * deep[i] * weight[i] * nz / 1.5 * Math.min(1, (area[i] - 1) / 4);
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
                    double drop = hi - h[j], max = GULLY_WALL * sp * (d < 4 ? 1 : Math.sqrt(2));
                    if (drop > max && !out[j]) { double m = (drop - max) * 0.25; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
        for (int i = 0; i < N; i++) carve[i] += Math.max(0f, before[i] - h[i]);
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
        for (int a = 0; a < np; a++) radius[a] = JOIN_REACH * cell * (0.55 + 0.45 * peaks.get(a).h() / horns.height());
        RiverWallFunction walls = RiverWallFunction.current;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double x = ox + gx * sp, z = oz + gz * sp;
            double wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
            double sum = 0, sumFree = 0, river = Double.NaN, first = 0, second = 0;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double r = radius[a];
                double dx = wx - p.x(), dz = wz - p.z();
                if (Math.abs(dx) > 1.4 * r || Math.abs(dz) > 1.4 * r) continue;
                // the outline wanders: distance is stretched by a slow noise that depends on direction
                double ang = Math.atan2(dz, dx);
                double wob = 1 + SHAPE_WARP * (0.6 * HornsFunction.valueNoise(Math.cos(ang) * 1.6 + p.ix() * 7.1, Math.sin(ang) * 1.6 + p.iz() * 3.3, seed + 501)
                        + 0.4 * HornsFunction.valueNoise(Math.cos(ang) * 3.7 + p.ix() * 5.3, Math.sin(ang) * 3.7 + p.iz() * 9.1, seed + 502));
                double t = Math.hypot(dx, dz) / (r * wob);
                if (t >= 1) continue;
                double share = Math.pow(1 - t, PROFILE), m = p.h() * share;
                if (share > first) { second = first; first = share; } else if (share > second) second = share;
                sumFree += Math.pow(m, SMOOTH_MAX);
                if (walls != null) {
                    // each mountain's own fade towards rivers, as in 0.5.1 (ErodedPeaks.RIVER_FADE)
                    if (Double.isNaN(river)) river = RiverDistance.at((int) Math.floor(x), (int) Math.floor(z), walls.channel());
                    if (fadeOf[a] == 0) fadeOf[a] = Math.max(ErodedPeaks.RIVER_FADE_MIN, Math.min(ErodedPeaks.RIVER_FADE,
                            ErodedPeaks.RIVER_SHARE * RiverDistance.at((int) Math.floor(p.x()), (int) Math.floor(p.z()), walls.channel())));
                    double f = river / fadeOf[a];
                    if (f < 1) m *= f <= 0 ? 0 : f * f * (3 - 2 * f);
                }
                sum += Math.pow(m, SMOOTH_MAX);
            }
            // between two peaks of a group the river is covered: no fade
            double b = Math.max(0, Math.min(1, (second - BURY_FROM) / (BURY_FULL - BURY_FROM)));
            b = b * b * (3 - 2 * b);
            bury[gz * n + gx] = sumFree > sum ? (float) b : 0f;
            sum += (sumFree - sum) * b;
            double v = Math.pow(sum, 1 / SMOOTH_MAX);
            if (v > 0.5) {
                double share = Math.min(1, v / 120), noise = 0, amp = 1, freq = 1 / ROUGH_LENGTH, norm = 0;
                for (int o = 0; o < 4; o++) { noise += amp * HornsFunction.valueNoise(x * freq, z * freq, seed + 610 + 13L * o); norm += amp; amp *= 0.5; freq *= 2.1; }
                v = Math.max(0, v + ROUGH * share * noise / norm);
            }
            int i = gz * n + gx;
            base[i] = (float) v;
            outlet[i] = v <= 0.5 || gx == 0 || gz == 0 || gx == n - 1 || gz == n - 1;
        }
    }

    private void erode() {
        // The range rises slowly (fastest under the summits) while the water cuts it; it starts as a low rough plain.
        for (int i = 0; i < h.length; i++) h[i] = outlet[i] ? 0 : (float) (0.03 * base[i]);
        for (int s = 0; s < STEPS; s++) {
            for (int i = 0; i < h.length; i++) if (!outlet[i]) h[i] += (float) (UPLIFT * base[i] / STEPS);
            water(); thermal(THERMAL_PASSES);
        }
        rescale();
        thermal(2);
    }

    /** Scales each mountain so its top is its set height again (ratios blended smoothly between peaks). */
    private void rescale() {
        int np = peaks.size();
        double[] ratio = new double[np];
        int reach = (int) Math.ceil(30 / sp);
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            int gx = (int) Math.round((p.x() - ox) / sp), gz = (int) Math.round((p.z() - oz) / sp);
            double top = 0;
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                int xx = gx + dx, zz = gz + dz;
                if (xx >= 0 && zz >= 0 && xx < n && zz < n) top = Math.max(top, h[zz * n + xx]);
            }
            ratio[a] = top < 5 ? 1 : Math.max(0.4, Math.min(4, p.h() / top));
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
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
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
        int[] rec = new int[N], order = new int[N];
        boolean[] seen = new boolean[N];
        float[] fill = new float[N];
        PriorityQueue<long[]> q = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[0]), Float.intBitsToFloat((int) b[0])));
        for (int i = 0; i < N; i++) if (outlet[i]) { seen[i] = true; rec[i] = i; fill[i] = h[i]; q.add(new long[]{Float.floatToIntBits(h[i]), i}); }
        int k = 0;
        while (!q.isEmpty()) {
            int i = (int) q.poll()[1];
            order[k++] = i;
            int x = i % n, z = i / n;
            for (int d = 0; d < 8; d++) {
                int xx = x + DX[d], zz = z + DZ[d];
                if (xx < 0 || zz < 0 || xx >= n || zz >= n) continue;
                int j = zz * n + xx;
                if (seen[j]) continue;
                seen[j] = true; rec[j] = i;
                fill[j] = Math.max(h[j], fill[i] + 1e-3f);
                q.add(new long[]{Float.floatToIntBits(fill[j]), j});
            }
        }
        double[] area = new double[N], wts = new double[8];
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
                double drop = (fill[i] - fill[j]) / (d < 4 ? 1 : Math.sqrt(2));
                if (drop <= 0) continue;
                if (drop > steep) { steep = drop; rec[i] = j; }
                wts[d] = Math.pow(drop, FLOW_POWER) * (d < 4 ? 1 : 1 / Math.sqrt(2));
                total += wts[d];
            }
            if (total <= 0) { area[rec[i]] += area[i]; continue; }
            for (int d = 0; d < 8; d++) if (wts[d] > 0) area[(z + DZ[d]) * n + x + DX[d]] += area[i] * wts[d] / total;
        }
        for (int a = 0; a < k; a++) {
            int i = order[a], r = rec[i];
            if (r == i || outlet[i]) continue;
            int dx = i % n - r % n, dz = i / n - r / n;
            double f = K * Math.pow(area[i], M) / (sp * Math.sqrt(dx * dx + dz * dz));
            double nh = (h[i] + f * Math.max(h[r], fill[r])) / (1 + f);
            if (nh < h[i]) h[i] = (float) Math.max(0, nh);
        }
    }

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
                    double drop = hi - h[j], max = crit * sp * (d < 4 ? 1 : Math.sqrt(2));
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
}
