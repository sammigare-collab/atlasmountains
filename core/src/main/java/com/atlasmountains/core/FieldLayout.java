package com.atlasmountains.core;

/**
 * Where the ridges are: the layout used by the mixed preset (the one players use).
 *
 * <h2>The idea</h2>
 * Ridges everywhere, as the edges of a warped Voronoi diagram: a connected network of ridges
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
 *
 * <h2>Steps for one position</h2>
 * <ol>
 *   <li><b>Warp.</b> The position is shifted by a smooth 2-octave noise ({@link #warp}), so the
 *       straight Voronoi edges come out curved. Everything below uses the warped point.</li>
 *   <li><b>Sites.</b> The plane is divided into squares of {@link MountainSettings#fieldCellSize};
 *       each square holds one site at a hashed, jittered position. The 5x5 squares around the
 *       warped point are examined ({@link #nearest}). Three-by-three is not enough: with a large
 *       jitter the second-nearest site can lie two squares away, and when it fell outside the
 *       searched set the ridge jumped, leaving a 64-block wall in the terrain.</li>
 *   <li><b>Ridges.</b> The ridge between the nearest site A and another site B is their
 *       perpendicular bisector. The distance from the point to it is
 *       {@code (|P-B|^2 - |P-A|^2) / (2 |B-A|)}, exact for a Voronoi edge, so no approximation
 *       error builds up near junctions.</li>
 *   <li><b>Footprint.</b> Once the world's erosion noise is installed ({@link #setErosion}), each
 *       site's cell is "inside" the mountains or not, and only ridges bounding an inside cell
 *       are used. {@link #zone} turns the same test into the mask.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * Called from many chunk-generation threads. The only shared state is the per-site inside cache,
 * a concurrent map; everything else is computed from hashes of the seed and the position, so
 * every thread gets identical answers.
 */
public final class FieldLayout {

    private final MountainSettings s;
    private final long siteSeed, warpSeed, heightSeed;
    /** (x, z) -> vanilla-scale erosion; null until the world's noise is installed (see setErosion). */
    private volatile java.util.function.DoubleBinaryOperator erosion;
    /**
     * Inside-the-footprint answer per site id. Each answer costs a noise evaluation and every block
     * asks about 25 sites, so caching is what makes the footprint affordable. Cleared when it
     * grows past 200 000 entries (a few MB) and whenever the erosion field changes.
     */
    private final java.util.Map<Long, Boolean> siteInside = new java.util.concurrent.ConcurrentHashMap<>();

    /** The seed is split into independent sub-seeds for site positions, the warp and the height field. */
    public FieldLayout(MountainSettings settings, long seed) {
        this.s = settings;
        this.siteSeed = Hash.hash(seed, 9001);
        this.warpSeed = Hash.hash(seed, 9002);
        this.heightSeed = Hash.hash(seed, 9003);
    }

    /**
     * Summit relief scale at (x, z): fieldHeightBoost times (1 + fieldHeightVariation * n), with n
     * a smooth noise in -1..1 over fieldHeightWavelength blocks. Value noise clusters near 0, so
     * most of the map sits close to the boost and the extremes are rare.
     */
    public double heightScale(double x, double z) {
        double n = Hash.value2(heightSeed, x / s.fieldHeightWavelength, z / s.fieldHeightWavelength);
        return s.fieldHeightBoost * (1 + s.fieldHeightVariation * n);
    }

    /** Domain-warp offset in blocks for one axis (salt 1 = x, 2 = z): two octaves plus a constant shift. */
    private double warp(long salt, double x, double z) {
        double w = s.fieldWarpWavelength;
        return Hash.value2(Hash.hash(warpSeed, salt), x / w, z / w) * s.fieldWarpAmplitude
             + Hash.value2(Hash.hash(warpSeed, salt + 10), x / w * 2.3, z / w * 2.3) * s.fieldWarpAmplitude * 0.25
             + s.fieldWarpShift;
    }

    /**
     * Gives the layout the world's erosion field, (x, z) -> erosion on vanilla's scale. Until it
     * is set there is no footprint: {@link #zone} is NaN and ridges run everywhere (the
     * "everywhere" preset and the standalone tools).
     */
    public void setErosion(java.util.function.DoubleBinaryOperator erosion) { this.erosion = erosion; siteInside.clear(); }

