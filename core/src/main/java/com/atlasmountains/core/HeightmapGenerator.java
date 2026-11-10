package com.atlasmountains.core;

/**
 * The un-eroded mountain: steps 1 to 4 of the pipeline.
 * <ol>
 *   <li>Ask the layout where the nearest ridges are ({@link FieldLayout#sample}).</li>
 *   <li>Read the crest height there ({@link CrestProfile}: a row of pointed summits along the
 *       ridge), scaled by the layout's height field so some massifs are bigger than others.</li>
 *   <li>Turn the distance from the ridge into a height ({@link CrossProfile}: rock face down to
 *       the rock line, then a concave grass flank to the valley floor). At a junction the
 *       higher of the two nearest ridges wins.</li>
 *   <li>Season it: ridged detail noise on the upper faces, and lake beds dipped into the valley
 *       floors.</li>
 * </ol>
 * The result is deterministic from the seed and the position, so any thread can ask for any
 * point and get the same answer. Erosion is applied afterwards, per region, by
 * {@link RegionPipeline}; {@link #heightAt} is also used directly where an eroded value is not
 * needed (the mask's flank test, the biome source).
 */
public final class HeightmapGenerator {

    private final MountainSettings s;
    private final long seed;
    private final FieldLayout layout;
    private final CrestProfile crest;
    private final CrossProfile cross;
    private final long detailSeed, lakeSeed;

    /** A generator with its own field layout (tests, the diagnostic tool). */
    public HeightmapGenerator(MountainSettings settings, long seed) {
        this(settings, seed, new FieldLayout(settings, seed));
    }

    /** A generator around a given layout, so the mod can install the world's erosion on that layout. */
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

    /**
     * Un-noised analytic shape: ridge, crest and cross profile only (steps 1 to 3). Leaves the
     * ridge sample in {@code scratch}, which {@link #heightFromBase} reads for the lake test.
     */
    public double baseHeightAt(double x, double z, RidgeSample scratch) {
        scratch.scale = 1.0;
        layout.sample(x, z, scratch);
        double c = Math.max(crest.height(scratch.ridge, scratch.along), junctionCrest(scratch.distance2, scratch));
        // the height scale stretches the relief above the valley floor, not the absolute height,
        // so a small massif is a lower mountain on the same floor, not a sunken one
        if (scratch.scale != 1.0) c = s.valleyFloorHeight + (c - s.valleyFloorHeight) * scratch.scale;
        double h = cross.height(c, scratch.distance);
        // The second ridge matters only where it is within a half-width (two ridges meeting at a
        // junction): the union of the two mountains is continuous where either alone would show
        // a seam. Across a valley the second ridge is too far away and adds nothing.
        if (scratch.distance2 < s.halfWidth() && (scratch.ridge2 != scratch.ridge || scratch.along2 != scratch.along)) {
            double c2 = Math.max(crest.height(scratch.ridge2, scratch.along2), junctionCrest(scratch.distance, scratch));
            if (scratch.scale != 1.0) c2 = s.valleyFloorHeight + (c2 - s.valleyFloorHeight) * scratch.scale;
            double h2 = cross.height(c2, scratch.distance2);
            if (h2 > h) h = h2;
        }
        return h;
    }

    /**
     * The crest of the junction summit, seen from a ridge at distance {@code other} from the other
     * ridge that meets it there (unscaled, like {@link CrestProfile#height}). Near a junction the
     * distance to the other ridge is roughly the distance along this ridge from the junction
     * (times sin 60 degrees for the usual three-way meeting at 120 degrees, hence the 1.15), so
     * this is a tent with its apex at the junction, falling at crestFallSlope, the same as an
     * ordinary summit. Returns -infinity where there is no other ridge close enough for it to
     * matter (deep inside a cell the second ridge is the far side of a valley).
     */
    private double junctionCrest(double other, RidgeSample scratch) {
        if (s.junctionPeakBoost <= 0 || scratch.ridge2 == scratch.ridge) return Double.NEGATIVE_INFINITY;
        double apex = s.summitHeight + s.junctionPeakBoost * s.relief();
        double c = apex - s.crestFallSlope * 1.15 * other;
        return c > s.valleyFloorHeight ? c : Double.NEGATIVE_INFINITY;
    }

