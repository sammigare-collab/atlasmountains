package com.atlasmountains.core;

/**
 * Spines everywhere, as the edges of a warped Voronoi diagram: a connected network of ridges
 * meeting at three-way junctions, enclosing cells. Where the mountain mask is on (vanilla's
 * mountain footprint) every column is on one of these ridges or on the flank below it, so the
 * footprint fills with ranges back to back; a cell wider than two flanks leaves a small
 * enclosed valley floor (with its lake) walled in by ridges. The distance to the nearest ridge
 * is the distance to the bisector of the two nearest sites, which is exact.
 * <p>
 * Each edge is its own range (seeded by its two sites), so the crest profile strings summits
 * along it; the second-nearest edge is reported too, so the generator unions the two ridges
 * at a junction. Summit heights are scaled by a slow 2-D field
 * ({@link MountainSettings#fieldHeightBoost} on average, plus or minus
 * {@link MountainSettings#fieldHeightVariation}), so some massifs are big and some small.
 */
public final class FieldLayout implements SpineLayout {

    private final MountainSettings s;
    private final long siteSeed, warpSeed, heightSeed;
    private volatile java.util.function.DoubleBinaryOperator erosion;
    private final java.util.Map<Long, Boolean> siteInside = new java.util.concurrent.ConcurrentHashMap<>();

    public FieldLayout(MountainSettings settings, long seed) {
        this.s = settings;
        this.siteSeed = Hash.hash(seed, 9001);
        this.warpSeed = Hash.hash(seed, 9002);
        this.heightSeed = Hash.hash(seed, 9003);
    }

    /** Summit relief scale at (x, z): boost times (1 +/- variation), smooth over fieldHeightWavelength. */
    public double heightScale(double x, double z) {
        double n = Hash.value2(heightSeed, x / s.fieldHeightWavelength, z / s.fieldHeightWavelength) * 2 - 1;
        return s.fieldHeightBoost * (1 + s.fieldHeightVariation * n);
    }

    private double warp(long salt, double x, double z) {
        double w = s.fieldWarpWavelength;
        return (Hash.value2(Hash.hash(warpSeed, salt), x / w, z / w) - 0.5) * 2 * s.fieldWarpAmplitude
             + (Hash.value2(Hash.hash(warpSeed, salt + 10), x / w * 2.3, z / w * 2.3) - 0.5) * s.fieldWarpAmplitude * 0.5;
    }

    @Override
    public void setErosion(java.util.function.DoubleBinaryOperator erosion) { this.erosion = erosion; siteInside.clear(); }

    @Override
    public boolean zoneReplacesFootprint() { return true; }

    /** Whether the cell of this site is in the mountains: vanilla's footprint is on at the site. */
    private boolean inside(long id, double sx, double sz) {
        Boolean b = siteInside.get(id);
        if (b == null) {
            b = erosion.applyAsDouble(sx, sz) < s.erosionMountainBelow;
            if (siteInside.size() > 200_000) siteInside.clear();
            siteInside.put(id, b);
        }
        return b;
    }

