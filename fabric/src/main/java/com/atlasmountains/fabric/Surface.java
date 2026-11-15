package com.atlasmountains.fabric;

import java.util.function.DoubleSupplier;

/**
 * What lies on the mountains, in plain Java: where the snow is and how dark the rock is. The
 * game's rules (PeakSnowCondition, PeakRockRule) and the preview tool both ask here, so the
 * preview draws what the game will.
 *
 * <h2>Why (0.6.0)</h2>
 * Minecraft has no sun: a face is lit by which way its blocks' sides point and nothing else, so a
 * smooth face of one rock looks the same whether it is a rib or a hollow. In Sammi's reference
 * pictures the shape is read from dark steep rock standing out of snow that lies on the gentler
 * ground and in the couloirs. Up to 0.5.13 the snow was a cap by height with a ragged edge and
 * the rock went from dark to light by height alone, so the ridges and couloirs that 0.5.12 cut
 * could hardly be seen. Now both follow the shape of the ground:
 * <ul>
 *   <li>snow lies much lower in hollows and couloirs (tongues), and is shed by steep ground and
 *       by the crests of ribs, which stand bare well above the snow line;</li>
 *   <li>the rock is darker where it is steep (walls, cliffs, the sides of ribs) and lighter on
 *       the floors of hollows, on top of the dark-to-light gradient by height he chose.</li>
 * </ul>
 */
final class Surface {
    private Surface() {}

