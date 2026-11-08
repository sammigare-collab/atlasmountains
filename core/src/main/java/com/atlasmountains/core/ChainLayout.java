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
        int ranges;
        long curveSeed;
        double startBoost;             // child chains: a fork sits at along = 0
        final List<Double> forks = new ArrayList<>();   // along positions of children on this chain

        /** Lateral curve of the centreline at v, 0 at the origin so children attach exactly. */
        double curve(double v, MountainSettings s) {
            return (Hash.value1(curveSeed, v / s.chainCurveWavelength) - Hash.value1(curveSeed, 0)) * s.chainCurveAmplitude;
        }

        /** World point at v along the centreline. */
        void point(double v, MountainSettings s, double[] out) {
            double c = curve(v, s);
            out[0] = ox + dx * v + nx * c;
            out[1] = oz + dz * v + nz * c;
        }

        /** Lateral offset of spine k (0..ranges-1) at v. */
        double spineU(int k, double v, MountainSettings s) {
            double base = (k - (ranges - 1) * 0.5) * s.rangeSpacing;
            double jitter = Hash.signed(Hash.hash(id, 202, k)) * s.rangeSpacingJitter;
            double curve = Hash.value1(Hash.hash(id, 303, k), v / s.spineCurveWavelength) * s.spineCurveAmplitude;
            return base + jitter + curve;
        }

        int spineId(int k) { return (int) Hash.mix(Hash.hash(id, 909, k)); }

        /** Half-width of the zone capsule: outer spine plus the range half-width plus a margin. */
        double zoneHalf(MountainSettings s) {
            return (ranges - 1) * 0.5 * s.rangeSpacing + s.rangeSpacingJitter + s.spineCurveAmplitude + s.halfWidth() + s.chainZoneMargin;
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
        double ru = Hash.unit(Hash.hash(h, 7));
        root.ranges = s.chainRangesMin + (int) Math.floor(ru * ru * (s.chainRangesMax - s.chainRangesMin + 1));   // skewed: wide chains are rarer
        root.ranges = Math.min(root.ranges, s.chainRangesMax);
        root.curveSeed = Hash.hash(h, 8);
        out.add(root);
        branch(root, s.chainBranchChance, 1, out);
        Chain[] arr = out.toArray(new Chain[0]);
        for (Chain c : arr) for (int k = 0; k < c.ranges; k++) bySpine.put(c.spineId(k), c);
        return arr;
    }

    private void branch(Chain parent, double chance, int depth, List<Chain> out) {
        long h = Hash.hash(parent.id, 77, depth);
        if (Hash.unit(Hash.hash(h, 1)) >= chance) return;
        double t = parent.length * (0.3 + 0.4 * Hash.unit(Hash.hash(h, 2)));
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
        c.startBoost = s.junctionBoost;
        parent.forks.add(t);
        out.add(c);
        if (depth < s.chainMaxBranchDepth) branch(c, chance * 0.6, depth + 1, out);
    }

    private final Map<Long, Chain[]> neighbourhoods = new ConcurrentHashMap<>();

    /** All chains whose cells are within reach of (x, z): the 5x5 cells around it (a chain and its children stay within two cells). */
    private Chain[] near(double x, double z) {
        long cx = (long) Math.floor(x / s.chainCellSize), cz = (long) Math.floor(z / s.chainCellSize);
        return neighbourhoods.computeIfAbsent((cx << 32) ^ (cz & 0xffffffffL), key -> {
            List<Chain> all = new ArrayList<>();
            for (long i = cx - 2; i <= cx + 2; i++)
                for (long k = cz - 2; k <= cz + 2; k++)
                    for (Chain c : cell(i, k)) all.add(c);
            return all.toArray(new Chain[0]);
        });
    }

    // ---- SpineLayout -----------------------------------------------------------------------

    @Override
    public void sample(double x, double z, RangeLayout.Sample out) {
        double[] best = {Double.MAX_VALUE, Double.MAX_VALUE, 0, 0};   // dist, dist2, along, along2
        int[] bestId = {0, 0};
        for (Chain c : near(x, z)) {
            double rx = x - c.ox, rz = z - c.oz;
            double v = rx * c.dx + rz * c.dz;
            double vc = Math.max(0, Math.min(c.length, v));
            double over = v - vc;                                   // beyond an end
            double lateral = rx * c.nx + rz * c.nz - c.curve(vc, s);
            for (int k = 0; k < c.ranges; k++) {
                double du = lateral - c.spineU(k, vc, s);
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

    @Override
    public double summitScale(int range, double along) {
        Chain c = bySpine.get(range);
        if (c == null) return 1.0;
        double boost = 0;
        if (c.startBoost > 0) boost = Math.max(boost, c.startBoost * smoothstep(1 - along / s.junctionBoostLength));
        for (double t : c.forks) boost = Math.max(boost, s.junctionBoost * smoothstep(1 - Math.abs(along - t) / s.junctionBoostLength));
        return 1 + boost;
    }

    @Override
    public double zone(double x, double z) {
        double m = 0;
        for (Chain c : near(x, z)) {
            double rx = x - c.ox, rz = z - c.oz;
            double v = rx * c.dx + rz * c.dz;
            double vc = Math.max(0, Math.min(c.length, v));
            double lateral = rx * c.nx + rz * c.nz - c.curve(vc, s);
            // capsule with round ends: distance to the centreline segment shortened by the
            // half-width at each end, minus the half-width. The spines run the full length, so
            // the ranges taper away over the last half-width of the chain.
            double half = c.zoneHalf(s);
            double v2 = Math.max(Math.min(half, c.length * 0.5), Math.min(c.length - half, v));
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

    /** For diagnostics: number of chains generated so far. */
    public int chainCount() { return bySpine.size(); }
}