    /**
     * Whether the cell of this site is in the mountains: vanilla's footprint is on at the site.
     * The test is {@code erosion(site) + footprintRagged * n(site) < erosionMountainBelow}, with n
     * a slow noise that roughens the province outline. The answer is a property of the site, not
     * of the block, so a whole cell is in or out together.
     */
    private boolean inside(long id, double sx, double sz) {
        Boolean b = siteInside.get(id);
        if (b == null) {
            double rag = Hash.value2(Hash.hash(siteSeed, 77), sx / s.footprintRaggedWavelength, sz / s.footprintRaggedWavelength) * s.footprintRagged;
            b = erosion.applyAsDouble(sx, sz) + rag < s.erosionMountainBelow;
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
    public double zone(double x, double z) {
        if (erosion == null) return Double.NaN;
        Sites n = nearest(x, z);
        if (inside(n.ids[n.i1], n.xs[n.i1], n.zs[n.i1])) return 1;
        double d = outsideEdges(n);
        // d is the distance to the nearest ridge that bounds an inside cell (any such ridge nearby,
        // not only the edges of the sample's own cell, so it does not jump where cells meet). The mask is 1 within
        // one flank's width of it (halfWidth), then falls to 0 over fieldEdgeFade blocks with a
        // smoothstep, so there is no kink where the fade starts or ends.
        double t = (s.halfWidth() + s.fieldEdgeFade - d) / s.fieldEdgeFade;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /**
     * Distance from the sample point to the bisector of sites a and b (a is the nearest site).
     * ds holds squared distances, so this is (db^2 - da^2) / (2 |ab|): positive, and exact.
     */
    /**
     * Distance from the sample point to the real Voronoi edge between cells a and b: the part of
     * their bisector that is closer to a and b than to any other of the 25 sites. That piece is
     * finite (it ends at the two corners where a third cell meets it), so past its ends the
     * distance is to the nearer corner. Returns Double.MAX_VALUE if a and b do not touch at all.
     * <p>
     * {@link #bisectorDistance} measures to the whole infinite line instead. For a point inside
     * cell a that is right for a's own nearest edge, but the line of a pair that does not touch,
     * or of a real edge beyond its corner, runs on through other cells. Outside the footprint
     * that showed as straight strips of mountain biome and flank leading out of a province, and
     * as walls where the flank met the next cell.
     * <p>
     * The edge is the line M + t * dir (M the midpoint of a and b, dir along the bisector). For
     * each other site j, "closer to a than to j" is a linear condition on t, so the valid t form
     * one interval; coordinates are taken relative to the sample point to keep the arithmetic
     * small.
     */
    private static double edgeDistance(Sites n, int a, int b) {
        double ax = n.xs[a] - n.px, az = n.zs[a] - n.pz, bx = n.xs[b] - n.px, bz = n.zs[b] - n.pz;
        double ex = bx - ax, ez = bz - az, len = Math.hypot(ex, ez);
        if (len < 1e-9) return Double.MAX_VALUE;
        double dx = -ez / len, dz = ex / len;                 // along the bisector
        double mx = (ax + bx) * 0.5, mz = (az + bz) * 0.5;    // midpoint, relative to the sample point
        double lo = Double.NEGATIVE_INFINITY, hi = Double.POSITIVE_INFINITY;
        for (int j = 0; j < Sites.N; j++) {
            if (j == a || j == b) continue;
            double jx = n.xs[j] - n.px, jz = n.zs[j] - n.pz;
            double gx = jx - ax, gz = jz - az;                // X closer to a than j: 2 X.(J-A) <= |J|^2 - |A|^2
            double c = 2 * (dx * gx + dz * gz);
            double r = (jx * jx + jz * jz) - (ax * ax + az * az) - 2 * (mx * gx + mz * gz);
            if (Math.abs(c) < 1e-12) { if (r < 0) return Double.MAX_VALUE; continue; }
            double t = r / c;
            if (c > 0) hi = Math.min(hi, t); else lo = Math.max(lo, t);
            if (lo > hi) return Double.MAX_VALUE;
        }
        double t0 = -(mx * dx + mz * dz);                     // the sample point (origin) projected onto the line
        double perp = Math.abs(mx * (ex / len) + mz * (ez / len));
        double tc = Math.max(lo, Math.min(hi, t0));
        return Math.hypot(perp, t0 - tc);
    }

    private static double bisectorDistance(Sites n, int a, int b) {
        double len = Math.max(1e-9, Math.hypot(n.xs[b] - n.xs[a], n.zs[b] - n.zs[a]));
        return (n.ds[b] - n.ds[a]) / (2 * len);
    }

    /**
     * The warped sample point and the 5x5 neighbourhood of sites (squared distances), with the
     * nearest marked. Arrays are indexed by the order squares were visited; ids are the site hashes,
     * which identify a site globally (for the inside cache and to seed each ridge's crest).
     */
    private static final class Sites {
        static final int N = 25;
        double px, pz;
        final double[] xs = new double[N], zs = new double[N], ds = new double[N];
        final long[] ids = new long[N];
        int i1;
    }

    /** Warps (x, z) and collects the 25 sites around it; see the class comment, step 2. */
    private Sites nearest(double x, double z) {
        Sites n = new Sites();
        n.px = x + warp(1, x, z); n.pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        long cx = (long) Math.floor(n.px / c), cz = (long) Math.floor(n.pz / c);
        int k = 0;
        double best = Double.MAX_VALUE;
        for (long i = cx - 2; i <= cx + 2; i++) {
            for (long j = cz - 2; j <= cz + 2; j++, k++) {
                long h = Hash.hash(siteSeed, i, j);   // the site's identity: same square, same site, on every thread
                // site = square centre + jitter, the jitter uniform in +/- fieldJitter/2 of a cell
                double sx = (i + 0.5 + (Hash.unit(Hash.hash(h, 1)) - 0.5) * s.fieldJitter) * c;
                double sz = (j + 0.5 + (Hash.unit(Hash.hash(h, 2)) - 0.5) * s.fieldJitter) * c;
                double d = (n.px - sx) * (n.px - sx) + (n.pz - sz) * (n.pz - sz);
                n.xs[k] = sx; n.zs[k] = sz; n.ds[k] = d; n.ids[k] = h;
                if (d < best) { best = d; n.i1 = k; }
            }
        }
        return n;
    }

    /**
     * The nearest two ridges to (x, z) and the height scale there, written into out. Two ridges,
     * because near a junction the terrain is the union of both (the higher of the two profiles),
     * which gives clean three-way meetings instead of a crease where the nearest ridge switches.
     * With no qualifying ridge (deep outside the footprint) the distance is set to three
     * half-widths, which the generator turns into flat valley floor.
     */
    public void sample(double x, double z, RidgeSample out) {
        Sites n = nearest(x, z);
        int a = n.i1;
        boolean gate = erosion != null;
        boolean aIn = gate && inside(n.ids[a], n.xs[a], n.zs[a]);
        // the two nearest ridges of the sample's cell; with the footprint installed only ridges
        // that bound a mountain cell count, so nothing stands between two non-mountain cells
        if (gate && !aIn) {
            // Outside the footprint: the two nearest real edges between any outside cell and an
            // inside cell, not only the edges of the sample's own cell. Measuring only from the
            // sample's own cell made the distance jump where that cell met an outside neighbour
            // that touches no inside cell (or a different one): the flank and the mask stopped
            // dead along that line, which showed as a straight wall (Nov 2026).
            int[] pair = new int[4];
            double[] dd = new double[3];
            outsideEdgesInto(n, pair, dd);
            out.distance3 = dd[2];
            double far = 3 * s.halfWidth();
            if (pair[0] < 0) { out.distance = far; out.along = 0; out.ridge = 0; }
            else {
                int j = pair[0], k = pair[1];
                edge(n.px, n.pz, n.ds[j], n.xs[j], n.zs[j], n.ids[j], n.ds[k], n.xs[k], n.zs[k], n.ids[k], out, false);
                out.distance = dd[0];
            }
            if (pair[2] < 0) { out.distance2 = far; out.along2 = 0; out.ridge2 = 1; }
            else {
                int j = pair[2], k = pair[3];
                edge(n.px, n.pz, n.ds[j], n.xs[j], n.zs[j], n.ids[j], n.ds[k], n.xs[k], n.zs[k], n.ids[k], out, true);
                out.distance2 = dd[1];
            }
            out.scale = heightScale(x, z);
            return;
        }
        // inside a mountain cell (or no footprint installed): the two nearest ridges of the cell
        int b1 = -1, b2 = -1;
        double e1 = Double.MAX_VALUE, e2 = Double.MAX_VALUE, e3 = Double.MAX_VALUE;
        for (int k = 0; k < Sites.N; k++) {
            if (k == a) continue;
            double d = bisectorDistance(n, a, k);
            if (d < e1) { e3 = e2; e2 = e1; b2 = b1; e1 = d; b1 = k; }
            else if (d < e2) { e3 = e2; e2 = d; b2 = k; }
            else if (d < e3) e3 = d;
        }
        out.distance3 = e3;
        double far = 3 * s.halfWidth();
        if (b1 < 0) { out.distance = far; out.along = 0; out.ridge = 0; }
        else edge(n.px, n.pz, n.ds[a], n.xs[a], n.zs[a], n.ids[a], n.ds[b1], n.xs[b1], n.zs[b1], n.ids[b1], out, false);
        if (b2 < 0) { out.distance2 = far; out.along2 = 0; out.ridge2 = 1; }
        else edge(n.px, n.pz, n.ds[a], n.xs[a], n.zs[a], n.ids[a], n.ds[b2], n.xs[b2], n.zs[b2], n.ids[b2], out, true);
        out.scale = heightScale(x, z);
    }

    /**
     * Distance to, and position along, the bisector of sites a and b; written as the first or second
     * ridge of out. "Along" is the coordinate along the ridge, measured from the midpoint of the two
     * sites, which the crest profile uses to place summits. Its direction is fixed by the order of
     * the two site ids, so both cells beside a ridge see the same summits in the same places. The
     * ridge id hashes the two site ids in sorted order for the same reason.
     */
    private static void edge(double px, double pz, double da, double ax, double az, long ida, double db, double bx, double bz, long idb,
                             RidgeSample out, boolean second) {
        double ex = bx - ax, ez = bz - az;
        double len = Math.max(1e-9, Math.sqrt(ex * ex + ez * ez));
        double dist = (db - da) / (2 * len);                      // db, da are squared distances
        // along the edge: project onto the edge's direction (perpendicular to a->b), oriented by site order so it is stable
        boolean flip = ida > idb;
        double tx = -ez / len, tz = ex / len;
        if (flip) { tx = -tx; tz = -tz; }
        double mx = (ax + bx) * 0.5, mz = (az + bz) * 0.5;
        double along = (px - mx) * tx + (pz - mz) * tz;
        int ridge = (int) Hash.hash(Math.min(ida, idb), Math.max(ida, idb));
        if (second) { out.distance2 = dist; out.along2 = along; out.ridge2 = ridge; }
        else { out.distance = dist; out.along = along; out.ridge = ridge; }
    }

    /** Distance to the nearest real edge between an outside cell and an inside cell (MAX_VALUE if none is near). */
    private double outsideEdges(Sites n) {
        int[] pair = new int[4];
        double[] dd = new double[3];
        outsideEdgesInto(n, pair, dd);
        return pair[0] < 0 ? Double.MAX_VALUE : dd[0];
    }

    /**
     * The two nearest real Voronoi edges that separate an outside cell j from an inside cell k,
     * measured from the sample point: pair = {j1, k1, j2, k2} (-1 if none), dd = their distances
     * and the third-nearest edge's.
     * Only edges within one flank plus the fade (reach) matter, and a point X on edge (j, k) is
     * closer to j and k than to the sample's own site, so |P - j| and |P - k| are at most
     * |P - a| + 2 reach; sites further than that are skipped. Taking every such edge, not only
     * those of the sample's own cell, keeps the distance continuous across cell boundaries.
     */
    private void outsideEdgesInto(Sites n, int[] pair, double[] dd) {
        pair[0] = pair[1] = pair[2] = pair[3] = -1;
        dd[0] = dd[1] = dd[2] = Double.MAX_VALUE;
        double reach = s.halfWidth() + s.fieldEdgeFade;
        double lim = Math.sqrt(n.ds[n.i1]) + 2 * reach;
        double lim2 = lim * lim;
        boolean[] in = new boolean[Sites.N];
        boolean[] near = new boolean[Sites.N];
        for (int k = 0; k < Sites.N; k++) {
            near[k] = n.ds[k] <= lim2;
            if (near[k]) in[k] = inside(n.ids[k], n.xs[k], n.zs[k]);
        }
        for (int j = 0; j < Sites.N; j++) {
            if (!near[j] || in[j]) continue;
            for (int k = 0; k < Sites.N; k++) {
                if (!near[k] || !in[k]) continue;
                double d = edgeDistance(n, j, k);
                if (d >= Double.MAX_VALUE / 2) continue;
                if (d < dd[0]) {
                    dd[2] = dd[1];
                    dd[1] = dd[0]; pair[2] = pair[0]; pair[3] = pair[1];
                    dd[0] = d; pair[0] = j; pair[1] = k;
                } else if (d < dd[1]) {
                    dd[2] = dd[1];
                    dd[1] = d; pair[2] = j; pair[3] = k;
                } else if (d < dd[2]) {
                    dd[2] = d;
                }
            }
        }
    }
}
