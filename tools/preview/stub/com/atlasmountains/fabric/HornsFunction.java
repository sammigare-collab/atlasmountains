package com.atlasmountains.fabric;

/**
 * Preview stand-in: the parts of the real HornsFunction that the eroded mountains use, copied
 * unchanged (where peaks stand, how tall, the warp, hash and noise, the snow line). Settings as
 * in tools/build_overhaul.py.
 */
record HornsFunction(double cell, double warp, double height, double heightVar, double minHeight) {
    static HornsFunction current;
    private static final double JITTER = 0.7, WARP_LENGTH = 120.0;
    static final boolean ERODED = true;
    static final double SNOW_SHARE = 0.25, SNOW_BLEND_CELLS = 0.6;

    static HornsFunction game() { return new HornsFunction(230.0, 3.0, 172.0, 0.42, 60.0); }

    ErodedPeaks.Peak peakOf(long ix, long iz, long seed) {
        double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * JITTER) * cell;
        double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * JITTER) * cell;
        double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
        h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
        if (h < minHeight) return null;
        return new ErodedPeaks.Peak(sx, sz, h, footOf(ix, iz, seed), ix, iz);
    }
    int footOf(long ix, long iz, long seed) { return (int) Math.floor(hash(ix, iz, seed + 14) * ErodedPeaks.LOOK_NAMES.length); }
    double warpX(double x, double z, long seed) { return x + warp * fbm(x / WARP_LENGTH, z / WARP_LENGTH, seed + 1, 3); }
    double warpZ(double x, double z, long seed) { return z + warp * fbm(x / WARP_LENGTH, z / WARP_LENGTH, seed + 2, 3); }

    static double hash(long ix, long iz, long seed) {
        long n = ix * 0x9E3779B97F4A7C15L + iz * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        n ^= n >>> 29; n *= 0xBF58476D1CE4E5B9L;
        n ^= n >>> 32; n *= 0x94D049BB133111EBL;
        n ^= n >>> 29;
        return (n >>> 11) * 0x1.0p-53;
    }
    static double valueNoise(double x, double z, long seed) {
        double fx0 = Math.floor(x), fz0 = Math.floor(z);
        long ix = (long) fx0, iz = (long) fz0;
        double fx = x - fx0, fz = z - fz0;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * ux + (c - a) * uz + (a - b - c + d) * ux * uz) * 2 - 1;
    }
    static double fbm(double x, double z, long seed, int octaves) {
        double sum = 0, amp = 1, freq = 1, total = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * valueNoise(x * freq, z * freq, seed + 17L * o);
            total += amp; amp *= 0.5; freq *= 2;
        }
        return sum / total;
    }

    /** The real snow line (0.4.9), unchanged. */
    double snowLine(int blockX, int blockZ, double[] heightOut) {
        long seed = AtlasMountainsMod.seed();
        long cx = (long) Math.floor(blockX / cell), cz = (long) Math.floor(blockZ / cell);
        double spread = SNOW_BLEND_CELLS * cell, sum = 0, weight = 0, heights = 0, statures = 0;
        for (long iz = cz - 2; iz <= cz + 2; iz++)
            for (long ix = cx - 2; ix <= cx + 2; ix++) {
                double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * JITTER) * cell;
                double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * JITTER) * cell;
                double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
                h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                if (h < minHeight) continue;
                double top = 128 + 128 * PeakStrength.landAt(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz)) + h;
                double snowH = Math.min(1.6 * h, Massif.stature(this, new ErodedPeaks.Peak(sx, sz, h, 0, ix, iz)));
                double dx = blockX - sx, dz = blockZ - sz, tall = h / height;
                double w = tall * tall * Math.exp(-(dx * dx + dz * dz) / (2 * spread * spread));
                sum += w * (top - SNOW_SHARE * snowH); weight += w; heights += w * h; statures += w * snowH;
            }
        if (weight < 1e-12) return Double.NaN;
        if (heightOut != null) { heightOut[0] = heights / weight; if (heightOut.length > 1) heightOut[1] = statures / weight; }
        return sum / weight;
    }
}