    /**
     * Height of the summit this column belongs to: the nearest summit along the nearest ridge,
     * with the same height scale as {@link #baseHeightAt}. Used to shade rock by how far below its
     * own peak it is, so every peak, large or small, gets the same run from dark to light.
     * This is the design height of that summit; erosion trims real summits by a few blocks.
     */
    public double peakHeightAt(double x, double z, RidgeSample scratch) {
        scratch.scale = 1.0;
        layout.sample(x, z, scratch);
        long i0 = Math.round(scratch.along / s.peakSpacing);
        long best = i0;
        double bestGap = Double.MAX_VALUE;
        for (long i = i0 - 1; i <= i0 + 1; i++) {   // jitter is under half a spacing, so the nearest is one of these
            double gap = Math.abs(crest.summitAlong(scratch.ridge, i) - scratch.along);
            if (gap < bestGap) { bestGap = gap; best = i; }
        }
        double c = crest.summitHeight(scratch.ridge, best);
        // near a junction the column belongs to the junction summit if that stands higher
        double apex = s.summitHeight + s.junctionPeakBoost * s.relief();
        if (s.junctionPeakBoost > 0 && scratch.ridge2 != scratch.ridge
                && apex - s.crestFallSlope * 1.15 * scratch.distance2 > crest.height(scratch.ridge, scratch.along)) c = apex;
        if (scratch.scale != 1.0) c = s.valleyFloorHeight + (c - s.valleyFloorHeight) * scratch.scale;
        return c;
    }

    /**
     * Ridged multifractal in [0,1], sharp ridges at 1: each octave is 1 - |noise|, which folds the
     * noise's zero crossings into sharp creases. Octaves halve in amplitude and double in
     * frequency, and the sum is normalised by the total amplitude.
     */
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

    /** 3t^2 - 2t^3 on t clamped to 0..1: an S-curve with zero slope at both ends. */
    private static double smoothstep(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Full step 1 to 4 height at any world position. */
    public double heightAt(double x, double z, RidgeSample sc) {
        return heightFromBase(baseHeightAt(x, z, sc), x, z, sc);
    }

    /**
     * heightAt given the base height already sampled into sc at (x, z): step 4. Above the valley
     * floor this adds detail noise; on the floor it may dip a lake.
     */
    private double heightFromBase(double h, double x, double z, RidgeSample sc) {
        double floor = s.valleyFloorHeight;

        if (h > floor + 1e-6) {
            // detail noise, scaled by height above the valley so flanks stay smooth
            double w = Math.pow(Math.max(0, (h - floor) / s.relief()), s.detailHeightPower);
            h += s.detailAmplitude * (ridged(x, z) - 0.5) * w;   // centred, so the mean height is unchanged
            return h;
        }

        // valley floor: dip a lake bed along the centre of the valley where the lake mask is on.
        // The floor between two ridges is (distance + distance2 - 2 half-widths) wide; the lake
        // takes the middle lakeWidthFraction of it, with a smoothstep cross-section, and an on/off
        // noise along the valley decides which stretches have water.
        double half = s.halfWidth();
        double width = sc.distance + sc.distance2 - 2 * half;   // flat floor width here
        if (width <= 1) return h;
        double inset = sc.distance - half;                        // how far into the floor from the near edge
        double fromCentre = Math.abs(width / 2 - inset);
        double lakeHalf = s.lakeWidthFraction * width / 2;
        double shape = smoothstep(1 - fromCentre / lakeHalf);
        double n = Hash.value1(lakeSeed, sc.along / s.lakeWavelength);  // -1..1 along the valley
        // shift the noise so about lakeCoverage of the valley length is "on", with a soft
        // 0.4-wide transition so a lake tapers out instead of ending in a wall of water
        double mask = smoothstep((n + (2 * s.lakeCoverage - 1)) / 0.4 + 0.5);
        return floor - s.lakeDepth * shape * mask;
    }

    /**
     * The heightmap of one region, overlap included, on the sample grid. Also records
     * {@code floorModel}: the model height where a sample is valley floor and NaN where it is
     * mountain, so the valley fill after erosion can restore flat floors and lakes exactly
     * (see {@link com.atlasmountains.core.erosion.ValleyFill}).
     */
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
