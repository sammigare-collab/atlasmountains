package com.atlasmountains.fabric;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Experiment for the preview (not in the mod): the shape made by water, not drawn by hand.
 * <p>
 * Each peak gets a rough lump, no crest, no legs, no scoop. The lumps are then worn down by
 * running water over the whole square at once (stream power: a cell is cut in proportion to the
 * square root of the area that drains through it, so big valleys cut deep and the tops are barely
 * touched), with rock slides after every step so the valley walls fall back to the angle rock can
 * stand. Where two valleys' walls meet, a sharp ridge is left. The ridges and valleys come from
 * the water, not from lines placed by hand.
 * <p>
 * JOINED: neighbouring peaks' lumps overlap, so they share a base and meet at saddles.
 * SEPARATE: each lump stops half-way to its nearest neighbour, so every peak stands alone.
 */
final class Massif {
    // lump
    static double JOIN_REACH = 1.00;     // joined: a lump's radius, in cells, for a full-height peak
    static double SEP_REACH = 0.48;      // separate: a lump's radius, as a share of the distance to the nearest peak
    static double PROFILE = 1.35;        // how concave a lump's sides are (1 straight cone)
    static double SMOOTH_MAX = 4;        // how lumps combine where they overlap (higher: closer to the taller one)
    static double SHAPE_WARP = 0.30;     // how far a lump's outline wanders, as a share of its radius
    static double ROUGH = 0.28;          // noise on the lump, as a share of the peak's height
    static double ROUGH_LENGTH = 110;
    // water
    static double K = 0.02;            // how hard running water cuts
    static double M = 0.5;               // exponent of drained area
    static int STEPS = 120;
    static double UPLIFT = 0;          // above 0: the range rises while it is cut (total rise, as a share of the lump)
    static boolean RESCALE = true;
    static double ICE = 0, ICE_FROM = 0.5, ICE_BAND = 0.2;   // ice erosion: strength, and from what share of the summit height it starts
    static double FLOW_POWER = 4;      // how strongly water prefers the steepest way down (high: one channel)
    // rock
    static double TALUS = 1.30;          // steepest rock can stand (52 degrees)
    static double TALUS_LOW = 0.75;      // steepest the lower ground can stand
    static int THERMAL_PASSES = 4;
    static double S = 4;                 // blocks between samples
    static double FINE = 1.5, FINE_LENGTH = 7;

    final int n;
    final double ox, oz;
    final float[] h, base;
    final boolean[] outlet;
    final List<ErodedPeaks.Peak> peaks = new ArrayList<>();

