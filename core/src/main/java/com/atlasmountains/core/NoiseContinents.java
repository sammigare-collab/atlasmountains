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
    /** One seed per octave, so octaves are independent noises rather than rescaled copies. */
    private final long[] octaveSeeds;
    /** Sum of the octave amplitudes, so the summed noise stays in about -1..1 whatever the gain. */
    private final double norm;
    /** Seed of the noise that bends the guaranteed spawn island's outline. */
    private final long domeSeed;

    public NoiseContinents(MountainSettings settings, long seed) {
        this.s = settings;
        long base = Hash.hash(seed, 4040);
        domeSeed = Hash.hash(base, 99);
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
        // spawn island: a smoothstep bump, continentSpawnBoost at the origin, 0 at continentSpawnRadius
        double r = Math.sqrt(x * x + z * z) / s.continentSpawnRadius;
        if (r < 1) {
            double t = 1 - r;
            n += s.continentSpawnBoost * t * t * (3 - 2 * t);
        }
        return n;
    }

    /**
     * Noise above the land threshold: positive on land, negative at sea. Within
     * continentSpawnGuaranteedRadius of the origin the value is at least a dome that is land
     * everywhere inside, so spawn is always on an island even when the noise there is deep
     * ocean; the additive spawn boost in {@link #noise} shapes it beyond that.
     */
    public double landValue(double x, double z) {
        double v = noise(x, z) - s.continentLandThreshold;
        // the dome's radius varies with direction (two octaves of noise), so the guaranteed island is a blob, not a disc
        double warp = 1 + 0.45 * (Hash.value2(domeSeed, x / 2800 + 7.1, z / 2800 - 3.3)
                                + 0.5 * Hash.value2(domeSeed + 1, x / 1200 - 2.9, z / 1200 + 5.7));
        double r = Math.sqrt(x * x + z * z) / (s.continentSpawnGuaranteedRadius * Math.max(0.35, warp));
        if (r < 1) {
            double dome = s.continentSpawnGuaranteedValue * (1 - r * r);
            if (dome > v) v = dome;
        }
        return v;
    }

    /**
     * Approximate signed distance to the shoreline in blocks: the land value divided by the
     * local gradient of the noise (finite differences). Normalising by the gradient keeps the
     * beach band and the mountain coast fade a fixed number of blocks wide whether the coast is
     * steep or gentle in noise terms; the raw noise would give kilometre-wide beaches where the
     * noise is flat.
     */
    public double signedDistance(double x, double z) {
        double h = 48;   // finite-difference step: small against the continent scale, large against noise jitter
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
