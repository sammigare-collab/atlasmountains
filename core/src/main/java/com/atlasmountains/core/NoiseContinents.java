package com.atlasmountains.core;

/**
 * Continents from a large-scale fractal noise, in the manner of the Continents mod: most of
 * the map is ocean, land comes as big organic masses and islands. The noise value minus the
 * land threshold is mapped onto vanilla's continentalness scale (deep ocean, ocean, a coast
 * band at the shoreline, near inland, far inland), so vanilla's terrain splines, biome table
 * and spawn search all work unchanged. The origin is raised so spawn is on land.
 */
public final class NoiseContinents implements Continents {

    private final MountainSettings s;
    private final long[] octaveSeeds;
    private final double norm;

    public NoiseContinents(MountainSettings settings, long seed) {
        this.s = settings;
        long base = Hash.hash(seed, 4040);
        octaveSeeds = new long[s.continentOctaves];
        double n = 0, amp = 1;
        for (int o = 0; o < s.continentOctaves; o++) {
            octaveSeeds[o] = Hash.hash(base, o);
            n += amp;
            amp *= s.continentGain;
        }
        norm = n;
    }

    /** Raw shaped noise, roughly -1..1, before the land threshold. */
    public double noise(double x, double z) {
        double sum = 0, amp = 1, freq = 1.0 / s.continentWavelength;
        for (int o = 0; o < octaveSeeds.length; o++) {
            // offset each octave so lattice lines never coincide
            sum += amp * Hash.value2(octaveSeeds[o], x * freq + 31.7 * o, z * freq - 17.3 * o);
            amp *= s.continentGain;
            freq *= 2;
        }
        double n = sum / norm;
        // spawn island
        double r = Math.sqrt(x * x + z * z) / s.continentSpawnRadius;
        if (r < 1) {
            double t = 1 - r;
            n += s.continentSpawnBoost * t * t * (3 - 2 * t);
        }
        return n;
    }

    /** Noise above the land threshold: positive on land, negative at sea. */
    public double landValue(double x, double z) {
        return noise(x, z) - s.continentLandThreshold;
    }

    /**
     * Approximate signed distance to the shoreline in blocks: the land value divided by the
     * local gradient of the noise (finite differences). Normalising by the gradient keeps the
     * beach band and the mountain coast fade a fixed number of blocks wide whether the coast is
     * steep or gentle in noise terms; the raw noise would give kilometre-wide beaches where the
     * noise is flat.
     */
    public double signedDistance(double x, double z) {
        double h = 48;
        double v = landValue(x, z);
        double gx = (landValue(x + h, z) - landValue(x - h, z)) / (2 * h);
        double gz = (landValue(x, z + h) - landValue(x, z - h)) / (2 * h);
        double g = Math.sqrt(gx * gx + gz * gz);
        double gmin = 1.0 / s.continentWavelength;   // gradient floor: never further than a wavelength per noise unit
        return v / Math.max(g, gmin);
    }

    @Override
    public double continentalness(double x, double z) {
        return Continents.fromDistance(s, signedDistance(x, z));
    }
}
