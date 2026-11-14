package com.atlasmountains.fabric;

import java.io.*;
import java.nio.*;
import java.nio.file.*;

/**
 * Draws the mountains of a square of the world without Minecraft, in the snapshot format of
 * /atlasmountains snapshot, so tools/render_snapshot.py renders it.
 *
 *   java ... Preview <current|joined|separate> <seed> <x0> <z0> <size> <out dir> [key=value ...]
 *
 * "current" is the mod's own ErodedPeaks.java, unchanged. "joined" and "separate" are the Massif
 * experiment. key=value sets a Massif or PeakStrength setting by name (e.g. K=0.005).
 * The land is a stand-in plain (PeakStrength in tools/preview/stub), not vanilla's.
 */
public final class Preview {
    static final String[] NAMES = {"grass_block", "oak_leaves", "stone", "snow_block", "gravel"};

    public static void main(String[] a) throws Exception {
        String mode = a[0];
        AtlasMountainsMod.SEED = Long.parseLong(a[1]);
        int x0 = Integer.parseInt(a[2]), z0 = Integer.parseInt(a[3]), size = Integer.parseInt(a[4]);
        Path out = Paths.get(a[5]);
        for (int i = 6; i < a.length; i++) set(a[i]);
        HornsFunction horns = HornsFunction.game();
        HornsFunction.current = horns;
        long t0 = System.nanoTime();
        float[] relief = new float[size * size], carve = new float[size * size];
        Massif massif = mode.equals("current") ? null : new Massif(horns, x0, z0, size, mode.equals("joined"));
        double[] o = new double[2];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int i = z * size + x;
            if (massif == null) {
                ErodedPeaks.sample(horns, x0 + x, z0 + z, o);
                relief[i] = (float) o[0]; carve[i] = (float) o[1];
            } else {
                relief[i] = (float) massif.relief(x0 + x, z0 + z);
                carve[i] = (float) massif.carve(x0 + x, z0 + z);
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        short[] data = new short[2 * size * size];
        int[] counts = new int[NAMES.length];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int i = z * size + x, bx = x0 + x, bz = z0 + z;
            double ground = PeakStrength.groundY(bx, bz);
            double y = Math.floor(ground + relief[i]);
            int id = surface(horns, relief, carve, size, x, z, bx, bz, y);
            counts[id]++;
            data[2 * i] = (short) y; data[2 * i + 1] = (short) id;
        }
        Files.createDirectories(out.getParent() == null ? Paths.get(".") : out.getParent());
        ByteBuffer bb = ByteBuffer.allocate(data.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : data) bb.putShort(s);
        String stem = out.toString() + "_" + x0 + "_" + z0;
        Files.write(Paths.get(stem + ".bin"), bb.array());
        Files.writeString(Paths.get(stem + ".txt"), String.join("\n", NAMES));
        // summits: set height against the highest relief within 12 blocks
        StringBuilder sb = new StringBuilder();
        long seed = AtlasMountainsMod.seed();
        double cell = horns.cell();
        int peaks = 0; double err = 0, worst = 0;
        for (long iz = (long) Math.floor(z0 / cell); iz <= (long) Math.floor((z0 + size) / cell); iz++)
            for (long ix = (long) Math.floor(x0 / cell); ix <= (long) Math.floor((x0 + size) / cell); ix++) {
                ErodedPeaks.Peak p = horns.peakOf(ix, iz, seed);
                if (p == null) continue;
                int px = (int) Math.round(p.x()) - x0, pz = (int) Math.round(p.z()) - z0;
                if (px < 12 || pz < 12 || px >= size - 12 || pz >= size - 12) continue;
                double top = 0;
                for (int dz = -12; dz <= 12; dz++) for (int dx = -12; dx <= 12; dx++) top = Math.max(top, relief[(pz + dz) * size + px + dx]);
                peaks++; err += top - p.h(); worst = Math.max(worst, Math.abs(top - p.h()));
                sb.append(String.format("  peak at %d %d: set %.0f, made %.0f%n", (int) p.x(), (int) p.z(), p.h(), top));
            }
        int land = 0; for (float r : relief) if (r >= 2) land++;
        System.out.printf("%s: %d ms; mountains on %.0f%% of the square; rock %.0f%%, snow %.0f%%, open grass %.0f%%, trees %.0f%% of the mountains; summits %+.1f on average, worst %.0f off (%d peaks)%n",
                mode, ms, 100.0 * land / relief.length, pct(counts[2] + counts[4], land), pct(counts[3], land), pct(counts[0], land, relief, true), pct(counts[1], land), peaks == 0 ? 0 : err / peaks, worst, peaks);
        System.out.print(sb);
        System.out.println("wrote " + stem + ".bin");
    }

