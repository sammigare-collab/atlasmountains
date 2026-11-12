package com.atlasmountains.fabric;

import java.util.concurrent.ConcurrentHashMap;

/**
 * How far, in blocks, a column is from the nearest river channel: the nearest place where
 * vanilla's weirdness map is within {@code channel} of zero, which is where vanilla runs its
 * rivers. Used by {@link RiverWallFunction} to keep the peaks out of the rivers.
 * <p>
 * Working this out for one column on its own would mean searching hundreds of blocks round it, so
 * it is done a tile at a time and remembered. The weirdness map is read once every CELL blocks.
 * A cell is "channel" if weirdness there is within the channel width of zero, or changes sign
 * between it and the next cell (a river too narrow to land on a sample still counts). The exact
 * distance from every cell of a tile to the nearest channel cell is then found in two passes
 * (down the columns, then along the rows), looking one whole tile out in every direction, and a
 * column's distance is read between the four cells round it. Distances stop at REACH: further
 * from a river than that, the answer is REACH.
 */
final class RiverDistance {
    /** The weirdness map is read once per CELL blocks each way. */
    private static final int CELL = 4;
    /** Cells along one side of a tile (320 blocks). */
    private static final int TILE = 80;
    /** The furthest a river is looked for, in blocks: one tile. */
    static final double REACH = TILE * CELL;
    private static final int MAX_TILES = 600;

    private static final ConcurrentHashMap<Long, float[]> weirdness = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, float[]> distance = new ConcurrentHashMap<>();
    /** The channel width the remembered distances were worked out for. */
    private static volatile double builtFor = Double.NaN;

    private RiverDistance() {
    }

    /** Forgets everything: called when a world starts or stops. */
    static void reset() {
        weirdness.clear();
        distance.clear();
        builtFor = Double.NaN;
    }

    private static long key(int tx, int tz) {
        return ((long) tx << 32) ^ (tz & 0xFFFFFFFFL);
    }

    /** Weirdness at the middle of every cell of a tile, with one extra row and column so each cell can see its neighbours. */
    private static float[] weirdnessTile(int tx, int tz) {
        long k = key(tx, tz);
        float[] have = weirdness.get(k);
        if (have != null) return have;
        int n = TILE + 1;
        float[] w = new float[n * n];
        for (int j = 0; j < n; j++)
            for (int i = 0; i < n; i++)
                w[j * n + i] = PeakStrength.weirdness((tx * TILE + i) * CELL + CELL / 2, (tz * TILE + j) * CELL + CELL / 2);
        if (weirdness.size() > MAX_TILES) weirdness.clear();
        weirdness.put(k, w);
        return w;
    }

    /** Distance, in blocks, from the middle of every cell of a tile to the nearest channel cell, at most REACH. */
    private static float[] distanceTile(int tx, int tz, double channel) {
        long k = key(tx, tz);
        float[] have = distance.get(k);
        if (have != null) return have;
        int big = 3 * TILE, n = TILE + 1;
        boolean[] river = new boolean[big * big];
        for (int dj = 0; dj < 3; dj++)
            for (int di = 0; di < 3; di++) {
                float[] w = weirdnessTile(tx + di - 1, tz + dj - 1);
                for (int j = 0; j < TILE; j++)
                    for (int i = 0; i < TILE; i++) {
                        float here = w[j * n + i];
                        river[(dj * TILE + j) * big + di * TILE + i] = Math.abs(here) < channel
                                || (here > 0) != (w[j * n + i + 1] > 0) || (here > 0) != (w[(j + 1) * n + i] > 0);
                    }
            }
        // pass 1: for every cell, how many cells up or down its own column to a channel cell
        final int far = 4 * big;
        int[] upDown = new int[big * big];
        for (int i = 0; i < big; i++) {
            int since = far;
            for (int j = 0; j < big; j++) {
                since = river[j * big + i] ? 0 : Math.min(far, since + 1);
                upDown[j * big + i] = since;
            }
            since = far;
            for (int j = big - 1; j >= 0; j--) {
                since = river[j * big + i] ? 0 : Math.min(far, since + 1);
                if (since < upDown[j * big + i]) upDown[j * big + i] = since;
            }
        }
        // pass 2: along the row, the nearest of those in a straight line
        float[] out = new float[TILE * TILE];
        for (int j = 0; j < TILE; j++) {
            int row = (TILE + j) * big;
            for (int i = 0; i < TILE; i++) {
                int x = TILE + i;
                long best = (long) TILE * TILE;                       // REACH, in cells, squared
                for (int o = x - TILE; o <= x + TILE; o++) {
                    long v = upDown[row + o], d = (long) (x - o) * (x - o) + v * v;
                    if (d < best) best = d;
                }
                out[j * TILE + i] = (float) (Math.sqrt(best) * CELL);
            }
        }
        if (distance.size() > MAX_TILES) distance.clear();
        distance.put(k, out);
        return out;
    }

    private static float cell(int ci, int cj, double channel) {
        int tx = Math.floorDiv(ci, TILE), tz = Math.floorDiv(cj, TILE);
        return distanceTile(tx, tz, channel)[(cj - tz * TILE) * TILE + (ci - tx * TILE)];
    }

    /** Blocks from this column to the nearest river channel, at most REACH; 0 with no world loaded. */
    static double at(int x, int z, double channel) {
        if (!PeakStrength.ready()) return 0.0;
        if (builtFor != channel) {
            synchronized (RiverDistance.class) {
                if (builtFor != channel) { distance.clear(); builtFor = channel; }
            }
        }
        double fx = (x - CELL / 2.0) / CELL, fz = (z - CELL / 2.0) / CELL;
        int i = (int) Math.floor(fx), j = (int) Math.floor(fz);
        double u = fx - i, v = fz - j;
        double a = cell(i, j, channel), b = cell(i + 1, j, channel), c = cell(i, j + 1, channel), d = cell(i + 1, j + 1, channel);
        return a + (b - a) * u + (c - a) * v + (a - b - c + d) * u * v;
    }
}
