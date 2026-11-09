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
     * erosion field is installed.
     */
    @Override
    public double zone(double x, double z) {
        if (erosion == null) return Double.NaN;
        Sites n = nearest(x, z);
        if (inside(n.id1, n.x1, n.z1)) return 1;
        double best = 0;
        if (inside(n.id2, n.x2, n.z2)) best = Math.max(best, edgeMask(n.px, n.pz, n.d1, n.x1, n.z1, n.d2, n.x2, n.z2));
        if (inside(n.id3, n.x3, n.z3)) best = Math.max(best, edgeMask(n.px, n.pz, n.d1, n.x1, n.z1, n.d3, n.x3, n.z3));
        return best;
    }

    private double edgeMask(double px, double pz, double da, double ax, double az, double db, double bx, double bz) {
        double len = Math.max(1e-9, Math.hypot(bx - ax, bz - az));
        double dist = (db - da) / (2 * len);           // distance to the ridge between the two cells
        double t = (s.halfWidth() + s.fieldEdgeFade - dist) / s.fieldEdgeFade;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** The warped sample point and its three nearest sites (squared distances). */
    private static final class Sites {
        double px, pz, d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE, d3 = Double.MAX_VALUE;
        double x1, z1, x2, z2, x3, z3;
        long id1, id2, id3;
    }

    private Sites nearest(double x, double z) {
        Sites n = new Sites();
        n.px = x + warp(1, x, z); n.pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        long cx = (long) Math.floor(n.px / c), cz = (long) Math.floor(n.pz / c);
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                long h = Hash.hash(siteSeed, i, j);
                double sx = (i + 0.5 + (Hash.unit(Hash.hash(h, 1)) - 0.5) * s.fieldJitter) * c;
                double sz = (j + 0.5 + (Hash.unit(Hash.hash(h, 2)) - 0.5) * s.fieldJitter) * c;
                double d = (n.px - sx) * (n.px - sx) + (n.pz - sz) * (n.pz - sz);
                if (d < n.d1) { n.d3 = n.d2; n.x3 = n.x2; n.z3 = n.z2; n.id3 = n.id2; n.d2 = n.d1; n.x2 = n.x1; n.z2 = n.z1; n.id2 = n.id1; n.d1 = d; n.x1 = sx; n.z1 = sz; n.id1 = h; }
                else if (d < n.d2) { n.d3 = n.d2; n.x3 = n.x2; n.z3 = n.z2; n.id3 = n.id2; n.d2 = d; n.x2 = sx; n.z2 = sz; n.id2 = h; }
                else if (d < n.d3) { n.d3 = d; n.x3 = sx; n.z3 = sz; n.id3 = h; }
            }
        }
        return n;
    }

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
    public void sample(double x, double z, RangeLayout.Sample out) {
        Sites n = nearest(x, z);
        edge(n.px, n.pz, n.d1, n.x1, n.z1, n.id1, n.d2, n.x2, n.z2, n.id2, out, false);
        edge(n.px, n.pz, n.d1, n.x1, n.z1, n.id1, n.d3, n.x3, n.z3, n.id3, out, true);
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
