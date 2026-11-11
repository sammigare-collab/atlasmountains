package com.atlasmountains.core;

/**
 * Where the ridges are: the layout used by the mixed preset (the one players use).
 *
 * <h2>The idea</h2>
 * A ridge skeleton. The plane is divided into squares, each with one jittered site; a site is
 * "inside" the mountains where vanilla's mountain footprint (low erosion) is on. Every inside
 * site is joined to at most one inside neighbour, its parent, by a straight spine; the parent
 * always has a higher hashed priority, so the spines form trees and can never close into a
 * loop. An inside site with no inside neighbour gets a short spine of its own. A warp of the
 * sample position bends the straight spines so they wander.
 * <p>
 * Until Nov 2026 ridges were the edges of the Voronoi cells of the same sites. Every mountain
 * cell was then walled in by ridges on all sides, which read as rings and triangles around a
 * bowl, with straight edges and plain outer walls; Sammi asked for the layout to be rebuilt.
 * <p>
 * Each spine is its own range (seeded by its two sites), so the crest profile strings summits
 * along it. The two nearest spines are reported, so the generator unions them where they meet
 * at a site (a junction summit), and legs (side ridges, see HeightmapGenerator.spurHeight)
 * branch off the summits. Summit heights are scaled by a slow 2-D field
 * ({@link MountainSettings#fieldHeightBoost} on average, plus or minus
 * {@link MountainSettings#fieldHeightVariation}), so some massifs are big and some small.
 *
 * <h2>Steps for one position</h2>
 * <ol>
 *   <li><b>Warp.</b> The position is shifted by a smooth 2-octave noise ({@link #warp}).
 *       Everything below uses the warped point.</li>
 *   <li><b>Spines.</b> The spines that can come within reach of the point all have an end in the
 *       7x7 squares around it (a spine between neighbouring squares is at most 2.1 squares long,
 *       and reach is under one square). That list is built once per square and cached
 *       ({@link #spinesNear}).</li>
 *   <li><b>Distance.</b> The distance to a spine is the distance to the segment, so a spine's
 *       end is a rounded cap; nothing is infinite and nothing jumps at a cell boundary.</li>
 * </ol>
 *
 * <h2>Thread safety</h2>
 * Called from many chunk-generation threads. Shared state is the per-site and per-square caches
 * (concurrent maps of values that are pure functions of the seed and the erosion field, so a
 * race only computes the same answer twice).
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
    public void setErosion(java.util.function.DoubleBinaryOperator erosion) { this.erosion = erosion; siteInside.clear(); sites.clear(); spineLists.clear(); }

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

    // ---- sites ---------------------------------------------------------------------------

    /** One square's site: position, identity, whether it is in the footprint, and its priority in the tree. */
    private static final class Site {
        double x, z, prio;
        long id;
        boolean in;
    }

    private final java.util.Map<Long, Site> sites = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Long, Spine[]> spineLists = new java.util.concurrent.ConcurrentHashMap<>();
    /** Per thread: the last square asked about and its spines (consecutive samples share a square). */
    private final ThreadLocal<Object[]> lastSquare = ThreadLocal.withInitial(() -> new Object[] { null, null, null });

    private static long key(long i, long j) { return (i << 32) ^ (j & 0xffffffffL); }

    private Site site(long i, long j) {
        long k = key(i, j);
        Site t = sites.get(k);
        if (t != null) return t;
        t = new Site();
        double c = s.fieldCellSize;
        long h = Hash.hash(siteSeed, i, j);   // the site's identity: same square, same site, on every thread
        // site = square centre + jitter, the jitter uniform in +/- fieldJitter/2 of a cell
        t.x = (i + 0.5 + (Hash.unit(Hash.hash(h, 1)) - 0.5) * s.fieldJitter) * c;
        t.z = (j + 0.5 + (Hash.unit(Hash.hash(h, 2)) - 0.5) * s.fieldJitter) * c;
        t.id = h;
        t.prio = Hash.unit(Hash.hash(h, 31));
        t.in = erosion == null || inside(h, t.x, t.z);
        if (sites.size() > 200_000) sites.clear();
        sites.put(k, t);
        return t;
    }

    private static final int[] DI = { 1, -1, 0, 0 }, DJ = { 0, 0, 1, -1 };

    /**
     * The neighbour (0..3 into DI/DJ) an inside site is joined to, or -1. It is the inside
     * 4-neighbour with the lowest priority above the site's own: always upwards in priority, so
     * following parents can never come back round (no loops), and the lowest such, so chains run
     * long instead of every site joining the local maximum directly (a star).
     */
    private int parent(long i, long j) {
        Site me = site(i, j);
        if (!me.in) return -1;
        int best = -1;
        double bp = Double.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            Site n = site(i + DI[d], j + DJ[d]);
            if (n.in && n.prio > me.prio && n.prio < bp) { bp = n.prio; best = d; }
        }
        return best;
    }

    /** How many neighbours are joined to this site as their parent. */
    private int children(long i, long j) {
        int n = 0;
        for (int d = 0; d < 4; d++) {
            long ni = i + DI[d], nj = j + DJ[d];
            int p = parent(ni, nj);
            if (p >= 0 && ni + DI[p] == i && nj + DJ[p] == j) n++;
        }
        return n;
    }

    // ---- spines --------------------------------------------------------------------------

    /**
     * One spine: a segment with midpoint (mx, mz), unit direction (tx, tz) and half-length. The
     * direction is fixed by the order of the two site ids, so "along" means the same thing from
     * both sides. leafLo / leafHi say whether the end at -half / +half is a free end (nothing
     * else joins there), which the generator tapers down.
     */
    private static final class Spine {
        double mx, mz, tx, tz, half;
        int ridge;
        boolean leafLo, leafHi;
    }

    private Spine between(Site a, boolean aLeaf, Site b, boolean bLeaf) {
        if (a.id > b.id) { Site t = a; a = b; b = t; boolean l = aLeaf; aLeaf = bLeaf; bLeaf = l; }
        Spine sp = new Spine();
        double ex = b.x - a.x, ez = b.z - a.z, len = Math.max(1e-9, Math.hypot(ex, ez));
        sp.mx = (a.x + b.x) * 0.5; sp.mz = (a.z + b.z) * 0.5;
        sp.tx = ex / len; sp.tz = ez / len; sp.half = len * 0.5;
        sp.ridge = (int) Hash.hash(a.id, b.id);
        sp.leafLo = aLeaf; sp.leafHi = bLeaf;
        return sp;
    }

    /** Every spine with an end in the 7x7 squares around square (ci, cj). */
    private Spine[] spinesNear(long ci, long cj) {
        Object[] last = lastSquare.get();
        Long k = key(ci, cj);
        if (k.equals(last[0]) && last[2] == erosion) return (Spine[]) last[1];
        Spine[] list = spineLists.get(k);
        if (list == null) {
            java.util.ArrayList<Spine> out = new java.util.ArrayList<>();
            for (long i = ci - 3; i <= ci + 3; i++) {
                for (long j = cj - 3; j <= cj + 3; j++) {
                    Site c = site(i, j);
                    if (!c.in) continue;
                    int p = parent(i, j);
                    int kids = children(i, j);
                    if (p >= 0) {
                        long pi = i + DI[p], pj = j + DJ[p];
                        boolean parentFree = parent(pi, pj) < 0 && children(pi, pj) == 1;
                        out.add(between(c, kids == 0, site(pi, pj), parentFree));
                    } else if (kids == 0) {
                        // a mountain cell on its own: a short spine through the site, in a hashed direction
                        double ang = Hash.unit(Hash.hash(c.id, 32)) * Math.PI;
                        Spine sp = new Spine();
                        sp.mx = c.x; sp.mz = c.z; sp.tx = Math.cos(ang); sp.tz = Math.sin(ang);
                        sp.half = s.loneSpineHalfLength * s.fieldCellSize;
                        sp.ridge = (int) Hash.hash(c.id, c.id ^ 0x5bd1e995L);
                        sp.leafLo = sp.leafHi = true;
                        out.add(sp);
                    }
                }
            }
            list = out.toArray(new Spine[0]);
            if (spineLists.size() > 50_000) spineLists.clear();
            spineLists.put(k, list);
        }
        last[0] = k; last[1] = list; last[2] = erosion;
        return list;
    }

    /**
     * The nearest two spines to (x, z) and the height scale there, written into out; see
     * {@link RidgeSample}. With no spine in reach the distance is set to three half-widths, which
     * the generator turns into flat valley floor.
     */
    public void sample(double x, double z, RidgeSample out) {
        double px = x + warp(1, x, z), pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        Spine[] list = spinesNear((long) Math.floor(px / c), (long) Math.floor(pz / c));
        Spine b1 = null, b2 = null;
        double d1 = Double.MAX_VALUE, d2 = Double.MAX_VALUE, d3 = Double.MAX_VALUE;
        for (Spine sp : list) {
            double rx = px - sp.mx, rz = pz - sp.mz;
            double al = rx * sp.tx + rz * sp.tz, pe = Math.abs(rz * sp.tx - rx * sp.tz);
            double over = Math.abs(al) - sp.half;
            double d = over > 0 ? Math.hypot(pe, over) : pe;
            if (d < d1) { d3 = d2; d2 = d1; b2 = b1; d1 = d; b1 = sp; }
            else if (d < d2) { d3 = d2; d2 = d; b2 = sp; }
            else if (d < d3) d3 = d;
        }
        double far = 3 * s.halfWidth();
        if (b1 == null) {
            out.distance = far; out.along = 0; out.ridge = 0; out.alongRaw = 0; out.perp = far; out.half = 0; out.leafLo = out.leafHi = false;
        } else {
            double rx = px - b1.mx, rz = pz - b1.mz, al = rx * b1.tx + rz * b1.tz;
            out.distance = d1; out.ridge = b1.ridge; out.half = b1.half;
            out.alongRaw = al; out.along = Math.max(-b1.half, Math.min(b1.half, al));
            out.perp = Math.abs(rz * b1.tx - rx * b1.tz);
            out.leafLo = b1.leafLo; out.leafHi = b1.leafHi;
        }
        if (b2 == null) {
            out.distance2 = far; out.along2 = 0; out.ridge2 = out.ridge; out.alongRaw2 = 0; out.perp2 = far; out.half2 = 0; out.leafLo2 = out.leafHi2 = false;
        } else {
            double rx = px - b2.mx, rz = pz - b2.mz, al = rx * b2.tx + rz * b2.tz;
            out.distance2 = d2; out.ridge2 = b2.ridge; out.half2 = b2.half;
            out.alongRaw2 = al; out.along2 = Math.max(-b2.half, Math.min(b2.half, al));
            out.perp2 = Math.abs(rz * b2.tx - rx * b2.tz);
            out.leafLo2 = b2.leafLo; out.leafHi2 = b2.leafHi;
        }
        out.distance3 = d3;
        out.scale = heightScale(x, z);
    }

    /**
     * The mask: 1 within one flank's width (halfWidth) of a spine, then a fade to 0 over
     * fieldEdgeFade blocks with a smoothstep, so there is no kink where the fade starts or ends.
     * NaN until the erosion field is installed. It is the same distance sample() reports, so the
     * mask and the terrain move together.
     */
    public double zone(double x, double z) {
        if (erosion == null) return Double.NaN;
        double px = x + warp(1, x, z), pz = z + warp(2, x, z);
        double c = s.fieldCellSize;
        double d = Double.MAX_VALUE;
        for (Spine sp : spinesNear((long) Math.floor(px / c), (long) Math.floor(pz / c))) {
            double rx = px - sp.mx, rz = pz - sp.mz;
            double pe = Math.abs(rz * sp.tx - rx * sp.tz), over = Math.abs(rx * sp.tx + rz * sp.tz) - sp.half;
            d = Math.min(d, over > 0 ? Math.hypot(pe, over) : pe);
        }
        double t = (s.halfWidth() + s.fieldEdgeFade - d) / s.fieldEdgeFade;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }
}
