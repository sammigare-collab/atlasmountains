package com.atlasmountains.fabric;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Draws the mountains of a square of the world without Minecraft, in the snapshot format of
 * /atlasmountains debug snapshot, so tools/render_snapshot.py renders it.
 *
 *   java ... Preview <label> <seed> <x0> <z0> <size> <out stem> [key=value ...]
 *
 * Runs the mod's own ErodedPeaks, Massif, SnowLine and Surface, unchanged: the shape, where the
 * snow lies, the bands of growth and how dark the rock is are the game's. key=value sets a
 * setting of Massif, Surface or the stand-in PeakStrength by name for this run (e.g. K=0.03).
 * NOT the game's unless REAL=<dir>,<x>,<z>,<size> names maps written by tools/dev/snap5.sh: the land, where
 * mountain country is and the temperature (with REAL they are that world's, and so are the peaks).
 * Never the game's: rivers (none), vanilla's own 3-D roughness, trees and surface, and the noises of the
 * surface rules (stand-ins of the same size).
 */
public final class Preview {
    static final String[] NAMES = {"grass_block", "oak_leaves", "snow_block", "andesite", "stone", "tuff", "deepslate", "smooth_basalt", "vanilla_land"};
    /** The rock stops of tools/build_overhaul.py (ROCK), and its span and blend. */
    static final int[] ROCK_AT = {5, 30, 62, 86, 95};
    static final double ROCK_SPAN = 330, ROCK_BLEND = 0.6;

    public static void main(String[] a) throws Exception {
        String label = a[0];
        AtlasMountainsMod.SEED = Long.parseLong(a[1]);
        int x0 = Integer.parseInt(a[2]), z0 = Integer.parseInt(a[3]), size = Integer.parseInt(a[4]);
        Path out = Paths.get(a[5]);
        for (int i = 6; i < a.length; i++) {
            if (a[i].startsWith("REAL=")) { String[] r = a[i].substring(5).split(","); PeakStrength.loadReal(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]), Integer.parseInt(r[3])); }
            else set(a[i]);
        }
        HornsFunction horns = HornsFunction.game();
        HornsFunction.current = horns;
        long seed = AtlasMountainsMod.seed();
        long t0 = System.nanoTime();
        float[] relief = new float[size * size];
        double[] o = new double[2];
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            ErodedPeaks.sample(horns, x0 + x, z0 + z, o);
            relief[z * size + x] = (float) Math.min(o[0], PeakStrength.riverCap(x0 + x, z0 + z));   // the data pack's cap near rivers
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;
        short[] data = new short[2 * size * size];
        int[] counts = new int[NAMES.length];
        int land = 0;
        for (int z = 0; z < size; z++) for (int x = 0; x < size; x++) {
            int i = z * size + x, bx = x0 + x, bz = z0 + z;
            int y = (int) Math.floor(PeakStrength.groundY(bx, bz) + relief[i]);
            int id = 8;                                                      // vanilla's own land, untouched
            if (relief[i] >= 2) {
                land++;
                double cover = ErodedPeaks.cover(horns, bx, bz);
                if (cover == 0) id = 1;                                       // vanilla's own surface and biome: trees
                else if (Surface.snow(horns, bx, y, bz, 9, 12,
                        () -> HornsFunction.valueNoise(bx / 40.0, bz / 40.0, seed + 31),
                        () -> HornsFunction.valueNoise(bx / 5.0, bz / 5.0, seed + 32))) id = 2;
                else if (cover == 1) id = 0;                                  // the open-grass band
                else {
                    double cap = SnowLine.at(horns, bx, bz);
                    double zero = cap - ROCK_SPAN * Math.max(0.55, Math.min(1, SnowLine.peakHeight(horns, bx, bz) / 170.0));
                    double h = 100 * (y + 8 * HornsFunction.valueNoise(bx / 90.0, bz / 90.0, seed + 33) - zero) / Math.max(8, cap - zero)
                            + Surface.rockShift(horns, bx, bz);
                    id = 3 + Surface.rockStop(ROCK_AT, h, ROCK_BLEND, HornsFunction.valueNoise(bx / 3.0, bz / 3.0, seed + 34), HornsFunction.hash(bx, bz, seed + 35));
                }
            }
            counts[id]++;
            data[2 * i] = (short) y; data[2 * i + 1] = (short) id;
        }
        ByteBuffer bb = ByteBuffer.allocate(data.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (short s : data) bb.putShort(s);
        String stem = out.toString() + "_" + x0 + "_" + z0;
        Files.write(Paths.get(stem + ".bin"), bb.array());
        Files.writeString(Paths.get(stem + ".txt"), String.join("\n", NAMES));
        StringBuilder sb = new StringBuilder();
        double cell = horns.cell();
        int peaks = 0; double err = 0, worst = 0;
        for (long iz = (long) Math.floor(z0 / cell); iz <= (long) Math.floor((z0 + size) / cell); iz++)
            for (long ix = (long) Math.floor(x0 / cell); ix <= (long) Math.floor((x0 + size) / cell); ix++) {
                ErodedPeaks.Peak p = horns.peakOf(ix, iz, seed);
                if (p == null) continue;
                int px = (int) Math.round(p.x()) - x0, pz = (int) Math.round(p.z()) - z0;
                if (px < 30 || pz < 30 || px >= size - 30 || pz >= size - 30) continue;
                // the summit's height above sea level: set (the land at the summit plus the peak's height) against made (the highest ground within 30 blocks)
                double top = 0, set = PeakStrength.groundY(p.x(), p.z()) + p.h();
                for (int dz = -30; dz <= 30; dz++) for (int dx = -30; dx <= 30; dx++) top = Math.max(top, data[2 * ((pz + dz) * size + px + dx)]);
                peaks++; err += top - set; worst = Math.max(worst, Math.abs(top - set));
                sb.append(String.format("  peak at %d %d: %.0f tall; summit set at y %.0f, made y %.0f%n", (int) p.x(), (int) p.z(), p.h(), set, top));
            }
        int rock = counts[3] + counts[4] + counts[5] + counts[6] + counts[7];
        System.out.printf("%s: %d ms; mountains on %.0f%% of the square; of them rock %.0f%% (dark %.0f%%), snow %.0f%%, open grass %.0f%%, trees %.0f%%; summits %+.1f on average, worst %.0f off (%d peaks)%n",
                label, ms, 100.0 * land / relief.length, pct(rock, land), pct(counts[6] + counts[7], land), pct(counts[2], land),
                pct(counts[0], land), pct(counts[1], land), peaks == 0 ? 0 : err / peaks, worst, peaks);
        System.out.printf("  erosion: %d regions, %d ms each on average, slowest %d ms%n", ErodedPeaks.eroded, ErodedPeaks.eroded == 0 ? 0 : ErodedPeaks.erodedMillis / ErodedPeaks.eroded, ErodedPeaks.slowestBuildMillis);
        System.out.print(sb);
    }

    private static double pct(int c, int of) { return of == 0 ? 0 : 100.0 * c / of; }

    static void set(String kv) throws Exception {
        String[] p = kv.split("=", 2);
        for (Class<?> c : new Class<?>[]{Massif.class, Surface.class, ErodedPeaks.class, PeakStrength.class}) {
            try {
                var f = c.getDeclaredField(p[0]);
                f.setAccessible(true);
                if (f.getType() == boolean.class) f.setBoolean(null, Boolean.parseBoolean(p[1]));
                else if (f.getType() == int.class) f.setInt(null, Integer.parseInt(p[1]));
                else f.setDouble(null, Double.parseDouble(p[1]));
                return;
            } catch (NoSuchFieldException ignored) { }
        }
        throw new IllegalArgumentException("no setting " + p[0]);
    }
}
