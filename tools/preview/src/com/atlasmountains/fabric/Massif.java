package com.atlasmountains.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Experiment for the preview (not in the mod): the shape made by water, not drawn by hand.
 * <p>
 * Version C, chosen by Sammi on 14 Nov 2026 from four in the preview: peaks in a group are
 * joined (they share a base and meet at saddles). Each peak gets a rough lump; the lumps rise
 * slowly while running water cuts them (stream power: a cell is cut by the square root of the
 * area draining through it), with rock slides after every step, so ridges and valleys come from
 * the drainage. Each mountain is then scaled so its summit is its set height.
 * <p>
 * Two passes: the big shape on a COARSE grid (8 blocks), then the old mod's droplet erosion on
 * a 4-block grid, which carves gullies into rock faces whatever their catchment, so the tops
 * are cut as well as the valleys.
 * <p>
 * SEPARATE (version B, not chosen) is kept for comparison: each lump stops half-way to its
 * nearest neighbour.
 */
final class Massif {
    // lump
    static double JOIN_REACH = 1.00;     // a lump's radius, in cells, for a full-height peak
    static double SEP_REACH = 0.48;      // separate: a lump's radius, as a share of the distance to the nearest peak
    static double PROFILE = 1.35;        // how concave a lump's sides are (1 straight cone)
    static double SMOOTH_MAX = 4;        // how lumps combine where they overlap (higher: closer to the taller one)
    static double SHAPE_WARP = 0.30;     // how far a lump's outline wanders, as a share of its radius
    static double ROUGH = 0.28;          // noise on the lump, as a share of 120 blocks
    static double ROUGH_LENGTH = 110;
    // water, on the coarse grid
    static double COARSE = 8;
    static double K = 0.02, M = 0.5, FLOW_POWER = 4;
    static double UPLIFT = 1.5;
    static int STEPS = 250;
    static double ICE = 0, ICE_FROM = 0.5, ICE_BAND = 0.2;
    // rock
    static double TALUS = 1.30, TALUS_LOW = 0.75;
    static int THERMAL_PASSES = 4;
    // the fine pass (4 blocks): the old mod's detail noise, droplets and slides
    static double FINE_STEP = 4;
    static double DETAIL = 9.0, DETAIL_LENGTH = 44;
    static double DROPLETS = 1.0;        // as a share of ErodedPeaks' density; 0 skips the fine pass
    static double ROCK_FROM = 0.25;      // droplets dig above this share of the summit height round about
    static int FINE_THERMAL = 20;
    static boolean RESCALE = true;
    static double FINE = 1.5, FINE_LENGTH = 7;

    double sp;
    int n;
    double ox, oz;
    float[] h, base, ref;
    boolean[] outlet;
    final List<ErodedPeaks.Peak> peaks = new ArrayList<>();
    final double cell;
    long millisCoarse, millisFine;

