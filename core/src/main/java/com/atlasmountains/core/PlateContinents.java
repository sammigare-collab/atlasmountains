package com.atlasmountains.core;

/**
 * Continents from Voronoi plates. The world is tiled by jittered lattice cells about
 * {@link MountainSettings#plateSizeBlocks} across; each cell is land or sea by a seeded coin
 * (the cell nearest the origin is always land so spawn is dry). The signed distance to the
 * nearest land/sea boundary is estimated from the two nearest cell centres of opposite type,
 * the sample position is domain-warped by a long-wavelength noise so cell edges bend instead
 * of running straight, and a short-wavelength noise is added to the distance for small
 * coastal detail (bays, headlands).
 * <p>
 * {@link #continentalness} maps that distance onto vanilla's continentalness scale, so the
 * vanilla terrain splines, biome table (beach, ocean, deep ocean) and the mountain mask's
 * coast fade all keep working unchanged: deep ocean far from land, ocean, a coast band at the
 * boundary, near inland just behind it, far inland in the plate interiors.
 */
public final class PlateContinents implements Continents {

    private final MountainSettings s;
    private final long seed;
    private final long warpSeedX, warpSeedZ, warpSeedX2, warpSeedZ2, coastSeed, shapeSeed, typeSeed, jitterSeed;

    public PlateContinents(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = Hash.hash(seed, 2020);
        this.warpSeedX = Hash.hash(this.seed, 1);
        this.warpSeedZ = Hash.hash(this.seed, 2);
        this.coastSeed = Hash.hash(this.seed, 3);
        this.typeSeed = Hash.hash(this.seed, 4);
        this.jitterSeed = Hash.hash(this.seed, 5);
        this.warpSeedX2 = Hash.hash(this.seed, 6);
        this.warpSeedZ2 = Hash.hash(this.seed, 7);
        this.shapeSeed = Hash.hash(this.seed, 8);
    }

    /** Two-octave value noise in roughly [-1, 1]. */
    private static double fbm2(long seed, double x, double z) {
        return (Hash.value2(Hash.hash(seed, 0), x, z) + 0.5 * Hash.value2(Hash.hash(seed, 1), x * 2 + 17.3, z * 2 - 9.1)) / 1.5;
    }

    private static double fbm4(long seed, double x, double z) {
        return (Hash.value2(Hash.hash(seed, 0), x, z)
                + 0.5 * Hash.value2(Hash.hash(seed, 1), x * 2 + 17.3, z * 2 - 9.1)
                + 0.25 * Hash.value2(Hash.hash(seed, 2), x * 4 - 5.7, z * 4 + 31.9)
                + 0.125 * Hash.value2(Hash.hash(seed, 3), x * 8 + 2.1, z * 8 - 13.7)) / 1.875;
    }

    private static double fbm3(long seed, double x, double z) {
        return (Hash.value2(Hash.hash(seed, 0), x, z)
                + 0.5 * Hash.value2(Hash.hash(seed, 1), x * 2 + 17.3, z * 2 - 9.1)
                + 0.25 * Hash.value2(Hash.hash(seed, 2), x * 4 - 5.7, z * 4 + 31.9)) / 1.75;
    }

    /** True if lattice cell (cx, cz) is land. */
    public boolean isLand(long cx, long cz) {
        if (cx == 0 && cz == 0) return true;   // spawn plate
        return Hash.unit(Hash.hash(typeSeed, cx, cz)) < s.plateLandFraction;
    }

    /** Distance from (dx, dz) to the centre of plate (cx, cz) in that plate's own stretched metric, so plates are elongated. */
    private double plateDistance(long cx, long cz, double dx, double dz) {
        double angle = Hash.unit(Hash.hash(jitterSeed, cx, cz, 3)) * Math.PI;
        double ratio = 1 + (s.plateStretchMax - 1) * Hash.unit(Hash.hash(jitterSeed, cx, cz, 4));
        double cos = Math.cos(angle), sin = Math.sin(angle);
        double a = (dx * cos + dz * sin) / ratio;   // long axis
        double b = -dx * sin + dz * cos;
        return Math.sqrt(a * a + b * b);
    }

    /** Centre of lattice cell (cx, cz), jittered, in blocks. out[0] = x, out[1] = z. */
    public void centre(long cx, long cz, double[] out) {
        if (cx == 0 && cz == 0) { out[0] = 0; out[1] = 0; return; }   // spawn plate centred on the origin
        double j = s.plateJitter;
        out[0] = (cx + j * Hash.signed(Hash.hash(jitterSeed, cx, cz, 0)) * 0.5) * s.plateSizeBlocks;
        out[1] = (cz + j * Hash.signed(Hash.hash(jitterSeed, cx, cz, 1)) * 0.5) * s.plateSizeBlocks;
    }

    /**
     * Signed distance in blocks to the nearest coastline: positive inland, negative at sea.
     * Approximate (bisector of the two nearest opposite-type centres), continuous, and capped
     * at about 2.5 plate sizes deep in a plate interior.
     */
    public double signedDistance(double x, double z) {
        // two warp layers: a long one that bends whole plate edges, a medium one that breaks
        // the remaining straight runs into lobes and inlets
        double wl = s.plateWarpWavelength, wl2 = s.plateWarp2Wavelength;
        double wx = x + s.plateWarpAmplitude * fbm2(warpSeedX, x / wl, z / wl)
                      + s.plateWarp2Amplitude * fbm2(warpSeedX2, x / wl2, z / wl2);
        double wz = z + s.plateWarpAmplitude * fbm2(warpSeedZ, x / wl + 100.0, z / wl + 100.0)
                      + s.plateWarp2Amplitude * fbm2(warpSeedZ2, x / wl2 + 100.0, z / wl2 + 100.0);

        double size = s.plateSizeBlocks;
        long cx = Math.round(wx / size), cz = Math.round(wz / size);
        double[] c = new double[2];
        // nearest land centre and nearest sea centre, each in its own plate's metric
        double bestLand = Double.MAX_VALUE, bestSea = Double.MAX_VALUE;
        for (long i = cx - 2; i <= cx + 2; i++) {
            for (long k = cz - 2; k <= cz + 2; k++) {
                centre(i, k, c);
                double d = plateDistance(i, k, wx - c[0], wz - c[1]);
                if (isLand(i, k)) { if (d < bestLand) bestLand = d; }
                else if (d < bestSea) bestSea = d;
            }
        }
        double cap = 2.5 * size;
        if (bestLand > cap) bestLand = cap;
        if (bestSea > cap) bestSea = cap;
        // Adjacent land plates merge, so a continent is a cluster of plates; sea plates are
        // open ocean between clusters. Positive inland, negative at sea.
        double sd = 0.5 * (bestSea - bestLand) - s.coastInset;

        // coast shape: a 4-octave noise at the kilometre scale makes bays, peninsulas and
        // offshore islets; the fine noise roughens the shoreline itself
        double sl = s.coastShapeWavelength;
        sd += s.coastShapeAmplitude * fbm4(shapeSeed, x / sl, z / sl);
        double cl = s.coastNoiseWavelength;
        sd += s.coastNoiseAmplitude * fbm3(coastSeed, x / cl, z / cl);
        return sd;
    }

    /** Vanilla-scale continentalness, -1 (deep ocean) .. 0.7 (plate interior). */
    @Override
    public double continentalness(double x, double z) {
        return Continents.fromDistance(s, signedDistance(x, z));
    }
}
