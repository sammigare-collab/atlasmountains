package com.atlasmountains.fabric;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 0.6.2: answers kept between one opening of a world and the next.
 * <p>
 * Every time a world is opened the game places its 128 strongholds, and for each it looks at the biome of some
 * thousands of columns round the site. The biome on a mountain depends on the mountain, so every region with a
 * mountain near a site was eroded, each time: 80 regions and 81 seconds on the dev server before the first
 * stronghold could be found in a world that already existed (Sammi's seed 2089824661498644259, 0.6.1), for mountains
 * nobody was near. Now the answers given to single columns, in regions no chunk has yet been made from, are kept in
 * the world's folder (atlasmountains/columns.bin) and given again next time without eroding anything: 2 seconds.
 * They are the same numbers: what is kept is exactly what was worked out, and only a question about a single column
 * is ever answered from here, never a chunk being made. Only the first opening of a world pays.
 */
final class KeptColumns {
    private KeptColumns() {}

    /** Per thread: above zero while a single column is being asked about (ColumnFunction.sampleValue), not a chunk being made. */
    static final class Asking { int single; }
    static final ThreadLocal<Asking> ASKING = ThreadLocal.withInitial(Asking::new);
    static boolean single() { return ASKING.get().single > 0; }

    /** Column to {the four values of horns, the cover}; NaN where one of them has not been asked for. */
    private static final ConcurrentHashMap<Long, double[]> KEPT = new ConcurrentHashMap<>();
    private static volatile boolean changed;
    private static volatile java.nio.file.Path file;
    private static volatile long stamp;
    static final int MAX = 400_000;
    /** For testing: with a file /tmp/atlas_nokeeping nothing is kept or read back, as before 0.6.2. */
    static final boolean OFF = new java.io.File("/tmp/atlas_nokeeping").exists();

    private static long columnKey(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }

    /** The kept answer for a column, if a single column is being asked about and there is one. Read it, do not change it. */
    static double[] kept(int x, int z) {
        if (OFF || KEPT.isEmpty() || !single()) return null;
        return KEPT.get(columnKey(x, z));
    }

    /** Keeps an answer for a column: the four values of horns (or null) and the cover (or NaN). */
    static void keep(HornsFunction horns, int x, int z, double[] four, double cover) {
        if (OFF || file == null || !single() || !PeakStrength.ready() || KEPT.size() >= MAX) return;
        if (!ErodedPeaks.keepable(horns, x, z)) return;
        KEPT.compute(columnKey(x, z), (k, old) -> {
            double[] v = old != null ? old.clone() : new double[]{Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN};
            if (four != null) System.arraycopy(four, 0, v, 0, 4);
            if (!Double.isNaN(cover)) v[4] = cover;
            return v;
        });
        changed = true;
    }

    /**
     * What the kept answers were worked out for: another seed, another version of the mod, other settings of the peaks
     * or another set of worldgen files (data packs change the land the mountains stand on), and they are thrown away.
     */
    private static long stampNow(net.minecraft.server.MinecraftServer server) {
        String version = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(AtlasMountainsMod.MOD_ID)
                .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("?");
        HornsFunction horns = HornsFunction.current;
        long packs = 0;
        try { for (String id : server.getPackRepository().getSelectedIds()) packs = packs * 31 + id.hashCode(); } catch (Throwable ignored) {}
        return AtlasMountainsMod.seed() * 31 + version.hashCode() * 1000003L + (horns == null ? 0 : horns.hashCode()) * 7919L + packs * 104729L;
    }

    /** As a world opens: reads back the answers kept for it, if they were kept by this version for this seed. Null as it closes. */
    static void open(net.minecraft.server.MinecraftServer server) {
        KEPT.clear(); changed = false; file = null;
        if (server == null || OFF) return;
        try {
            java.nio.file.Path f = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID).resolve("columns.bin");
            stamp = stampNow(server);
            file = f;
            if (!java.nio.file.Files.exists(f)) return;
            try (java.io.DataInputStream in = new java.io.DataInputStream(new java.io.BufferedInputStream(new java.util.zip.GZIPInputStream(java.nio.file.Files.newInputStream(f))))) {
                if (in.readInt() != 0x41544C43 || in.readInt() != 1 || in.readLong() != stamp) {
                    AtlasMountainsMod.LOGGER.info("the kept column answers are for other settings; they will be worked out again");
                    return;
                }
                int count = in.readInt();
                for (int i = 0; i < count && i < MAX; i++) {
                    long key = in.readLong();
                    double[] v = new double[5];
                    for (int k = 0; k < 5; k++) v[k] = in.readDouble();
                    KEPT.put(key, v);
                }
            }
            AtlasMountainsMod.LOGGER.info("read back {} kept column answers", KEPT.size());
        } catch (Exception e) {
            KEPT.clear();
            AtlasMountainsMod.LOGGER.warn("could not read the kept column answers ({}); they will be worked out again", e.toString());
        }
    }

    /** Writes the kept answers to the world's folder, if any are new. */
    static void save() {
        java.nio.file.Path f = file;
        if (f == null || !changed || OFF) return;
        changed = false;
        try {
            java.nio.file.Files.createDirectories(f.getParent());
            java.nio.file.Path tmp = f.resolveSibling("columns.bin.tmp");
            java.util.List<java.util.Map.Entry<Long, double[]>> all = new java.util.ArrayList<>(KEPT.entrySet());
            try (java.io.DataOutputStream out = new java.io.DataOutputStream(new java.io.BufferedOutputStream(new java.util.zip.GZIPOutputStream(java.nio.file.Files.newOutputStream(tmp))))) {
                out.writeInt(0x41544C43); out.writeInt(1); out.writeLong(stamp); out.writeInt(all.size());
                for (java.util.Map.Entry<Long, double[]> e : all) {
                    out.writeLong(e.getKey());
                    for (double d : e.getValue()) out.writeDouble(d);
                }
            }
            java.nio.file.Files.move(tmp, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            AtlasMountainsMod.LOGGER.info("kept {} column answers for the next opening of this world", all.size());
        } catch (Exception e) {
            changed = true;
            AtlasMountainsMod.LOGGER.warn("could not write the kept column answers ({})", e.toString());
        }
    }
}
