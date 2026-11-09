package com.atlasmountains.core;

/**
 * Steps 1 to 4: builds a region heightmap from the analytic range model, adds ridged detail
 * noise on the upper slopes and dips the valley floor where lakes sit.
 * Deterministic from (seed, regionX, regionZ). Erosion is applied afterwards by RegionPipeline.
 */
public final class HeightmapGenerator {

    private final MountainSettings s;
    private final long seed;
    private final FieldLayout layout;
    private final CrestProfile crest;
    private final CrossProfile cross;
    private final long detailSeed, lakeSeed;

    public HeightmapGenerator(MountainSettings settings, long seed) {
        this(settings, seed, new FieldLayout(settings, seed));
    }

    public HeightmapGenerator(MountainSettings settings, long seed, FieldLayout layout) {
        this.s = settings;
        this.seed = seed;
        this.layout = layout;
        this.crest = new CrestProfile(settings, seed);
        this.cross = new CrossProfile(settings);
        this.detailSeed = Hash.hash(seed, 606);
        this.lakeSeed = Hash.hash(seed, 707);
    }

    public FieldLayout layout() { return layout; }
    public CrestProfile crest() { return crest; }
    public MountainSettings settings() { return s; }

    /** Un-noised analytic shape: ridge, crest and cross profile only. */
    public double baseHeightAt(double x, double z, RidgeSample scratch) {
        scratch.scale = 1.0;
        layout.sample(x, z, scratch);
        double c = crest.height(scratch.ridge, scratch.along);
        if (scratch.scale != 1.0) c = s.valleyFloorHeight + (c - s.valleyFloorHeight) * scratch.scale;
        double h = cross.height(c, scratch.distance);
        // The second ridge matters only where it is within a half-width (two ridges meeting at a
        // junction): the union of the two mountains is continuous where either alone would show
        // a seam. Across a valley the second ridge is too far away and adds nothing.
        if (scratch.distance2 < s.halfWidth() && (scratch.ridge2 != scratch.ridge || scratch.along2 != scratch.along)) {
            double c2 = crest.height(scratch.ridge2, scratch.along2);
            if (scratch.scale != 1.0) c2 = s.valleyFloorHeight + (c2 - s.valleyFloorHeight) * scratch.scale;
            double h2 = cross.height(c2, scratch.distance2);
            if (h2 > h) h = h2;
        }
        return h;
    }

    /** Ridged multifractal in [0,1], sharp ridges at 1. */
    private double ridged(double x, double z) {
        double sum = 0, amp = 1, norm = 0, freq = 1.0 / s.detailWavelength;
        for (int o = 0; o < s.detailOctaves; o++) {
            double n = Hash.value2(Hash.hash(detailSeed, o), x * freq, z * freq);
            sum += amp * (1 - Math.abs(n));
            norm += amp;
            amp *= 0.5;
            freq *= 2;
        }
        return sum / norm;
    }

    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Full step 1 to 4 height at any world position. */
    public double heightAt(double x, double z, RidgeSample sc) {
        return heightFromBase(baseHeightAt(x, z, sc), x, z, sc);
    }

    /** heightAt given the base height already sampled into sc at (x, z). */
    private double heightFromBase(double h, double x, double z, RidgeSample sc) {
        double floor = s.valleyFloorHeight;

        if (h > floor + 1e-6) {
            // detail noise, scaled by height above the valley so flanks stay smooth
            double w = Math.pow(Math.max(0, (h - floor) / s.relief()), s.detailHeightPower);
            h += s.detailAmplitude * (ridged(x, z) - 0.5) * w;
            return h;
        }

        // valley floor: dip a lake bed along the centre of the valley where the lake mask is on
        double half = s.halfWidth();
        double width = sc.distance + sc.distance2 - 2 * half;   // flat floor width here
        if (width <= 1) return h;
        double inset = sc.distance - half;                        // how far into the floor from the near edge
        double fromCentre = Math.abs(width / 2 - inset);
        double lakeHalf = s.lakeWidthFraction * width / 2;
        double shape = smoothstep(1 - fromCentre / lakeHalf);
        double n = Hash.value1(lakeSeed, sc.along / s.lakeWavelength);  // -1..1 along the valley
        double mask = smoothstep((n + (2 * s.lakeCoverage - 1)) / 0.4 + 0.5);
        return floor - s.lakeDepth * shape * mask;
    }

    public RegionHeightmap generate(int regionX, int regionZ) {
        RegionHeightmap map = new RegionHeightmap(s, regionX, regionZ);
        map.floorModel = new float[map.size * map.size];
        RidgeSample scratch = new RidgeSample();
        double floor = s.valleyFloorHeight;
        for (int gz = 0; gz < map.size; gz++) {
            double z = map.originZ + gz * (double) map.spacing;
            for (int gx = 0; gx < map.size; gx++) {
                double x = map.originX + gx * (double) map.spacing;
                double base = baseHeightAt(x, z, scratch);
                double h = heightFromBase(base, x, z, scratch);
                map.set(gx, gz, (float) h);
                map.floorModel[gz * map.size + gx] = base > floor + 1e-6 ? Float.NaN : (float) h;
            }
        }
        return map;
    }
}