    Massif(HornsFunction horns, double x0, double z0, int size, boolean joined) {
        long seed = AtlasMountainsMod.seed();
        double margin = 320;
        ox = x0 - margin; oz = z0 - margin;
        cell = horns.cell();
        long t0 = System.nanoTime();
        grid(COARSE, size + 2 * margin);
        long c0x = (long) Math.floor(ox / cell) - 2, c0z = (long) Math.floor(oz / cell) - 2;
        long c1x = (long) Math.floor((ox + n * sp) / cell) + 2, c1z = (long) Math.floor((oz + n * sp) / cell) + 2;
        for (long iz = c0z; iz <= c1z; iz++) for (long ix = c0x; ix <= c1x; ix++) {
            ErodedPeaks.Peak p = horns.peakOf(ix, iz, seed);
            if (p != null) peaks.add(p);
        }
        int np = peaks.size();
        double[] radius = new double[np];
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            double nearest = 1e9;
            for (int b = 0; b < np; b++) if (b != a) nearest = Math.min(nearest, Math.hypot(p.x() - peaks.get(b).x(), p.z() - peaks.get(b).z()));
            radius[a] = joined ? JOIN_REACH * cell * (0.55 + 0.45 * p.h() / horns.height()) : SEP_REACH * nearest;
        }
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double x = ox + gx * sp, z = oz + gz * sp;
            double wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
            double sum = 0, top = 0;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double r = radius[a];
                double dx = wx - p.x(), dz = wz - p.z();
                if (Math.abs(dx) > 1.4 * r || Math.abs(dz) > 1.4 * r) continue;
                double ang = Math.atan2(dz, dx);
                double wob = 1 + SHAPE_WARP * (0.6 * HornsFunction.valueNoise(Math.cos(ang) * 1.6 + p.ix() * 7.1, Math.sin(ang) * 1.6 + p.iz() * 3.3, seed + 501)
                        + 0.4 * HornsFunction.valueNoise(Math.cos(ang) * 3.7 + p.ix() * 5.3, Math.sin(ang) * 3.7 + p.iz() * 9.1, seed + 502));
                double t = Math.hypot(dx, dz) / (r * wob);
                if (t >= 1) continue;
                double m = p.h() * Math.pow(1 - t, PROFILE);
                if (m > top) top = m;
                sum += Math.pow(m, SMOOTH_MAX);
            }
            double v = joined ? Math.pow(sum, 1 / SMOOTH_MAX) : top;
            if (v > 0.5) {
                double share = Math.min(1, v / 120), noise = 0, amp = 1, freq = 1 / ROUGH_LENGTH, norm = 0;
                for (int o = 0; o < 4; o++) { noise += amp * HornsFunction.valueNoise(x * freq, z * freq, seed + 610 + 13L * o); norm += amp; amp *= 0.5; freq *= 2.1; }
                v = Math.max(0, v + ROUGH * 120 * share * noise / norm);
            }
            int i = gz * n + gx;
            base[i] = (float) v;
            outlet[i] = v <= 0.5 || gx == 0 || gz == 0 || gx == n - 1 || gz == n - 1;
        }
        // The range rises slowly (fastest under the summits) while the water cuts it.
        for (int i = 0; i < h.length; i++) h[i] = outlet[i] ? 0 : (float) (0.03 * base[i]);
        for (int s = 0; s < STEPS; s++) {
            for (int i = 0; i < h.length; i++) if (!outlet[i]) h[i] += (float) (UPLIFT * base[i] / STEPS);
            if (ICE > 0 && s % 10 == 0) ref = summits();
            water(); thermal(THERMAL_PASSES);
        }
        if (RESCALE) rescale();
        thermal(2);
        millisCoarse = (System.nanoTime() - t0) / 1_000_000;
        if (DROPLETS > 0) {
            t0 = System.nanoTime();
            refine(seed);
            millisFine = (System.nanoTime() - t0) / 1_000_000;
        }
        for (int i = 0; i < h.length; i++) base[i] = Math.max(base[i], h[i]);   // base is now "before the fine pass", for carve
    }

    private void grid(double spacing, double span) {
        sp = spacing;
        n = (int) Math.ceil(span / sp) + 1;
        h = new float[n * n]; base = new float[n * n]; outlet = new boolean[n * n];
    }

    /** The fine pass: the coarse shape on a 4-block grid, the old detail noise, droplets, slides. */
    private void refine(long seed) {
        int cn = n; double csp = sp; float[] ch = h;
        grid(FINE_STEP, (cn - 1) * csp);
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double v = cubicAt(ch, cn, gx * sp / csp, gz * sp / csp);
            int i = gz * n + gx;
            h[i] = (float) Math.max(0, v);
            outlet[i] = h[i] <= 0.5;
        }
        ref = summits();
        float[] rock = new float[n * n];
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            int i = gz * n + gx;
            if (!outlet[i]) {
                double x = ox + gx * sp, z = oz + gz * sp;
                double w = Math.pow(Math.min(1, h[i] / Math.max(1, ref[i])), 2.0);
                h[i] = (float) Math.max(0.6, h[i] + DETAIL * (ridged(x, z, seed) - 0.5) * w);
            }
            base[i] = h[i];
            rock[i] = (float) (ROCK_FROM * ref[i]);
        }
        droplets(rock, new Random(seed * 0x9E3779B97F4A7C15L + 808));
        thermalFine(rock, FINE_THERMAL);
        for (int i = 0; i < h.length; i++) if (outlet[i]) h[i] = Math.min(Math.max(h[i], 0), 2);
        if (RESCALE) rescale();
    }

    private static double ridged(double x, double z, long seed) {
        double sum = 0, amp = 1, norm = 0, freq = 1.0 / DETAIL_LENGTH;
        for (int o = 0; o < 3; o++) {
            sum += amp * (1 - Math.abs(HornsFunction.valueNoise(x * freq, z * freq, seed + 606 + 31L * o)));
            norm += amp; amp *= 0.5; freq *= 2;
        }
        return sum / norm;
    }

    private double summitOf(ErodedPeaks.Peak p) {
        int gx = (int) Math.round((p.x() - ox) / sp), gz = (int) Math.round((p.z() - oz) / sp);
        int reach = (int) Math.ceil(30 / sp);
        double top = 0;
        for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
            int xx = gx + dx, zz = gz + dz;
            if (xx >= 0 && zz >= 0 && xx < n && zz < n) top = Math.max(top, h[zz * n + xx]);
        }
        return top;
    }

    /** Scales each mountain so its top is its set height again (ratios blended smoothly between peaks). */
    private void rescale() {
        int np = peaks.size();
        double[] ratio = new double[np];
        for (int a = 0; a < np; a++) {
            double top = summitOf(peaks.get(a));
            ratio[a] = top < 5 ? 1 : Math.max(0.4, Math.min(4, peaks.get(a).h() / top));
        }
        double sig = 0.35 * cell;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            int i = gz * n + gx;
            if (h[i] <= 0) continue;
            double x = ox + gx * sp, z = oz + gz * sp, sw = 1e-4, sr = 1e-4;
            for (ErodedPeaks.Peak p : peaks) {
                double dx = x - p.x(), dz = z - p.z();
                if (Math.abs(dx) > 4 * sig || Math.abs(dz) > 4 * sig) continue;
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; sr += w * ratio[peaks.indexOf(p)];
            }
            h[i] = (float) (h[i] * sr / sw);
        }
    }

    /** The height of the summits round each cell (blended as the snow line is). */
    private float[] summits() {
        int np = peaks.size();
        double[] top = new double[np];
        for (int a = 0; a < np; a++) top[a] = summitOf(peaks.get(a));
        float[] r = new float[n * n];
        double sig = 0.4 * cell;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double x = ox + gx * sp, z = oz + gz * sp, sw = 1e-9, st = 0;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double dx = x - p.x(), dz = z - p.z();
                if (Math.abs(dx) > 4 * sig || Math.abs(dz) > 4 * sig) continue;
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; st += w * top[a];
            }
            r[gz * n + gx] = (float) (sw < 1e-6 ? 120 : st / sw);
        }
        return r;
    }

    private static final int[] DX = {1, 0, -1, 0, 1, -1, -1, 1}, DZ = {0, 1, 0, -1, 1, 1, -1, -1};

    /** One step of running water: priority flood from the outlets gives an order to work in; water spreads by slope; a cell is cut towards its steepest lower neighbour. */
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
            double dist = sp * Math.sqrt(dx * dx + dz * dz);
            double f = K * Math.pow(area[i], M) / dist;
            if (ICE > 0 && ref != null) {
                double t = Math.max(0, Math.min(1, (h[i] / Math.max(1, ref[i]) - ICE_FROM) / ICE_BAND));
                f += ICE * Math.pow(area[i], 0.25) * t * t * (3 - 2 * t) / dist;
            }
            double hr = Math.max(h[r], fill[r]);
            double nh = (h[i] + f * hr) / (1 + f);
            if (nh < h[i]) h[i] = (float) Math.max(0, nh);
        }
    }

    private void thermal(int passes) {
        for (int p = 0; p < passes; p++) {
            for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
                int i = z * n + x;
                double hi = h[i];
                if (hi <= 0.5) continue;
                double crit = hi > 42 ? TALUS : TALUS_LOW + (TALUS - TALUS_LOW) * hi / 42;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = hi - h[j], max = crit * sp * (d < 4 ? 1 : Math.sqrt(2));
                    if (drop > max) { double m = (drop - max) * 0.25; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
    }

    /** ErodedPeaks' thermal: rock stands steep above its line, scree gentler below. */
    private void thermalFine(float[] rock, int passes) {
        for (int p = 0; p < passes; p++) {
            for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
                int i = z * n + x;
                double hi = h[i];
                if (hi <= 0.5) continue;
                double t = Math.max(0, Math.min(1, (hi - rock[i]) / ErodedPeaks.THERMAL_BAND + 0.5));
                t = t * t * (3 - 2 * t);
                double crit = ErodedPeaks.THERMAL_FLANK + (ErodedPeaks.THERMAL_ROCK - ErodedPeaks.THERMAL_FLANK) * t;
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = hi - h[j], max = crit * sp * (d < 4 ? 1 : Math.sqrt(2));
                    if (drop > max) { double m = (drop - max) * ErodedPeaks.THERMAL_RATE; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
    }

    /** ErodedPeaks' droplets (after Hans Beyer), the same numbers, on this grid; ground at 0. */
    private void droplets(float[] rock, Random rng) {
        int mountain = 0;
        for (boolean f : outlet) if (!f) mountain++;
        int count = (int) Math.round(DROPLETS * ErodedPeaks.DROPLETS_PER_SAMPLE * mountain);
        double[] brush = new double[9];
        double sum = 0;
        for (int bz = -1; bz <= 1; bz++) for (int bx = -1; bx <= 1; bx++) {
            double w = Math.max(0, 1.5 - Math.sqrt(bx * bx + bz * bz));
            brush[(bz + 1) * 3 + (bx + 1)] = w; sum += w;
        }
        for (int i = 0; i < 9; i++) brush[i] /= sum;
        for (int d = 0; d < count; d++) {
            double px = 0, pz = 0;
            for (int tries = 0; tries < 12; tries++) {
                px = 1 + rng.nextDouble() * (n - 3); pz = 1 + rng.nextDouble() * (n - 3);
                if (!outlet[(int) pz * n + (int) px]) break;
            }
            double dirX = 0, dirZ = 0, speed = 1, water = 1, sediment = 0;
            for (int life = 0; life < ErodedPeaks.DROPLET_LIFE; life++) {
                int ix = (int) px, iz = (int) pz;
                double fx = px - ix, fz = pz - iz;
                if (ix < 1 || iz < 1 || ix >= n - 2 || iz >= n - 2) break;
                int i00 = iz * n + ix;
                double h00 = h[i00], h10 = h[i00 + 1], h01 = h[i00 + n], h11 = h[i00 + n + 1];
                double gx = ((h10 - h00) * (1 - fz) + (h11 - h01) * fz) / sp;
                double gz = ((h01 - h00) * (1 - fx) + (h11 - h10) * fx) / sp;
                double height = h00 * (1 - fx) * (1 - fz) + h10 * fx * (1 - fz) + h01 * (1 - fx) * fz + h11 * fx * fz;
                dirX = dirX * ErodedPeaks.INERTIA - gx * (1 - ErodedPeaks.INERTIA);
                dirZ = dirZ * ErodedPeaks.INERTIA - gz * (1 - ErodedPeaks.INERTIA);
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
                double capacity = Math.max(-deltaH, ErodedPeaks.MIN_SLOPE_CAPACITY) * speed * water * ErodedPeaks.CAPACITY;
                if (sediment > capacity || deltaH > 0) {
                    double amount = deltaH > 0 ? Math.min(deltaH, sediment) : (sediment - capacity) * ErodedPeaks.DEPOSIT;
                    sediment -= amount;
                    h[i00] += (float) (amount * (1 - fx) * (1 - fz));
                    h[i00 + 1] += (float) (amount * fx * (1 - fz));
                    h[i00 + n] += (float) (amount * (1 - fx) * fz);
                    h[i00 + n + 1] += (float) (amount * fx * fz);
                } else {
                    double amount = Math.min(Math.min((capacity - sediment) * ErodedPeaks.ERODE, -deltaH), ErodedPeaks.MAX_ERODE);
                    double t = (height - (rock[i00] - ErodedPeaks.ERODE_BELOW_ROCK - ErodedPeaks.ERODE_BAND)) / ErodedPeaks.ERODE_BAND;
                    t = Math.max(0, Math.min(1, t));
                    amount *= t * t * (3 - 2 * t);
                    if (amount > 0) {
                        for (int bz = -1; bz <= 1; bz++) for (int bx = -1; bx <= 1; bx++) {
                            int ci = (iz + bz) * n + ix + bx;
                            double take = Math.min(amount * brush[(bz + 1) * 3 + (bx + 1)], Math.max(0, h[ci] + ErodedPeaks.FLOOR_DIG));
                            h[ci] -= (float) take;
                            sediment += take;
                        }
                    }
                }
                speed = Math.min(ErodedPeaks.MAX_SPEED, Math.sqrt(Math.max(0, speed * speed - deltaH * ErodedPeaks.GRAVITY)));
                water *= 1 - ErodedPeaks.EVAPORATE;
                px = nx; pz = nz;
            }
        }
    }

    private static double cubicAt(float[] f, int m, double gx, double gz) {
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        ix = Math.max(1, Math.min(m - 3, ix)); iz = Math.max(1, Math.min(m - 3, iz));
        double tx = gx - ix, tz = gz - iz;
        double[] row = new double[4];
        for (int j = -1; j <= 2; j++) {
            int r = (iz + j) * m + ix;
            row[j + 1] = cubic(f[r - 1], f[r], f[r + 1], f[r + 2], tx);
        }
        return cubic(row[0], row[1], row[2], row[3], tz);
    }

    /** Height above the ground at a block, cubic between samples, with the same fine noise as ErodedPeaks. */
    double relief(double x, double z) {
        double gx = (x - ox) / sp, gz = (z - oz) / sp;
        if (gx < 1 || gz < 1 || gx > n - 3 || gz > n - 3) return 0;
        double v = cubicAt(h, n, gx, gz);
        if (v <= 0) return 0;
        long seed = AtlasMountainsMod.seed();
        return Math.max(0, v + FINE * 0.5 * HornsFunction.valueNoise(x / FINE_LENGTH, z / FINE_LENGTH, seed + 909) * Math.min(1, v / 8));
    }

    /** How far the fine pass cut here (blocks), for the snow's gully rule. */
    double carve(double x, double z) {
        int ix = (int) Math.round((x - ox) / sp), iz = (int) Math.round((z - oz) / sp);
        if (ix < 0 || iz < 0 || ix >= n || iz >= n) return 0;
        return base[iz * n + ix] - h[iz * n + ix];
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }
}
