package com.atlasmountains.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mountain chains. The world is divided into cells of {@link MountainSettings#chainCellSize};
 * a cell holds one chain with probability {@code chainChance}. A chain is a gently curved
 * centreline of random direction and length carrying 1 to 3 parallel spines (the same spine
 * spacing, curve, crest and cross profile as the parallel layout, so the mountains themselves
 * are unchanged). A chain may fork: a child chain starts on the parent part-way along and
 * leaves at 35 to 65 degrees, and may fork once more. Summits within
 * {@code junctionBoostLength} of a fork are raised by {@code junctionBoost}.
 * <p>
 * {@link #zone} is 1 inside the capsule around a chain's spines and fades to 0 over
 * {@code chainEdgeWidth} beyond it, so the ranges taper away at the chain ends and sides
 * (the relief is scaled by the mask).
 */
public final class ChainLayout implements SpineLayout {

    private static final class Chain {
        long id;
        double ox, oz, dx, dz, nx, nz, length;
        /** The chain occupies v in [vStart, vEnd] along its centreline (trimmed to the footprint). */
        double vStart, vEnd;
        int ranges;
        long curveSeed;
        double startBoost;             // child chains: a fork sits at along = 0
        final List<Double> forks = new ArrayList<>();   // along positions of children on this chain

        /** Lateral curve of the centreline at v, 0 at the origin so children attach exactly. */
        double curve(double v, MountainSettings s) {
            double w = s.chainCurveWavelength, a = s.chainCurveAmplitude;
            return (Hash.value1(curveSeed, v / w) - Hash.value1(curveSeed, 0)) * a
                 + (Hash.value1(curveSeed + 1, v / (w / 3) + 0.37) - Hash.value1(curveSeed + 1, 0.37)) * (a / 3);
        }

        /** World point at v along the centreline. */
        void point(double v, MountainSettings s, double[] out) {
            double c = curve(v, s);
            out[0] = ox + dx * v + nx * c;
            out[1] = oz + dz * v + nz * c;
        }

        /** Base (tight) gap between spine k-1 and spine k (k >= 1): the ranges meet back to back in a col. */
        double gap(int k, MountainSettings s) {
            double u = Hash.unit(Hash.hash(Hash.hash(id, 404, k), 2));
            return s.spineGapTightMin + u * (s.spineGapTightMax - s.spineGapTightMin);
        }

        /** Lateral base offset of spine k: spines are stacked from gaps and centred on the chain line. */
        double spineBase(int k, MountainSettings s) {
            double total = 0, pos = 0;
            for (int i = 1; i < ranges; i++) total += gap(i, s);
            for (int i = 1; i <= k; i++) pos += gap(i, s);
            return pos - total * 0.5;
        }

        /** Per gap (index k-1): the tight gap, then triples {centre, half, widening} of its valley pockets. Built by finish(). */
        double[] tightGap = new double[0];
        double[][] pockets = new double[0][];
        /** Per spine: start and end along the chain. Built by finish(). */
        double[] spStart = new double[0], spEnd = new double[0];

        /** Precomputes the per-gap pockets and per-spine extents; call after the chain's extents and spine count are final. */
        void finish(MountainSettings s) {
            tightGap = new double[ranges];
            pockets = new double[ranges][];
            for (int k = 1; k < ranges; k++) {
                tightGap[k] = gap(k, s);
                long c0 = (long) Math.floor((vStart - s.valleyPocketHalfMax) / s.valleyPocketSpacing);
                long c1 = (long) Math.floor((vEnd + s.valleyPocketHalfMax) / s.valleyPocketSpacing);
                ArrayList<double[]> list = new ArrayList<>();
                for (long c = c0; c <= c1; c++) {
                    long h = Hash.hash(id, 606, k, c);
                    if (Hash.unit(Hash.hash(h, 1)) >= s.spineGapWideChance) continue;
                    double centre = (c + Hash.unit(Hash.hash(h, 2))) * s.valleyPocketSpacing;
                    double half = s.valleyPocketHalfMin + Hash.unit(Hash.hash(h, 3)) * (s.valleyPocketHalfMax - s.valleyPocketHalfMin);
                    double wide = s.spineGapWideMin + Hash.unit(Hash.hash(h, 4)) * (s.spineGapWideMax - s.spineGapWideMin);
                    list.add(new double[] {centre, half, wide - tightGap[k]});
                }
                double[] flat = new double[list.size() * 3];
                for (int i = 0; i < list.size(); i++) System.arraycopy(list.get(i), 0, flat, i * 3, 3);
                pockets[k] = flat;
            }
            spStart = new double[ranges]; spEnd = new double[ranges];
            for (int k = 0; k < ranges; k++) { spStart[k] = spineStart(k, s); spEnd[k] = spineEnd(k, s); }
        }

        /**
         * Gap between spine k-1 and spine k at v: the tight gap swelling and shrinking a little
         * along the chain, plus enclosed valley pockets where it opens to a wide gap and closes
         * again (see MountainSettings.valleyPocket*).
         */
        double gapAt(int k, double v, MountainSettings s) {
            double n = Hash.value1(Hash.hash(id, 505, k), v / s.spineGapWavelength) * 2 - 1;
            double g = tightGap[k] * (1 + s.spineGapVariation * n);
            double[] pk = pockets[k];
            for (int i = 0; i < pk.length; i += 3) {
                double t = (v - pk[i]) / pk[i + 1];
                if (t <= -1 || t >= 1) continue;
                double bell = (1 - t * t) * (1 - t * t);
                g += pk[i + 2] * bell;
            }
            return g;
        }

        /** Each spine's own wander about its stacked position at v. */
        double wiggle(int k, double v, MountainSettings s) {
            return (Hash.value1(Hash.hash(id, 303, k), v / s.spineCurveWavelength) - 0.5) * 2 * s.spineCurveAmplitude
                 + (Hash.value1(Hash.hash(id, 313, k), v / s.spineWiggleWavelength) - 0.5) * 2 * s.spineWiggleAmplitude;
        }

        /**
         * Lateral offset of spine k (0..ranges-1) at v. Spines are stacked from gaps that vary
         * along the chain and each wanders on its own, so neighbouring ranges converge, diverge
         * and run back to back in places instead of lying parallel; they never come closer than
         * 200 blocks, so two ranges don't collapse into one.
         */
        double spineU(int k, double v, MountainSettings s) {
            double[] us = new double[ranges];
            spineUs(v, s, us);
            return us[k];
        }

        /** Lateral offsets of every spine at v, in one pass (each gap evaluated once). */
        void spineUs(double v, MountainSettings s, double[] out) {
            double total = 0;
            for (int i = 1; i < ranges; i++) { out[i] = gapAt(i, v, s); total += out[i]; }
            double pos = -total * 0.5, u = pos + wiggle(0, v, s);
            out[0] = u;
            for (int i = 1; i < ranges; i++) {
                pos += out[i];
                u = Math.max(u + 200, pos + wiggle(i, v, s));
                out[i] = u;
            }
        }

        boolean isMiddle(int k) { return Math.abs(k - (ranges - 1) * 0.5) < 0.51; }

        /** Where spine k starts along the chain: the middle spine runs the full length, outer ones are pulled in. */
        double spineStart(int k, MountainSettings s) {
            if (isMiddle(k)) return vStart;
            return vStart + (vEnd - vStart) * s.spineStaggerMax * Hash.unit(Hash.hash(id, 707, k));
        }

        double spineEnd(int k, MountainSettings s) {
            if (isMiddle(k)) return vEnd;
            return vEnd - (vEnd - vStart) * s.spineStaggerMax * Hash.unit(Hash.hash(id, 717, k));
        }

        int spineId(int k) { return (int) Hash.mix(Hash.hash(id, 909, k)); }

        int spineIndex(int spineId) {
            for (int k = 0; k < ranges; k++) if (spineId(k) == spineId) return k;
            return 0;
        }

        /** Half-width of the zone capsule: outer spine plus the range half-width plus a margin. */
        double zoneHalf(MountainSettings s) {
            return Math.abs(spineBase(ranges - 1, s)) * (1 + s.spineGapVariation) + 0.5 * (s.spineGapWideMax - s.spineGapTightMin)
                 + s.spineCurveAmplitude + s.spineWiggleAmplitude + s.halfWidth() + s.chainZoneMargin;
        }
    }

    private final MountainSettings s;
    private final long seed;
    private final Map<Long, Chain[]> cells = new ConcurrentHashMap<>();
    /**
     * (x, z) -> erosion on vanilla's scale (-1 mountainous .. 1 flat), supplied by the mod from
     * the world's own erosion noise. Chains are only placed where it is low (see
     * {@link MountainSettings#chainErosionBelow}), so they live in the hilly provinces of the
     * biome layer and not on the plains. Null (the diagnostics) means no gating.
     */
    private volatile java.util.function.DoubleBinaryOperator erosion;

    /** Sets the erosion field and forgets any chains placed without it. */
    @Override
    public void setErosion(java.util.function.DoubleBinaryOperator erosion) {
        this.erosion = erosion;
        cells.clear();
        neighbourhoods.clear();
        bySpine.clear();
    }

    public boolean hasErosion() { return erosion != null; }
    private final Map<Integer, Chain> bySpine = new ConcurrentHashMap<>();
    private final ThreadLocal<double[]> pt = ThreadLocal.withInitial(() -> new double[2]);

    public ChainLayout(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = Hash.hash(seed, 3030);
    }

    // ---- chain generation ------------------------------------------------------------------

    private Chain[] cell(long cx, long cz) {
        return cells.computeIfAbsent((cx << 32) ^ (cz & 0xffffffffL), k -> makeCell(cx, cz));
    }

    private Chain[] makeCell(long cx, long cz) {
        long h = Hash.hash(seed, cx, cz);
        double ox = (cx + Hash.unit(Hash.hash(h, 3))) * s.chainCellSize;
        double oz = (cz + Hash.unit(Hash.hash(h, 4))) * s.chainCellSize;
        if (cx == 0 && cz == 0) {   // the spawn chain starts 600 to 2400 blocks from the origin, well inside the spawn island
            ox = (0.1 + 0.3 * Hash.unit(Hash.hash(h, 3))) * s.chainCellSize;
            oz = (0.1 + 0.3 * Hash.unit(Hash.hash(h, 4))) * s.chainCellSize;
        }
        double chance = s.chainChance;
        java.util.function.DoubleBinaryOperator er = erosion;
        if (cx == 0 && cz == 0) chance = 1;          // spawn always has a chain in reach
        else if (er != null) {
            // biome-layer gating: full chance well below the threshold, none well above it
            double e = er.applyAsDouble(ox, oz);
            chance *= smoothstep((s.chainErosionBelow - e) / s.chainErosionBand + 0.5);
        }
        if (Hash.unit(Hash.hash(h, 1)) >= chance) return new Chain[0];
        List<Chain> out = new ArrayList<>();
        Chain root = new Chain();
        root.id = Hash.hash(h, 2);
        root.ox = ox;
        root.oz = oz;
        double angle = Hash.unit(Hash.hash(h, 5)) * Math.PI;
        root.dx = Math.cos(angle); root.dz = Math.sin(angle);
        root.nx = -root.dz; root.nz = root.dx;
        root.length = s.chainLengthMin + Hash.unit(Hash.hash(h, 6)) * (s.chainLengthMax - s.chainLengthMin);
        root.ranges = pickRanges(Hash.unit(Hash.hash(h, 7)));
        root.curveSeed = Hash.hash(h, 8);
        root.length = Math.max(root.length, s.chainMinAspect * 2 * root.zoneHalf(s));   // wide chains must be long: chains, not blobs
        root.vStart = 0; root.vEnd = root.length;
        if (!trim(root, 0)) return new Chain[0];
        root.finish(s);
        out.add(root);
        branch(root, s.chainBranchChance, 1, out);
        Chain[] arr = out.toArray(new Chain[0]);
        for (Chain c : arr) for (int k = 0; k < c.ranges; k++) bySpine.put(c.spineId(k), c);
        return arr;
    }

    /** Spines per chain from the chainRangesWeights table (1, 2, 3, 4 spines). */
    private int pickRanges(double u) {
        double[] w = s.chainRangesWeights;
        double total = 0;
        for (double v : w) total += v;
        double acc = 0;
        for (int i = 0; i < w.length; i++) {
            acc += w[i] / total;
            if (u < acc) return Math.max(s.chainRangesMin, Math.min(s.chainRangesMax, i + 1));
        }
        return s.chainRangesMax;
    }

    /**
     * Inner mask 0..1: 1 between the spines of a chain (the flat valley floor with lakes stays
     * ours there), fading to 0 just outside the outermost spine, so the foot of an outer flank
     * grows out of vanilla terrain instead of a flat plain.
     */
    public double inner(double x, double z) {
        RangeLayout.Sample sc = innerScratch.get();
        sample(x, z, sc);
        // between two spines: distance + distance2 == their gap; outside the outer spine it grows by twice the overshoot
        double outside = 0.5 * (sc.distance + sc.distance2 - s.spineGapWideMax);
        return smoothstep(1 - outside / s.innerFade);
    }

    private final ThreadLocal<RangeLayout.Sample> innerScratch = ThreadLocal.withInitial(RangeLayout.Sample::new);

    private void branch(Chain parent, double chance, int depth, List<Chain> out) {
        long h = Hash.hash(parent.id, 77, depth);
        if (Hash.unit(Hash.hash(h, 1)) >= chance) return;
        double t = parent.vStart + (parent.vEnd - parent.vStart) * (0.2 + 0.65 * Hash.unit(Hash.hash(h, 2)));
        double side = Hash.unit(Hash.hash(h, 3)) < 0.5 ? -1 : 1;
        double turn = Math.toRadians(s.chainBranchAngleMin + Hash.unit(Hash.hash(h, 4)) * (s.chainBranchAngleMax - s.chainBranchAngleMin));
        double parentAngle = Math.atan2(parent.dz, parent.dx);
        double angle = parentAngle + side * turn;
        Chain c = new Chain();
        c.id = Hash.hash(h, 5);
        double[] p = new double[2];
        parent.point(t, s, p);
        c.ox = p[0]; c.oz = p[1];
        c.dx = Math.cos(angle); c.dz = Math.sin(angle);
        c.nx = -c.dz; c.nz = c.dx;
        c.length = parent.length * (s.chainBranchLengthMin + Hash.unit(Hash.hash(h, 6)) * (s.chainBranchLengthMax - s.chainBranchLengthMin));
        c.ranges = Math.max(1, 1 + (int) Math.floor(Hash.unit(Hash.hash(h, 7)) * parent.ranges));
        c.curveSeed = Hash.hash(h, 8);
        c.length = Math.max(c.length, s.chainMinAspect * 2 * c.zoneHalf(s));
        c.vStart = 0; c.vEnd = c.length;
        if (!trim(c, depth)) return;
        c.finish(s);
        c.startBoost = s.junctionBoost;
        parent.forks.add(t);
        out.add(c);
        if (depth < s.chainMaxBranchDepth) branch(c, chance * 0.7, depth + 1, out);
    }

    /**
     * Trims a chain to the stretch of its centreline inside vanilla's mountain footprint
     * (erosion below erosionMountainBelow), so it ends with its own tapered ends inside the
     * mountain biome rather than being sliced by the biome edge. A child must keep its start,
     * where it meets its parent. Wide chains that come out short lose spines rather than
     * becoming blobs. Returns false if nothing worth keeping is left. No footprint: unchanged.
     */
    private boolean trim(Chain c, int depth) {
        boolean child = depth > 0;
        java.util.function.DoubleBinaryOperator er = erosion;
        if (er == null || !s.chainTrimToFootprint) return true;
        double[] p = new double[2];
        int n = (int) Math.floor(c.length / s.chainTrimStep) + 1;
        boolean[] in = new boolean[n + 1];
        for (int i = 0; i <= n; i++) {
            double v = Math.min(c.length, i * s.chainTrimStep);
            c.point(v, s, p);
            in[i] = er.applyAsDouble(p[0], p[1]) < s.erosionMountainBelow;
        }
        int bestA = -1, bestB = -1, a = -1;
        for (int i = 0; i <= n + 1; i++) {
            boolean inside = i <= n && in[i];
            if (inside && a < 0) a = i;
            if (!inside && a >= 0) {
                if (child ? a == 0 : (bestA < 0 || i - a > bestB - bestA)) { bestA = a; bestB = i - 1; }
                a = -1;
            }
        }
        if (bestA < 0) return false;
        double vs = Math.max(0, (bestA - 0.5) * s.chainTrimStep), ve = Math.min(c.length, (bestB + 0.5) * s.chainTrimStep);
        if (child) vs = 0;
        if (ve - vs < (depth >= 2 ? s.chainTrimMinGrandchildLength : s.chainTrimMinLength)) return false;
        c.vStart = vs; c.vEnd = ve;
        while (c.ranges > 1 && ve - vs < s.chainTrimMinAspect * 2 * c.zoneHalf(s)) c.ranges--;
        return true;
    }

    private final Map<Long, Chain[]> neighbourhoods = new ConcurrentHashMap<>();

    /**
     * All chains that can reach (x, z). A root chain starts anywhere in its cell and runs up to
     * chainLengthMax; a child up to 0.8 of that from part-way along, a grandchild 0.64: about
     * 2.5 lengths plus a cell. With chainLengthMax at most 1.2 cells that is under 4 cells, so
     * the 9x9 block of cells is searched. (Searching too few cells cut chains off along a
     * straight cell line as a vertical wall.) Only chains whose bounding reach touches the cell
     * are kept, so the per-sample loop stays short.
     */
    private Chain[] near(double x, double z) {
        long cx = (long) Math.floor(x / s.chainCellSize), cz = (long) Math.floor(z / s.chainCellSize);
        return neighbourhoods.computeIfAbsent((cx << 32) ^ (cz & 0xffffffffL), key -> {
            List<Chain> all = new ArrayList<>();
            double size = s.chainCellSize;
            double x0 = cx * size, z0 = cz * size, x1 = x0 + size, z1 = z0 + size;
            for (long i = cx - 4; i <= cx + 4; i++)
                for (long k = cz - 4; k <= cz + 4; k++)
                    for (Chain c : cell(i, k)) {
                        double reach = c.vEnd + c.zoneHalf(s) + s.chainEdgeWidth + 2 * s.halfWidth();
                        double nx = Math.max(x0, Math.min(x1, c.ox)), nz = Math.max(z0, Math.min(z1, c.oz));
                        if (Math.hypot(c.ox - nx, c.oz - nz) <= reach) all.add(c);
                    }
            return all.toArray(new Chain[0]);
        });
    }

    // ---- SpineLayout -----------------------------------------------------------------------

    @Override
    public void sample(double x, double z, RangeLayout.Sample out) {
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, 0, 0};   // dist, dist2, along, along2
        int[] bestId = {0, 0};
        double[] us = out.scratch;
        for (Chain c : near(x, z)) {
            double rx = x - c.ox, rz = z - c.oz;
            double v = rx * c.dx + rz * c.dz;
            double lat0 = rx * c.nx + rz * c.nz;
            double vAll = Math.max(c.vStart, Math.min(c.vEnd, v));
            // cheap lower bound on the distance to any spine of this chain: skip it if it can't beat the second best
            double overAll = Math.abs(v - vAll);
            double latBound = Math.max(0, Math.abs(lat0 - c.curve(vAll, s)) - c.zoneHalf(s) - 0.5 * (c.ranges - 1) * (s.spineGapWideMax - s.spineGapTightMin));
            if (latBound * latBound + overAll * overAll >= best[1] * best[1]) continue;
            if (us.length < c.ranges) { us = new double[Math.max(c.ranges, 4)]; out.scratch = us; }
            c.spineUs(vAll, s, us);
            double curveAll = c.curve(vAll, s);
            for (int k = 0; k < c.ranges; k++) {
                double vc = Math.max(c.spStart[k], Math.min(c.spEnd[k], v));
                double over = v - vc;                               // beyond this spine's end
                double u = vc == vAll ? us[k] : c.spineU(k, vc, s);
                double du = lat0 - (vc == vAll ? curveAll : c.curve(vc, s)) - u;
                double d = Math.sqrt(du * du + over * over);
                if (d < best[0]) {
                    best[1] = best[0]; best[3] = best[2]; bestId[1] = bestId[0];
                    best[0] = d; best[2] = vc; bestId[0] = c.spineId(k);
                } else if (d < best[1]) {
                    best[1] = d; best[3] = vc; bestId[1] = c.spineId(k);
                }
            }
        }
        out.range = bestId[0];
        out.distance = best[0];
        out.along = best[2];
        out.range2 = bestId[1];
        out.distance2 = best[1];
        out.along2 = best[3];
    }

    /**
     * Summit height factor: the chain's own overall scale, times a cross-chain profile (outer
     * spines are foothills, the middle spine is full height), times the larger of the fork boost
     * and the chain's one dominant summit (on the middle spine, well above its neighbours).
     */
    @Override
    public double summitScale(int range, double along) {
        Chain c = bySpine.get(range);
        if (c == null) return 1.0;
        int k = c.spineIndex(range);
        double centre = (c.ranges - 1) * 0.5;
        double profile = centre == 0 ? 1.0 : 1 - s.foothillDrop * Math.abs(k - centre) / centre;
        double chainScale = s.chainHeightScaleMin + Hash.unit(Hash.hash(c.id, 505)) * (s.chainHeightScaleMax - s.chainHeightScaleMin);
        double boost = 0;
        if (c.startBoost > 0) boost = Math.max(boost, c.startBoost * smoothstep(1 - along / s.junctionBoostLength));
        for (double t : c.forks) boost = Math.max(boost, s.junctionBoost * smoothstep(1 - Math.abs(along - t) / s.junctionBoostLength));
        if (Math.abs(k - centre) < 0.51) {   // the middle spine (or the two middle ones of an even chain)
            double vd = c.vStart + (c.vEnd - c.vStart) * (0.2 + 0.6 * Hash.unit(Hash.hash(c.id, 606)));
            boost = Math.max(boost, s.dominantSummitBoost * smoothstep(1 - Math.abs(along - vd) / s.dominantSummitLength));
        }
        return chainScale * profile * (1 + boost);
    }

    @Override
    public double zone(double x, double z) {
        double m = 0;
        for (Chain c : near(x, z)) {
            double rx = x - c.ox, rz = z - c.oz;
            double v = rx * c.dx + rz * c.dz;
            double vc = Math.max(c.vStart, Math.min(c.vEnd, v));
            double lateral = rx * c.nx + rz * c.nz - c.curve(vc, s);
            // capsule with round ends: distance to the centreline segment shortened by the
            // half-width at each end, minus the half-width. The middle spine runs the full
            // length, so the ranges taper away over the last half-width of the chain.
            double half = c.zoneHalf(s);
            double len = c.vEnd - c.vStart;
            double v2 = Math.max(c.vStart + Math.min(half, len * 0.5), Math.min(c.vEnd - half, v));
            double dv = v - v2;
            double dOut = Math.max(0, Math.sqrt(dv * dv + lateral * lateral) - half);
            double zm = smoothstep(1 - dOut / s.chainEdgeWidth);
            if (zm > m) m = zm;
        }
        return m;
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** For diagnostics: one line per chain whose zone reaches within radius blocks of (x, z). */
    public java.util.List<String> describe(double x, double z, double radius) {
        java.util.List<String> out = new ArrayList<>();
        for (Chain c : near(x, z)) {
            double rx = x - c.ox, rz = z - c.oz;
            double v = Math.max(c.vStart, Math.min(c.vEnd, rx * c.dx + rz * c.dz));
            double lateral = rx * c.nx + rz * c.nz - c.curve(v, s);
            double over = (rx * c.dx + rz * c.dz) - v;
            double d = Math.hypot(Math.max(0, Math.abs(lateral) - c.zoneHalf(s)), over);
            if (d > radius) continue;
            double[] a = new double[2], b = new double[2];
            c.point(c.vStart, s, a); c.point(c.vEnd, s, b);
            StringBuilder sp = new StringBuilder();
            for (int k = 0; k < c.ranges; k++) sp.append(String.format(java.util.Locale.UK, " [u%+.0f v%.0f..%.0f]", c.spineU(k, v, s), c.spineStart(k, s), c.spineEnd(k, s)));
            out.add(String.format(java.util.Locale.UK, "chain %08x %s: (%.0f,%.0f)->(%.0f,%.0f) len %.0f of %.0f, %d spines, %d forks, zoneHalf %.0f, your v %.0f lateral %+.0f; spines%s",
                (int) c.id, c.startBoost > 0 ? "child" : "root", a[0], a[1], b[0], b[1], c.vEnd - c.vStart, c.length, c.ranges, c.forks.size(), c.zoneHalf(s), v, lateral, sp));
        }
        return out;
    }

    /** For diagnostics: number of chains generated so far. */
    public int chainCount() { return bySpine.size(); }
}
