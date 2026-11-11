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
    private final long detailSeed, lakeSeed, spurSeed;

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
        this.spurSeed = Hash.hash(seed, 808);
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
        // The union of every spine within a half-width: each is a mountain of its own (crest along
        // it, cross profile off it) and the terrain is the highest. Where spines meet, each also
        // carries a tent centred on the joint (junctionCrest), so an elbow or a fork is a summit.
        // The height scale, a side ridge's relief and the taper towards a free end all stretch the
        // relief above the valley floor, not the absolute height.
        double k1 = scratch.scale * scratch.rel * taper(scratch.along, scratch.half, scratch.leafLo, scratch.leafHi);
        double k2 = scratch.scale * scratch.rel2 * taper(scratch.along2, scratch.half2, scratch.leafLo2, scratch.leafHi2);
        double h = cross.height(s.valleyFloorHeight, scratch.distance);
        for (int i = 0; i < scratch.n; i++) {
            int r = scratch.nRidge[i];
            boolean nearest = r == scratch.ridge;
            double other = nearest ? scratch.distance2 : scratch.distance;
            int otherRidge = nearest ? scratch.ridge2 : scratch.ridge;
            double c = Math.max(crest.height(r, scratch.nAlong[i]), junctionCrest(other, r, otherRidge));
            double k = scratch.scale * scratch.nRel[i] * taper(scratch.nAlong[i], scratch.nHalf[i], scratch.nLeafLo[i], scratch.nLeafHi[i]);
            c = s.valleyFloorHeight + (c - s.valleyFloorHeight) * k;
            double hi = cross.height(c, scratch.nDist[i]);
            if (hi > h) h = hi;
        }
        // Legs: side ridges branching off the summits, so a long face is broken into arms instead of
        // one plain wall (the outer face of an area's border ridge had nothing else to break it).
        // Only the two nearest ridges are known, so a leg is faded out as its ridge is about to
        // drop to third nearest (distance3 catching up): it ends smoothly instead of being cut.
        double sp = spurHeight(scratch.ridge, scratch.alongRaw, scratch.perp, scratch.distance, scratch.half, k1, scratch.distance3 - scratch.distance);
        if (sp > h) h = sp;
        if (scratch.ridge2 != scratch.ridge) {
            double sp2 = spurHeight(scratch.ridge2, scratch.alongRaw2, scratch.perp2, scratch.distance2, scratch.half2, k2, scratch.distance3 - scratch.distance2);
            if (sp2 > h) h = sp2;
        }
        return h;
    }

    /**
     * The highest leg (side ridge) of this ridge at a point {@code v} blocks off the ridge and
     * {@code along} along it, or -infinity. A summit gets a leg with probability spurChance (per
     * side of the ridge alike: both sides are measured the same way). The leg leaves the summit at
     * spurAngleMin..spurAngleMax degrees off straight down the face, leaning one way or the other
     * along the ridge, and its crest falls at spurFallSlope, gentler than the face, so it runs out
     * past the foot of the face before it reaches the floor. Along it, small peaks every ~spurPeakSpacing
     * blocks (notches spurNotchDepth deep); across it, sides at spurSideSlope, a sharp arete. It
     * never stands above the main crest near the ridge (its slope there is steeper than the crest's
     * fall), and it fades out between spurFadeStart and spurReach half-widths from the ridge, and
     * over the last spurHandover blocks before its ridge stops being one of the two nearest
     * ({@code margin}), so it ends smoothly instead of being cut off. Each leg bends by up to
     * spurBend times its reach sideways, so neighbouring legs are not parallel.
     */
    private double spurHeight(int ridge, double along, double v, double dist, double half, double scale, double margin) {
        if (s.spurChance <= 0) return Double.NEGATIVE_INFINITY;
        double hw = s.halfWidth(), reach = s.spurReach * hw, fadeFrom = s.spurFadeStart * hw;
        // along and v are measured to the spine's whole line (so a leg keeps its shape past the
        // spine's end); the fade uses the true distance to the spine
        if (dist >= reach) return Double.NEGATIVE_INFINITY;
        double fade = 1;
        if (dist > fadeFrom) { double t = (dist - fadeFrom) / (reach - fadeFrom); fade = 1 - t * t * (3 - 2 * t); }
        if (margin < s.spurHandover) {
            if (margin <= 0) return Double.NEGATIVE_INFINITY;
            double t = margin / s.spurHandover;
            fade *= t * t * (3 - 2 * t);
        }
        long i0 = Math.round(along / s.peakSpacing);
        int span = (int) Math.ceil(reach / s.peakSpacing) + 1;
        double best = Double.NEGATIVE_INFINITY;
        for (long i = i0 - span; i <= i0 + span; i++) {
            long h0 = Hash.hash(spurSeed, ridge, i);
            if (Hash.unit(h0) >= s.spurChance) continue;
            double a = crest.summitAlong(ridge, i);
            if (Math.abs(a) > half) continue;             // only summits that are on the spine
            double top = crest.summitHeight(ridge, i);
            double deg = s.spurAngleMin + Hash.unit(Hash.hash(h0, 1)) * (s.spurAngleMax - s.spurAngleMin);
            double ang = Math.toRadians(Hash.unit(Hash.hash(h0, 2)) < 0.5 ? -deg : deg);
            double sn = Math.sin(ang), cs = Math.cos(ang);
            double u = along - a;
            double run = u * sn + v * cs;                 // distance along the leg from the summit
            if (run < 0) continue;
            // each leg bends gently one way or the other as it runs out, so no two are parallel
            double bend = Hash.signed(Hash.hash(h0, 4)) * s.spurBend * run * run / reach;
            double w = Math.abs(u * cs - v * sn - bend);  // distance off the leg's (curved) crest line
            double period = s.spurPeakSpacing * (0.8 + 0.4 * Hash.unit(Hash.hash(h0, 3)));
            double f = run / period;
            double notch = s.spurNotchDepth * 0.5 * (1 - Math.cos(2 * Math.PI * f));   // 0 at each little peak
            double hh = top - s.spurFallSlope * run - notch - s.spurSideSlope * w;
            double rel = (hh - s.valleyFloorHeight) * fade * scale;
            if (rel <= 0) continue;
            double out = s.valleyFloorHeight + rel;
            if (out > best) best = out;
        }
        return best;
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
    /** Relief multiplier along a spine: 1, falling to 1 - spineEndDrop at a free end over spineTaperLength blocks. */
    private double taper(double along, double half, boolean leafLo, boolean leafHi) {
        double k = 1;
        if (leafLo) k = Math.min(k, endTaper(along + half));
        if (leafHi) k = Math.min(k, endTaper(half - along));
        return k;
    }

    private double endTaper(double fromEnd) {
        if (fromEnd >= s.spineTaperLength) return 1;
        double t = Math.max(0, fromEnd) / s.spineTaperLength;
        return 1 - s.spineEndDrop * (1 - t * t * (3 - 2 * t));
    }

    private double junctionCrest(double other, int ridge, int otherRidge) {
        if (s.junctionPeakBoost <= 0 || otherRidge == ridge) return Double.NEGATIVE_INFINITY;
        // each joint gets its own boost (0.1 to 1.5 of the setting), the same from both spines,
        // so the summits on the elbows are not all one height
        long j = Hash.hash(seed, Math.min(ridge, otherRidge), Math.max(ridge, otherRidge));
        double apex = s.summitHeight + s.junctionPeakBoost * (0.1 + 1.4 * Hash.unit(j)) * s.relief();
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
        c = s.valleyFloorHeight + (c - s.valleyFloorHeight) * scratch.scale * scratch.rel * taper(scratch.along, scratch.half, scratch.leafLo, scratch.leafHi);
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