    static int openGrassOnMountain;
    private static double pct(int c, int of) { return of == 0 ? 0 : 100.0 * c / of; }
    private static double pct(int c, int of, float[] r, boolean grass) { return of == 0 ? 0 : 100.0 * openGrassOnMountain / of; }

    private static double at(float[] f, int size, int x, int z) {
        x = Math.max(0, Math.min(size - 1, x)); z = Math.max(0, Math.min(size - 1, z));
        return f[z * size + x];
    }

    /** The surface rules of 0.5.3 (ErodedPeaks.cover, snowShift, PeakSnowCondition), read off the arrays. */
    private static int surface(HornsFunction horns, float[] rel, float[] carve, int size, int x, int z, int bx, int bz, double y) {
        long seed = AtlasMountainsMod.seed();
        double r = rel[z * size + x];
        double line = SnowLine.at(horns, bx, bz);
        if (!Double.isNaN(line) && r >= 2) {
            double above = y - line;
            double rib = Math.max(-1, Math.min(1, Math.max(0, carve[z * size + x]) / 12.0));
            above += 9 * Math.max(-1.5, Math.min(1.5, 2 * HornsFunction.valueNoise(bx / 40.0, bz / 40.0, seed + 31))) + 14 * rib;
            double ring = 0;
            int[] rx = {20, 14, 0, -14, -20, -14, 0, 14}, rz = {0, 14, 20, 14, 0, -14, -20, -14};
            for (int k = 0; k < 8; k++) ring += at(rel, size, x + rx[k], z + rz[k]);
            double sl = Math.hypot(at(rel, size, x + 3, z) - at(rel, size, x - 3, z), at(rel, size, x, z + 3) - at(rel, size, x, z - 3)) / 6.0;
            double t = Math.max(0, Math.min(1, (sl - 1.15) / 0.5));
            above += Math.max(-3, Math.min(28, ring / 8 - r)) - 7 * t * t * (3 - 2 * t);
            if (above >= 12 || (above >= 0 && HornsFunction.valueNoise(bx / 5.0, bz / 5.0, seed + 32) > 0.25 - 0.85 * above / 12)) return 3;
        }
        if (r < 2) return 0;
        double tall = SnowLine.peakHeight(horns, bx, bz);
        if (!(tall > 20)) tall = 120;
        double up = r / tall + 0.06 * HornsFunction.valueNoise(bx / 37.0, bz / 37.0, seed + 771);
        if (up >= ErodedPeaks.PATCH_UP) return 2;
        double slope = Math.hypot(at(rel, size, x + 3, z) - at(rel, size, x - 3, z), at(rel, size, x, z + 3) - at(rel, size, x, z - 3)) / 6.0;
        double limit = ErodedPeaks.GRASS_SLOPE * (1 - up / ErodedPeaks.PATCH_UP) + 0.12 * HornsFunction.valueNoise(bx / 19.0, bz / 19.0, seed + 772);
        if (slope >= limit) return 2;
        if (up < ErodedPeaks.TREES_UP) return 1;
        if (up < ErodedPeaks.GRASS_UP) { openGrassOnMountain++; return 0; }
        if (HornsFunction.valueNoise(bx / 6.0, bz / 6.0, seed + 773) > 2 * (up - ErodedPeaks.GRASS_UP) / (ErodedPeaks.PATCH_UP - ErodedPeaks.GRASS_UP) - 1) { openGrassOnMountain++; return 0; }
        return 2;
    }

    private static void set(String kv) throws Exception {
        String[] p = kv.split("=", 2);
        for (Class<?> c : new Class<?>[]{Massif.class, PeakStrength.class}) {
            try {
                var f = c.getDeclaredField(p[0]);
                f.setAccessible(true);
                if (f.getType() == boolean.class) f.setBoolean(null, Boolean.parseBoolean(p[1])); else if (f.getType() == int.class) f.setInt(null, Integer.parseInt(p[1])); else f.setDouble(null, Double.parseDouble(p[1]));
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new IllegalArgumentException("no setting " + p[0]);
    }
}
