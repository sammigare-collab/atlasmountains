package com.atlasmountains.core;

/**
 * Steps 1 to 4: builds a region heightmap from the analytic range model, adds ridged detail
 * noise on the upper slopes and dips the valley floor where lakes sit.
 * Deterministic from (seed, regionX, regionZ). Erosion is applied afterwards by RegionPipeline.
 */
public final class HeightmapGenerator {

    private final MountainSettings s;
    private final long seed;
    private final RangeLayout layout;
    private final CrestProfile crest;
    private final CrossProfile cross;
    private final long detailSeed, lakeSeed;

    public HeightmapGenerator(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
        this.layout = new RangeLayout(settings, seed);
        this.crest = new CrestProfile(settings, seed);
        this.cross = new CrossProfile(settings);
        this.detailSeed = Hash.hash(seed, 606);
        this.lakeSeed = Hash.hash(seed, 707);
    }

    public RangeLayout layout() { return layout; }
    public CrestProfile crest() { return crest; }
    public MountainSettings settings() { return s; }

    /** Un-noised analytic shape: spine, crest, cross profile only (steps 1 to 3). */
    public double baseHeightAt(double x, double z, RangeLayout.Sample scratch) {
        layout.sample(x, z, scratch);
        double c = crest.height(scratch.range, scratch.along);
        return cross.height(c, scratch.distance);
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
    public double heightAt(double x, double z, RangeLayout.Sample sc) {
        double h = baseHeightAt(x, z, sc);
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
        RangeLayout.Sample scratch = new RangeLayout.Sample();
        for (int gz = 0; gz < map.size; gz++) {
            double z = map.originZ + gz * (double) map.spacing;
            for (int gx = 0; gx < map.size; gx++) {
                double x = map.originX + gx * (double) map.spacing;
                map.set(gx, gz, (float) heightAt(x, z, scratch));
            }
        }
        return map;
    }
}
