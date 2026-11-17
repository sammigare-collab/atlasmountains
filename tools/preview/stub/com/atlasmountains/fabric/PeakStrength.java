package com.atlasmountains.fabric;

/**
 * Preview stand-in for vanilla's land and mountain country. The real one reads vanilla's density
 * functions; here mountain country is a slow noise (some cells full strength, some none, so peaks
 * come in groups as they do in the game) and the land is a gently rolling plain round y 90.
 */
final class PeakStrength {
    static double COUNTRY_LENGTH = 1100, COUNTRY_BIAS = 0.55, COUNTRY_GAIN = 1.1;
    /** Share of cells that keep their peak (1: all). Below 1 gives tall peaks standing apart, as in sparse mountain country in the game. */
    static double THIN = 1;
    /** Scales every peak's strength (below 1: small peaks, as on the edges of mountain country in the game). */
    static double PEAK_SCALE = 1;
    static boolean ready() { return true; }

    // ---- REAL mode: the land, the strength of mountain country and the temperature of a real world, from files written
    // by the dev server (tools/dev/snap5.sh: /atlasmountains debug dfmap ... <size> 4). With these the preview places the
    // same peaks on the same land as the game does for that seed, within the mapped square. Set with REAL=<dir> x0 z0 n.
    private static float[] realLand, realStrength, realTemp, realWall;
    private static int realX0, realZ0, realN, realStep = 4;
    static void loadReal(String dir, int cx, int cz, int size) throws java.io.IOException {
        realN = size / realStep; realX0 = cx - size / 2; realZ0 = cz - size / 2;
        realLand = read(dir, "atlasmountains_overworld_vanilla_offset", cx, cz, size);
        realStrength = read(dir, "atlasmountains_overworld_strength", cx, cz, size);
        realTemp = read(dir, "minecraft_overworld_temperature", cx, cz, size);
        realWall = read(dir, "atlasmountains_overworld_river_wall", cx, cz, size);
        if (realWall != null) RiverWallFunction.current = new RiverWallFunction(0.04);
    }
    private static float[] read(String dir, String name, int cx, int cz, int size) throws java.io.IOException {
        java.nio.file.Path p = java.nio.file.Path.of(dir, "dfmap_" + name + "_" + cx + "_" + cz + "_" + size + "_" + realStep + ".f32");
        if (!java.nio.file.Files.exists(p)) return null;
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(java.nio.file.Files.readAllBytes(p)).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        float[] f = new float[bb.remaining() / 4];
        bb.asFloatBuffer().get(f);
        return f;
    }
    /** The data pack's cap on a mountain's height near a river (blocks above the land), or a large number with no map. */
    static double riverCap(double x, double z) { return realWall == null ? 1e9 : 128 * real(realWall, x, z); }
    /** The distance to the nearest river (blocks), worked back from the cap: cap = 1.73 d + 40 min(1, d / 40). */
    static double riverDistance(double x, double z) {
        if (realWall == null) return 1e9;
        double cap = riverCap(x, z);
        return Math.min(RiverDistance.REACH, cap < 109.2 ? cap / 2.73 : (cap - 40) / 1.73);
    }
    private static double real(float[] f, double x, double z) {
        double gx = Math.max(0, Math.min(realN - 1.001, (x - realX0) / realStep)), gz = Math.max(0, Math.min(realN - 1.001, (z - realZ0) / realStep));
        int ix = (int) gx, iz = (int) gz; double tx = gx - ix, tz = gz - iz; int i = iz * realN + ix;
        return f[i] * (1 - tx) * (1 - tz) + f[i + 1] * tx * (1 - tz) + f[i + realN] * (1 - tx) * tz + f[i + realN + 1] * tx * tz;
    }
    static double at(long ix, long iz, int x, int z) {
        if (realStrength != null) return (x < realX0 || z < realZ0 || x >= realX0 + realN * realStep || z >= realZ0 + realN * realStep) ? 0 : real(realStrength, x, z);
        if (HornsFunction.hash(ix, iz, AtlasMountainsMod.seed() + 9009) > THIN) return 0;
        double n = 0.65 * HornsFunction.valueNoise(x / COUNTRY_LENGTH, z / COUNTRY_LENGTH, AtlasMountainsMod.seed() + 9001)
                 + 0.35 * HornsFunction.valueNoise(x / (COUNTRY_LENGTH / 2.3), z / (COUNTRY_LENGTH / 2.3), AtlasMountainsMod.seed() + 9002);
        return PEAK_SCALE * Math.max(0, Math.min(1, COUNTRY_BIAS + COUNTRY_GAIN * n));
    }
    /**
     * Ground height y at a column. As in the game, the land under mountain country is itself high
     * and hilly (LAND_RISE where the country is at full strength, hills of LAND_HILLS on top):
     * the peaks stand on vanilla's smooth hills, not on a plain. Set both to 0 for the flat plain
     * the preview had up to 0.5.13, which hid that.
     */
    static double LAND_RISE = 35, LAND_HILLS = 60, HILL_LENGTH = 260, DOME = 0, DOME_RADIUS = 190;
    static double groundY(double x, double z) {
        if (realLand != null) return 128 + 128 * real(realLand, x, z);
        long seed = AtlasMountainsMod.seed();
        double country = Math.max(0, Math.min(1, COUNTRY_BIAS - 0.3 + COUNTRY_GAIN * (0.65 * HornsFunction.valueNoise(x / COUNTRY_LENGTH, z / COUNTRY_LENGTH, seed + 9001)
                + 0.35 * HornsFunction.valueNoise(x / (COUNTRY_LENGTH / 2.3), z / (COUNTRY_LENGTH / 2.3), seed + 9002))));
        double hills = Math.max(0, 0.25 + HornsFunction.valueNoise(x / HILL_LENGTH, z / HILL_LENGTH, seed + 9005) + 0.4 * HornsFunction.valueNoise(x / (HILL_LENGTH / 2.1), z / (HILL_LENGTH / 2.1), seed + 9006));
        // a smooth hill under every peak, as vanilla has (its land is highest where its own mountains would stand)
        double dome = 0;
        HornsFunction horns = HornsFunction.current;
        if (DOME > 0 && horns != null) {
            long cx = (long) Math.floor(x / horns.cell()), cz = (long) Math.floor(z / horns.cell());
            for (long iz = cz - 1; iz <= cz + 1; iz++) for (long ix = cx - 1; ix <= cx + 1; ix++) {
                ErodedPeaks.Peak p = horns.peakOf(ix, iz, seed);
                if (p == null) continue;
                double t = Math.hypot(x - p.x() - 25, z - p.z() + 15) / DOME_RADIUS;
                if (t < 1) dome = Math.max(dome, DOME * (1 - t * t) * (1 - t * t));
            }
        }
        return dome + 70 + LAND_RISE * country + LAND_HILLS * country * Math.min(1.2, hills)
                + 5 * HornsFunction.valueNoise(x / 170.0, z / 170.0, seed + 9003) + 2 * HornsFunction.valueNoise(x / 45.0, z / 45.0, seed + 9004);
    }
    static double land(int x, int z) { return (groundY(x, z) - 128) / 128.0; }
    /** The real one tells vanilla's own land from the land with the mod's river cut; here they are the same (no rivers are cut in the preview). */
    static void lands(int x, int z, double[] out) { out[0] = out[1] = land(x, z); }
    static double vanillaLand(int x, int z) { return land(x, z); }
    static double landAt(long ix, long iz, int x, int z) { return land(x, z); }
    static double temperature(int x, int z) { return realTemp != null ? real(realTemp, x, z) : 0.0; }
}