    Massif(HornsFunction horns, double x0, double z0, int size, boolean joined) {
        long seed = AtlasMountainsMod.seed();
        double margin = 320;
        ox = x0 - margin; oz = z0 - margin;
        n = (int) Math.ceil((size + 2 * margin) / S) + 1;
        h = new float[n * n]; base = new float[n * n]; outlet = new boolean[n * n];
        double cell = horns.cell();
        long c0x = (long) Math.floor(ox / cell) - 2, c0z = (long) Math.floor(oz / cell) - 2;
        long c1x = (long) Math.floor((ox + n * S) / cell) + 2, c1z = (long) Math.floor((oz + n * S) / cell) + 2;
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
            double x = ox + gx * S, z = oz + gz * S;
            double wx = horns.warpX(x, z, seed), wz = horns.warpZ(x, z, seed);
            double sum = 0, top = 0;
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
                double m = p.h() * Math.pow(1 - t, PROFILE);
                if (m > top) top = m;
                sum += Math.pow(m, SMOOTH_MAX);
            }
            double v = joined ? Math.pow(sum, 1 / SMOOTH_MAX) : top;
            if (v > 0.5) {
                double share = Math.min(1, v / 120);
                double noise = 0;
                double amp = 1, freq = 1 / ROUGH_LENGTH, norm = 0;
                for (int o = 0; o < 4; o++) { noise += amp * HornsFunction.valueNoise(x * freq, z * freq, seed + 610 + 13L * o); norm += amp; amp *= 0.5; freq *= 2.1; }
                v = Math.max(0, v + ROUGH * 120 * share * noise / norm * (1 - Math.pow(v / Math.max(v, top + 1e-9), 8) * 0.0));
            }
            int i = gz * n + gx;
            h[i] = (float) v; base[i] = (float) v;
            outlet[i] = v <= 0.5 || gx == 0 || gz == 0 || gx == n - 1 || gz == n - 1;
        }
        if (UPLIFT > 0) {
            // The range rises slowly (fastest under the summits) while the water cuts it, as real
            // ranges are made. Start from a low rough plain.
            for (int i = 0; i < h.length; i++) h[i] = outlet[i] ? 0 : (float) (0.03 * base[i]);
            for (int s = 0; s < STEPS; s++) {
                for (int i = 0; i < h.length; i++) if (!outlet[i]) h[i] += (float) (UPLIFT * base[i] / STEPS);
                if (ICE > 0 && s % 10 == 0) ref = summits(cell);
                water(); thermal();
            }
        } else {
            for (ErodedPeaks.Peak p : peaks) pin(p);
            for (int s = 0; s < STEPS; s++) { water(); thermal(); }
        }
        if (RESCALE) rescale(cell);
        for (int t = 0; t < 2; t++) thermal();
    }

    /** Scales each mountain so its top is its set height again (ratios blended smoothly between peaks). */
    private void rescale(double cell) {
        int np = peaks.size();
        double[] ratio = new double[np];
        int reach = (int) Math.ceil(30 / S);
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            int gx = (int) Math.round((p.x() - ox) / S), gz = (int) Math.round((p.z() - oz) / S);
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
            double x = ox + gx * S, z = oz + gz * S, sw = 1e-4, sr = 1e-4;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double dx = x - p.x(), dz = z - p.z();
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; sr += w * ratio[a];
            }
            h[i] = (float) (h[i] * sr / sw);
            base[i] = Math.max(base[i], h[i]);
        }
    }

    private void pin(ErodedPeaks.Peak p) {
        int gx = (int) Math.round((p.x() - ox) / S), gz = (int) Math.round((p.z() - oz) / S);
        if (gx < 2 || gz < 2 || gx > n - 3 || gz > n - 3) return;
        // scale the 5x5 round the summit so its top is the set height
        float top = 0;
        for (int dz = -2; dz <= 2; dz++) for (int dx = -2; dx <= 2; dx++) top = Math.max(top, h[(gz + dz) * n + gx + dx]);
        h[gz * n + gx] = (float) Math.max(top, p.h());
    }

    private float[] ref;   // the height of the summits round each cell, for the ice

    private float[] summits(double cell) {
        int np = peaks.size();
        double[] top = new double[np];
        int reach = (int) Math.ceil(30 / S);
        for (int a = 0; a < np; a++) {
            ErodedPeaks.Peak p = peaks.get(a);
            int gx = (int) Math.round((p.x() - ox) / S), gz = (int) Math.round((p.z() - oz) / S);
            for (int dz = -reach; dz <= reach; dz++) for (int dx = -reach; dx <= reach; dx++) {
                int xx = gx + dx, zz = gz + dz;
                if (xx >= 0 && zz >= 0 && xx < n && zz < n) top[a] = Math.max(top[a], h[zz * n + xx]);
            }
        }
        float[] r = new float[n * n];
        double sig = 0.4 * cell;
        for (int gz = 0; gz < n; gz++) for (int gx = 0; gx < n; gx++) {
            double x = ox + gx * S, z = oz + gz * S, sw = 1e-9, st = 0;
            for (int a = 0; a < np; a++) {
                ErodedPeaks.Peak p = peaks.get(a);
                double dx = x - p.x(), dz = z - p.z();
                double w = Math.exp(-(dx * dx + dz * dz) / (2 * sig * sig));
                sw += w; st += w * top[a];
            }
            r[gz * n + gx] = (float) (st / sw);
        }
        return r;
    }

    private static final int[] DX = {1, 0, -1, 0, 1, -1, -1, 1}, DZ = {0, 1, 0, -1, 1, 1, -1, -1};

    /** One step of running water: priority flood from the outlets gives every cell its receiver and an order to work in. */
    private void water() {
        int N = n * n;
        int[] rec = new int[N], order = new int[N];
        boolean[] seen = new boolean[N];
        float[] fill = new float[N];
        PriorityQueue<long[]> q = new PriorityQueue<>((a, b) -> Float.compare(Float.intBitsToFloat((int) a[0]), Float.intBitsToFloat((int) b[0])));
        for (int i = 0; i < N; i++) if (outlet[i]) { seen[i] = true; rec[i] = i; fill[i] = h[i]; q.add(new long[]{Float.floatToIntBits(h[i]), i}); }
        int k = 0;
        while (!q.isEmpty()) {
            long[] e = q.poll();
            int i = (int) e[1];
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
        // Water spreads over every lower neighbour by slope (so valleys are not held to eight
        // directions); a cell is cut towards its steepest lower neighbour.
        double[] area = new double[N];
        double[] wts = new double[8];
        for (int a = k - 1; a >= 0; a--) {
            int i = order[a];
            area[i] += S * S;
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
            double dist = S * Math.sqrt(dx * dx + dz * dz);
            double f = K * Math.pow(area[i], M) / dist;
            if (ICE > 0 && ref != null) {
                // ice: high up, hollows are scooped hard whatever their catchment (cirques)
                double rel = h[i] / Math.max(1, ref[i]);
                double t = Math.max(0, Math.min(1, (rel - ICE_FROM) / ICE_BAND));
                f += ICE * Math.pow(area[i], 0.25) * t * t * (3 - 2 * t) / dist;
            }
            double hr = Math.max(h[r], fill[r]);
            double nh = (h[i] + f * hr) / (1 + f);
            if (nh < h[i]) h[i] = (float) Math.max(0, nh);
        }
    }

    private void thermal() {
        for (int p = 0; p < THERMAL_PASSES; p++) {
            for (int z = 1; z < n - 1; z++) for (int x = 1; x < n - 1; x++) {
                int i = z * n + x;
                double hi = h[i];
                if (hi <= 0.5) continue;
                double crit = hi > 0.35 * 120 ? TALUS : TALUS_LOW + (TALUS - TALUS_LOW) * hi / (0.35 * 120);
                for (int d = 0; d < 8; d++) {
                    int j = (z + DZ[d]) * n + x + DX[d];
                    double drop = hi - h[j], max = crit * S * (d < 4 ? 1 : Math.sqrt(2));
                    if (drop > max) { double m = (drop - max) * 0.25; hi -= m; h[j] += m; }
                }
                h[i] = (float) hi;
            }
        }
    }

    /** Height above the ground at a block, cubic between samples, with the same fine noise as ErodedPeaks. */
    double relief(double x, double z) {
        double gx = (x - ox) / S, gz = (z - oz) / S;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        if (ix < 1 || iz < 1 || ix > n - 3 || iz > n - 3) return 0;
        double tx = gx - ix, tz = gz - iz;
        double[] row = new double[4];
        for (int j = -1; j <= 2; j++) {
            int r = (iz + j) * n + ix;
            row[j + 1] = cubic(h[r - 1], h[r], h[r + 1], h[r + 2], tx);
        }
        double v = cubic(row[0], row[1], row[2], row[3], tz);
        if (v <= 0) return 0;
        long seed = AtlasMountainsMod.seed();
        return Math.max(0, v + FINE * 0.5 * HornsFunction.valueNoise(x / FINE_LENGTH, z / FINE_LENGTH, seed + 909) * Math.min(1, v / 8));
    }

    /** How far the water cut here (blocks), for the snow's gully rule. */
    double carve(double x, double z) {
        int ix = (int) Math.round((x - ox) / S), iz = (int) Math.round((z - oz) / S);
        if (ix < 0 || iz < 0 || ix >= n || iz >= n) return 0;
        return base[iz * n + ix] - h[iz * n + ix];
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }
}
