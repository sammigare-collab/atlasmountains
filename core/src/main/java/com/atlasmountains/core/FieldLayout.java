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
        double px = x + warp(1, x, z), pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        long cx = (long) Math.floor(px / c), cz = (long) Math.floor(pz / c);
        // three nearest sites
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE, d3 = Double.MAX_VALUE;
        double x1 = 0, z1 = 0, x2 = 0, z2 = 0, x3 = 0, z3 = 0;
        long id1 = 0, id2 = 0, id3 = 0;
        for (long i = cx - 1; i <= cx + 1; i++) {
            for (long j = cz - 1; j <= cz + 1; j++) {
                long h = Hash.hash(siteSeed, i, j);
                double sx = (i + 0.5 + (Hash.unit(Hash.hash(h, 1)) - 0.5) * s.fieldJitter) * c;
                double sz = (j + 0.5 + (Hash.unit(Hash.hash(h, 2)) - 0.5) * s.fieldJitter) * c;
                double d = (px - sx) * (px - sx) + (pz - sz) * (pz - sz);
                if (d < d1) { d3 = d2; x3 = x2; z3 = z2; id3 = id2; d2 = d1; x2 = x1; z2 = z1; id2 = id1; d1 = d; x1 = sx; z1 = sz; id1 = h; }
                else if (d < d2) { d3 = d2; x3 = x2; z3 = z2; id3 = id2; d2 = d; x2 = sx; z2 = sz; id2 = h; }
                else if (d < d3) { d3 = d; x3 = sx; z3 = sz; id3 = h; }
            }
        }
        edge(px, pz, d1, x1, z1, id1, d2, x2, z2, id2, out, false);
        edge(px, pz, d1, x1, z1, id1, d3, x3, z3, id3, out, true);
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