    /**
     * The mask: 1 across every cell whose site is in vanilla's footprint, and one flank's width
     * beyond the ridge that bounds such a cell, then a fade of fieldEdgeFade blocks. NaN until the
     * erosion field is installed. Uses the same ridges as sample(), so the mask and the terrain
     * move together.
     */
    @Override
    public double zone(double x, double z) {
        if (erosion == null) return Double.NaN;
        Sites n = nearest(x, z);
        if (inside(n.ids[n.i1], n.xs[n.i1], n.zs[n.i1])) return 1;
        double d = Double.MAX_VALUE;
        for (int k = 0; k < Sites.N; k++) {
            if (k == n.i1 || !inside(n.ids[k], n.xs[k], n.zs[k])) continue;
            d = Math.min(d, bisectorDistance(n, n.i1, k));
        }
        double t = (s.halfWidth() + s.fieldEdgeFade - d) / s.fieldEdgeFade;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Distance from the sample point to the bisector of sites a and b (a is the nearest site). */
    private static double bisectorDistance(Sites n, int a, int b) {
        double len = Math.max(1e-9, Math.hypot(n.xs[b] - n.xs[a], n.zs[b] - n.zs[a]));
        return (n.ds[b] - n.ds[a]) / (2 * len);
    }

    /** The warped sample point and the 5x5 neighbourhood of sites (squared distances), with the nearest marked. */
    private static final class Sites {
        static final int N = 25;
        double px, pz;
        final double[] xs = new double[N], zs = new double[N], ds = new double[N];
        final long[] ids = new long[N];
        int i1;
    }

    private Sites nearest(double x, double z) {
        Sites n = new Sites();
        n.px = x + warp(1, x, z); n.pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        long cx = (long) Math.floor(n.px / c), cz = (long) Math.floor(n.pz / c);
        int k = 0;
        double best = Double.MAX_VALUE;
        for (long i = cx - 2; i <= cx + 2; i++) {
            for (long j = cz - 2; j <= cz + 2; j++, k++) {
                long h = Hash.hash(siteSeed, i, j);
                double sx = (i + 0.5 + (Hash.unit(Hash.hash(h, 1)) - 0.5) * s.fieldJitter) * c;
                double sz = (j + 0.5 + (Hash.unit(Hash.hash(h, 2)) - 0.5) * s.fieldJitter) * c;
                double d = (n.px - sx) * (n.px - sx) + (n.pz - sz) * (n.pz - sz);
                n.xs[k] = sx; n.zs[k] = sz; n.ds[k] = d; n.ids[k] = h;
                if (d < best) { best = d; n.i1 = k; }
            }
        }
        return n;
    }

    @Override
    public void sample(double x, double z, RangeLayout.Sample out) {
        Sites n = nearest(x, z);
        int a = n.i1;
        boolean gate = erosion != null;
        boolean aIn = gate && inside(n.ids[a], n.xs[a], n.zs[a]);
        // the two nearest ridges of the sample's cell; with the footprint installed only ridges
        // that bound a mountain cell count, so nothing stands between two non-mountain cells
        int b1 = -1, b2 = -1;
        double e1 = Double.MAX_VALUE, e2 = Double.MAX_VALUE;
        for (int k = 0; k < Sites.N; k++) {
            if (k == a) continue;
            if (gate && !aIn && !inside(n.ids[k], n.xs[k], n.zs[k])) continue;
            double d = bisectorDistance(n, a, k);
            if (d < e1) { e2 = e1; b2 = b1; e1 = d; b1 = k; }
            else if (d < e2) { e2 = d; b2 = k; }
        }
        double far = 3 * s.halfWidth();
        if (b1 < 0) { out.distance = far; out.along = 0; out.range = 0; }
        else edge(n.px, n.pz, n.ds[a], n.xs[a], n.zs[a], n.ids[a], n.ds[b1], n.xs[b1], n.zs[b1], n.ids[b1], out, false);
        if (b2 < 0) { out.distance2 = far; out.along2 = 0; out.range2 = 1; }
        else edge(n.px, n.pz, n.ds[a], n.xs[a], n.zs[a], n.ids[a], n.ds[b2], n.xs[b2], n.zs[b2], n.ids[b2], out, true);
        out.scale = heightScale(x, z);
    }

    /** Distance to, and position along, the bisector of sites a and b; written as the first or second spine of out. */
    private static void edge(double px, double pz, double da, double ax, double az, long ida, double db, double bx, double bz, long idb,
                             RangeLayout.Sample out, boolean second) {
        double ex = bx - ax, ez = bz - az;
        double len = Math.max(1e-9, Math.sqrt(ex * ex + ez * ez));
        double dist = (db - da) / (2 * len);                      // db, da are squared distances
        // along the edge: project onto the edge's direction (perpendicular to a->b), oriented by site order so it is stable
        boolean flip = ida > idb;
        double tx = -ez / len, tz = ex / len;
        if (flip) { tx = -tx; tz = -tz; }
        double mx = (ax + bx) * 0.5, mz = (az + bz) * 0.5;
        double along = (px - mx) * tx + (pz - mz) * tz;
        int range = (int) Hash.hash(Math.min(ida, idb), Math.max(ida, idb));
        if (second) { out.distance2 = dist; out.along2 = along; out.range2 = range; }
        else { out.distance = dist; out.along = along; out.range = range; }
    }
}
