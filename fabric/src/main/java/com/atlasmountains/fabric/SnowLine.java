package com.atlasmountains.fabric;

/**
 * Where the snow starts at a column: the range's own snow line ({@link HornsFunction#snowLine}),
 * moved by the climate. In vanilla's warm bands it is WARM_RAISE higher, so only the very tallest
 * summits there get a dusting; in its coldest band it is COLD_DROP lower. Between bands it moves
 * smoothly, so there is no step in the snow where the climate changes.
 */
final class SnowLine {
    /** Vanilla temperature at which the line starts to rise, and where it has risen fully. */
    static final double WARM_FROM = 0.45, WARM_FULL = 0.65, WARM_RAISE = 30.0;
    /** Vanilla temperature at which the line has dropped fully, and where it starts to. */
    static final double COLD_FULL = -0.55, COLD_FROM = -0.35, COLD_DROP = 12.0;

    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, Double.NaN, 0, 0, 0});
    private static final ThreadLocal<double[]> HEIGHT = ThreadLocal.withInitial(() -> new double[1]);

    private SnowLine() {
    }

    /** The height (y) the snow starts at here, or NaN where there are no peaks about. */
    static double at(HornsFunction horns, int x, int z) {
        double[] last = LAST.get();
        double world = AtlasMountainsMod.seed(), self = System.identityHashCode(horns);
        if (last[0] == x && last[1] == z && last[3] == world && last[4] == self) return last[2];
        double[] height = HEIGHT.get();
        double line = horns.snowLine(x, z, height);
        last[5] = height[0];
        if (!Double.isNaN(line)) {
            double t = PeakStrength.temperature(x, z);
            line += WARM_RAISE * ramp(t, WARM_FROM, WARM_FULL) - COLD_DROP * (1 - ramp(t, COLD_FULL, COLD_FROM));
        }
        last[0] = x; last[1] = z; last[2] = line; last[3] = world; last[4] = self;
        return line;
    }

    /** The height (blocks, foot to summit) of the peaks round a column, blended as the snow line is. Call after at(). */
    static double peakHeight(HornsFunction horns, int x, int z) {
        at(horns, x, z);
        return LAST.get()[5];
    }

    private static double ramp(double v, double from, double to) {
        double t = Math.max(0, Math.min(1, (v - from) / (to - from)));
        return t * t * (3 - 2 * t);
    }
}
