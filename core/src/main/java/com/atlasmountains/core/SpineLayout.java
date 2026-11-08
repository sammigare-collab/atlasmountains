package com.atlasmountains.core;

/**
 * Where the range spines are. Two implementations: {@link RangeLayout} (parallel spines over
 * the whole world, one seed-chosen direction; the "everywhere" preset) and {@link ChainLayout}
 * (separate mountain chains, each with its own direction, that may branch; the mixed preset).
 * The crest and cross profiles only ever see a {@link RangeLayout.Sample}, so the mountain
 * design is the same whichever layout is used.
 */
public interface SpineLayout {

    /** Nearest spine to (x, z): its id, the distance to it, the position along it, and the distance to the next spine. */
    void sample(double x, double z, RangeLayout.Sample out);

    /** Factor applied to the height of the summit at {@code along} on spine {@code range}. 1 everywhere by default. */
    default double summitScale(int range, double along) { return 1.0; }

    /**
     * Zone mask 0..1 (no coast fade): 1 well inside a mountain chain, 0 far from any. Layouts
     * with no notion of zones return NaN and the caller uses its own zone noise.
     */
    default double zone(double x, double z) { return Double.NaN; }
}