    // ---- the shape of the ground at a column ----
    /** Rings the ground is compared with to find hollows and ribs: 20 blocks out (couloirs) and 44 (cirques, valleys). */
    private static final int[] NEAR_X = {20, 14, 0, -14, -20, -14, 0, 14}, NEAR_Z = {0, 14, 20, 14, 0, -14, -20, -14};
    private static final int[] WIDE_X = {44, 31, 0, -31, -44, -31, 0, 31}, WIDE_Z = {0, 31, 44, 31, 0, -31, -44, -31};
    static final int RELIEF = 4, CARVE = 5, SLOPE = 6, HOLLOW = 7;
    private static final ThreadLocal<double[]> COLUMN = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0, 0, 0, 0, 0});
    private static final ThreadLocal<double[]> OUT = ThreadLocal.withInitial(() -> new double[2]);

    /**
     * The mountain at a column: [RELIEF] its height above the land, [CARVE] how deep the couloir
     * cut is here, [SLOPE] how steep (rise over run), [HOLLOW] how far the ground lies below the
     * ground round about (above zero in a hollow, below on a rib or crest). Remembers the last
     * column per thread; the array is reused, read it and do not keep it.
     */
    static double[] shape(HornsFunction horns, int x, int z) {
        double[] c = COLUMN.get();
        double world = AtlasMountainsMod.seed(), self = System.identityHashCode(horns);
        if (c[0] == x && c[1] == z && c[2] == world && c[3] == self) return c;
        double[] o = OUT.get();
        ErodedPeaks.sample(horns, x, z, o);
        double here = o[0], carve = o[1];
        double slope = 0, hollow = 0;
        if (here > 0) {
            ErodedPeaks.sample(horns, x + 3, z, o); double e = o[0];
            ErodedPeaks.sample(horns, x - 3, z, o); double w = o[0];
            ErodedPeaks.sample(horns, x, z + 3, o); double s = o[0];
            ErodedPeaks.sample(horns, x, z - 3, o); double n = o[0];
            slope = Math.hypot(e - w, s - n) / 6.0;
            double near = 0, wide = 0;
            for (int i = 0; i < 8; i++) { ErodedPeaks.sample(horns, x + NEAR_X[i], z + NEAR_Z[i], o); near += o[0]; }
            for (int i = 0; i < 8; i++) { ErodedPeaks.sample(horns, x + WIDE_X[i], z + WIDE_Z[i], o); wide += o[0]; }
            near = near / 8 - here; wide = wide / 8 - here;
            // a couloir counts in full; a wide hollow for less (on an even slope both are nothing)
            hollow = Math.abs(near) >= Math.abs(WIDE_SHARE * wide) || near * wide < 0 ? near : WIDE_SHARE * wide;
            if (near > 0 && wide > 0) hollow = Math.max(near, WIDE_SHARE * wide);
        }
        c[0] = x; c[1] = z; c[2] = world; c[3] = self;
        c[RELIEF] = here; c[CARVE] = carve; c[SLOPE] = slope; c[HOLLOW] = hollow;
        return c;
    }
    static double WIDE_SHARE = 0.6;

    // ---- snow ----
    /** Each block a hollow lies below its surroundings lowers the snow line by this much there, up to HOLLOW_MAX blocks of hollow. */
    static double HOLLOW_GAIN = 1.5, HOLLOW_MAX = 16;
    /** A rib or crest standing proud loses snow the same way, up to RIB_MAX blocks of standing proud. */
    static double RIB_GAIN = 2.5, RIB_MAX = 9;
    /** A couloir's own cut counts as well: this many blocks lower at COULOIR_FULL blocks of cut. */
    static double COULOIR = 10, COULOIR_FULL = 14;
    /**
     * Cliffs shed snow: nothing lost at STEEP_FROM (rise over run), STEEP_LOSS blocks at STEEP_FULL. The
     * carved upper part of a mountain stands at 1.9 nearly everywhere (Massif.RIDGE_SLOPE: the rock rises
     * from every couloir at that angle), so steepness cannot tell a couloir from a rib there, and a first
     * try that shed snow from 1.15 left the whole upper mountain bare. Ribs and couloirs are told apart
     * by the hollow measure above; only ground steeper than the carving itself counts as a cliff.
     */
    static double STEEP_FROM = 1.95, STEEP_FULL = 2.6, STEEP_LOSS = 45;
    /** Vanilla's temperature under which a place is frozen country (in full, and from where it begins), and how far over the snow line all ground counts as being there. */
    static double FROZEN_FULL = -0.52, FROZEN_FROM = -0.42, FROZEN_ABOVE = 12;
    static double SNOW_LOWER = 35, SMALL = 0.35, FULL_SIZE = 170, SNOW_UP = 0.4, UP_LOSS = 22;
    /** Below this far under the snow line nothing can be snow whatever the ground: skip the work. */
    static double REACH = 90;

    /** Whether the block at (x, y, z) is snow. edgeNoise and patchNoise as PeakSnowCondition's (about -1 to 1). */
    static boolean snow(HornsFunction horns, int x, int y, int z, double edge, double fringe, DoubleSupplier edgeNoise, DoubleSupplier patchNoise) {
        double line = SnowLine.at(horns, x, z);
        if (Double.isNaN(line)) return false;
        // SNOW_LOWER: snow can lie this far below the range's snow line where the ground holds it (hollows,
        // couloirs, gentle ground); the line itself stays where it was for the rock's dark band and the cold
        // biome. Less on small mountains (in proportion, down to SMALL of it), and not on the lower part of
        // any mountain: below SNOW_UP of the way up it, snow is lost at UP_LOSS blocks for each tenth.
        // (Without these two, half the snow of a test square lay in the hollows at the feet of small peaks.)
        double size = Math.max(SMALL, Math.min(1, SnowLine.stature(horns, x, z) / FULL_SIZE));
        double above = y - line + SNOW_LOWER * size;
        if (above < -REACH) return false;
        double[] s = shape(horns, x, z);
        double up = ErodedPeaks.up(horns, x, z);
        if (up < SNOW_UP) above -= UP_LOSS * (SNOW_UP - up) * 10;
        // In vanilla's frozen country (temperature under FROZEN_FULL; snowy plains, frozen rivers) the ground all
        // round is white, and so is every part of a mountain that can hold snow, down to its foot: only steep
        // rock and the crests of ribs stand out dark. (Up to 0.5.13 a mountain there had a cap of snow on top and
        // bare rock and green grass below it, in a white landscape.) There every column counts as at least
        // FROZEN_ABOVE blocks over the line, whatever its height.
        double frozen = 1 - ramp(SnowLine.temperature(horns, x, z), FROZEN_FULL, FROZEN_FROM);
        if (frozen > 0 && above < FROZEN_ABOVE) above += (FROZEN_ABOVE - above) * frozen;
        above += edge * Math.max(-1.5, Math.min(1.5, 2 * edgeNoise.getAsDouble()));
        double hollow = s[HOLLOW];
        above += hollow >= 0 ? size * HOLLOW_GAIN * Math.min(HOLLOW_MAX, hollow) : -RIB_GAIN * Math.min(RIB_MAX, -hollow);
        above += size * COULOIR * Math.max(0, Math.min(1, s[CARVE] / COULOIR_FULL));
        above -= STEEP_LOSS * ramp(s[SLOPE], STEEP_FROM, STEEP_FULL);
        if (above < 0) return false;
        if (above >= fringe) return true;
        // patches: about a quarter covered right at the line, filling in with height
        return patchNoise.getAsDouble() > 0.25 - 0.85 * above / fringe;
    }

    // ---- rock ----
    /**
     * The rock's place on PeakRockRule's light-to-dark scale (0 lightest, 100 darkest) moves with the
     * shape of the ground: RIB_DARK points darker on the crest of a rib standing RIB_MAX blocks proud,
     * HOLLOW_LIGHT lighter on the floor of a hollow HOLLOW_LIGHT_FULL deep, CLIFF_DARK darker on a cliff
     * (steeper than the carving, as for the snow), and ROCK_BASE everywhere (the snow line, from which
     * the dark band hangs, lies lower since 0.6.0; this keeps the body of a mountain mid-grey).
     */
    static double RIB_DARK = 24, HOLLOW_LIGHT = 18, HOLLOW_LIGHT_FULL = 14, CLIFF_DARK = 25, ROCK_BASE = -8;

    /** How far the rock at a column moves along PeakRockRule's light-to-dark scale for the shape of the ground. */
    static double rockShift(HornsFunction horns, int x, int z) {
        double[] s = shape(horns, x, z);
        if (s[RELIEF] <= 0) return 0;
        double hollow = s[HOLLOW];
        return ROCK_BASE + CLIFF_DARK * ramp(s[SLOPE], STEEP_FROM, STEEP_FULL)
                + (hollow < 0 ? RIB_DARK * Math.min(1, -hollow / RIB_MAX) : -HOLLOW_LIGHT * Math.min(1, hollow / HOLLOW_LIGHT_FULL));
    }

    /**
     * Which of PeakRockRule's stops the rock is: at[] the place on the scale at which each stop is
     * pure, h the block's place, mottle (-1 to 1) and jitter (0 to 1) the two rolls.
     */
    static int rockStop(int[] at, double h, double blendWidth, double mottle, double jitter) {
        int n = at.length;
        if (n == 1 || h <= at[0]) return 0;
        if (h >= at[n - 1]) return n - 1;
        int k = 0;
        while (k < n - 2 && h >= at[k + 1]) k++;
        double t = (h - at[k]) / Math.max(1, at[k + 1] - at[k]);
        t = Math.max(0, Math.min(1, (t - 0.5) / Math.max(1e-3, blendWidth) + 0.5));
        double roll = 0.5 + 0.5 * (mottle * 0.5 + (jitter * 2 - 1) * 0.5);
        return roll < t ? k + 1 : k;
    }

    private static double ramp(double v, double from, double to) {
        double t = Math.max(0, Math.min(1, (v - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
