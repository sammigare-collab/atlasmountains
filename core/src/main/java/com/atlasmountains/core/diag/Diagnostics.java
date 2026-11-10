package com.atlasmountains.core.diag;

import com.atlasmountains.core.RidgeSample;
import com.atlasmountains.core.HeightmapGenerator;
import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionHeightmap;
import com.atlasmountains.core.RegionPipeline;
import com.atlasmountains.core.SettingsOverrides;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * Standalone PNG diagnostic. Usage: Diagnostics <seed> <regionX> <regionZ> [outDir] [overviewSpan] [raw]
 * Writes hillshade.png, contour.png, slope.png and section.csv for the region, and prints stats.
 * Uses exactly the same generator code as the mod.
 * <p>
 * Settings can be overridden with name=value arguments anywhere on the line (see
 * {@link SettingsOverrides}), which is how a change is previewed before it goes into the mod.
 * The raw (un-eroded) region is always written too, so the effect of erosion can be compared.
 * Every image is one pixel per sample (4 blocks), so a region is 640x640 with the overlap.
 */
public final class Diagnostics {

    /** Command-line entry; see the class comment for the arguments. */
    public static void main(String[] args) throws IOException {
        java.util.List<String> pos = new java.util.ArrayList<>();
        MountainSettings s = new MountainSettings();
        for (String a : args) {
            if (SettingsOverrides.isOverride(a)) SettingsOverrides.apply(s, a); else pos.add(a);
        }
        args = pos.toArray(new String[0]);
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
        int rx = args.length > 1 ? Integer.parseInt(args[1]) : 0;
        int rz = args.length > 2 ? Integer.parseInt(args[2]) : 0;
        File outDir = new File(args.length > 3 ? args[3] : "out/seed_" + seed);
        outDir.mkdirs();

        boolean rawOnly = args.length > 5 && args[5].equals("raw");
        RegionPipeline pipeline = new RegionPipeline(s, seed);
        pipeline.verbose = true;
        HeightmapGenerator gen = pipeline.generator();
        System.out.printf(Locale.UK, "seed %d region %d,%d%n", seed, rx, rz);

        String prefix = "r" + rx + "_" + rz + "_";
        RegionHeightmap raw = pipeline.raw(rx, rz);
        ImageIO.write(hillshade(raw), "png", new File(outDir, prefix + "raw_hillshade.png"));
        writeGrid(raw, new File(outDir, prefix + "raw.f32"));
        System.out.print("raw:    ");
        stats(raw, s);
        if (!rawOnly) {
            RegionHeightmap map = pipeline.eroded(rx, rz);
            ImageIO.write(hillshade(map), "png", new File(outDir, prefix + "hillshade.png"));
            ImageIO.write(contours(map, 20), "png", new File(outDir, prefix + "contour.png"));
            ImageIO.write(slopeMap(map), "png", new File(outDir, prefix + "slope.png"));
            ImageIO.write(zoom(hillshade(map), map, s, 4), "png", new File(outDir, prefix + "zoom_hillshade.png"));
            ImageIO.write(zoom(contours(map, 10), map, s, 4), "png", new File(outDir, prefix + "zoom_contour10.png"));
            crossSection(map, gen, s, new File(outDir, prefix + "section.csv"));
            writeGrid(map, new File(outDir, prefix + "eroded.f32"));
            System.out.print("eroded: ");
            stats(map, s);
        }

        // Overview: span x span regions at 8 blocks per sample, same generator, no overlap.
        int span = args.length > 4 ? Integer.parseInt(args[4]) : 3;
        MountainSettings wide = new MountainSettings();
        wide.regionSizeBlocks = s.regionSizeBlocks * span;
        wide.sampleSpacing = 8;
        wide.overlapSamples = 0;
        RegionHeightmap over = new HeightmapGenerator(wide, seed).generate(0, 0);
        ImageIO.write(hillshade(over), "png", new File(outDir, "overview_raw_" + span + "x" + span + "_hillshade.png"));
        ImageIO.write(slopeMap(over), "png", new File(outDir, "overview_raw_" + span + "x" + span + "_slope.png"));
        ImageIO.write(maskOverview(s, seed, 24000, 40), "png", new File(outDir, "mask_24km.png"));
    }

    // ---- Hillshade: sun from the north-west, 45 degrees elevation ----------------
    static BufferedImage hillshade(RegionHeightmap m) {
        BufferedImage img = new BufferedImage(m.size, m.size, BufferedImage.TYPE_INT_RGB);
        double el = Math.toRadians(45);
        // north is -z; north-west is (-x, -z)
        double lx = -Math.cos(el) / Math.sqrt(2), ly = Math.sin(el), lz = -Math.cos(el) / Math.sqrt(2);
        for (int gz = 0; gz < m.size; gz++) {
            for (int gx = 0; gx < m.size; gx++) {
                double dx = (m.get(Math.min(m.size - 1, gx + 1), gz) - m.get(Math.max(0, gx - 1), gz)) / (2.0 * m.spacing);
                double dz = (m.get(gx, Math.min(m.size - 1, gz + 1)) - m.get(gx, Math.max(0, gz - 1))) / (2.0 * m.spacing);
                double nx = -dx, ny = 1, nz = -dz;
                double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double shade = Math.max(0, (nx * lx + ny * ly + nz * lz) / len);
                int v = (int) Math.round(255 * (0.22 + 0.78 * shade)); // ambient so shadowed faces still show shape
                img.setRGB(gx, gz, (v << 16) | (v << 8) | v);
            }
        }
        return img;
    }

    // ---- Contours: 20-block interval, every fifth line heavier --------------------
    static BufferedImage contours(RegionHeightmap m, int interval) {
        BufferedImage img = new BufferedImage(m.size, m.size, BufferedImage.TYPE_INT_RGB);
        for (int gz = 0; gz < m.size; gz++) {
            for (int gx = 0; gx < m.size; gx++) {
                int level = (int) Math.floor(m.get(gx, gz) / interval);
                int right = (int) Math.floor(m.get(Math.min(m.size - 1, gx + 1), gz) / interval);
                int down = (int) Math.floor(m.get(gx, Math.min(m.size - 1, gz + 1)) / interval);
                int rgb = 0xFFFFFF;
                if (level != right || level != down) {
                    int crossed = Math.max(level, Math.max(right, down));
                    rgb = (crossed % 5 == 0) ? 0x000000 : 0x808080;
                }
                img.setRGB(gx, gz, rgb);
            }
        }
        return img;
    }

    // ---- Slope map: classes with 35 and 45 degree boundaries ----------------------
    /** Slope class colour: under 35 degrees (grass), 35 to 45 (steep), over 45 (rock face). */
    static int slopeColour(double deg) {
        if (deg < 5) return 0x1A4D1A;      // dark green: flat floor
        if (deg < 10) return 0x2E7D32;
        if (deg < 20) return 0x7CB342;
        if (deg < 30) return 0xC0CA33;     // yellow-green: grass flank
        if (deg < 35) return 0xFFEB3B;     // yellow
        if (deg < 40) return 0xFB8C00;     // orange: above the rock/grass line
        if (deg < 45) return 0xE53935;     // red
        if (deg < 50) return 0xB71C1C;     // dark red: face band
        return 0xD500F9;                   // magenta: steeper than 50
    }

    /** One pixel per sample, coloured by slopeColour. */
    static BufferedImage slopeMap(RegionHeightmap m) {
        BufferedImage img = new BufferedImage(m.size, m.size, BufferedImage.TYPE_INT_RGB);
        for (int gz = 0; gz < m.size; gz++) {
            for (int gx = 0; gx < m.size; gx++) {
                img.setRGB(gx, gz, slopeColour(Math.toDegrees(Math.atan(m.slope(gx, gz)))));
            }
        }
        return img;
    }

    /** Crop of 160x160 samples (640 blocks) centred on the highest summit, scaled up with no smoothing. */
    static BufferedImage zoom(BufferedImage src, RegionHeightmap m, MountainSettings s, int scale) {
        int o = s.overlapSamples, n = s.samplesPerRegion();
        int bx = 0, bz = 0; float best = -Float.MAX_VALUE;
        for (int gz = o; gz < o + n; gz++) for (int gx = o; gx < o + n; gx++)
            if (m.get(gx, gz) > best) { best = m.get(gx, gz); bx = gx; bz = gz; }
        int half = 80;
        int x0 = Math.max(0, Math.min(m.size - 2 * half, bx - half));
        int z0 = Math.max(0, Math.min(m.size - 2 * half, bz - half));
        BufferedImage out = new BufferedImage(2 * half * scale, 2 * half * scale, BufferedImage.TYPE_INT_RGB);
        for (int z = 0; z < 2 * half * scale; z++)
            for (int x = 0; x < 2 * half * scale; x++)
                out.setRGB(x, z, src.getRGB(x0 + x / scale, z0 + z / scale));
        return out;
    }

    // ---- Cross-section through the highest summit, across the range ---------------
    static void crossSection(RegionHeightmap m, HeightmapGenerator gen, MountainSettings s, File file) throws IOException {
        int bx = 0, bz = 0;
        float best = -Float.MAX_VALUE;
        int o = s.overlapSamples, n = s.samplesPerRegion();
        for (int gz = o; gz < o + n; gz++) {
            for (int gx = o; gx < o + n; gx++) {
                if (m.get(gx, gz) > best) { best = m.get(gx, gz); bx = gx; bz = gz; }
            }
        }
        // refine apex to block resolution around the best sample using the analytic model
        RidgeSample sc = new RidgeSample();
        double ax = m.originX + bx * (double) m.spacing, az = m.originZ + bz * (double) m.spacing;
        double ah = best;
        double bestX = ax, bestZ = az;
        for (int dz = -m.spacing; dz <= m.spacing; dz++) {
            for (int dx = -m.spacing; dx <= m.spacing; dx++) {
                double h = gen.heightAt(ax + dx, az + dz, sc);
                if (h > ah) { ah = h; bestX = ax + dx; bestZ = az + dz; }
            }
        }
        ax = bestX; az = bestZ;
        // the cross-section runs along x (ridges in the field layout run every way)
        double[] along = {0, 1};
        double cx = along[1], cz = -along[0];
        int half = 1200;
        try (PrintWriter w = new PrintWriter(file)) {
            w.println("offset,height_eroded_bicubic,height_model_uneroded,slope_deg_eroded");
            double prev = Double.NaN;
            for (int d = -half; d <= half; d++) {
                double x = ax + cx * d, z = az + cz * d;
                double hg = m.sample(x, z);
                double ha = gen.heightAt(x, z, sc);
                double slope = Double.isNaN(prev) ? 0 : Math.toDegrees(Math.atan(Math.abs(hg - prev)));
                w.printf(Locale.UK, "%d,%.2f,%.2f,%.1f%n", d, hg, ha, slope);
                prev = hg;
            }
        }
        // apex report
        double apexH = gen.heightAt(ax, az, sc);
        int width = 0;
        for (int d = -10; d <= 10; d++) if (gen.heightAt(ax + cx * d, az + cz * d, sc) >= apexH - 1.0) width++;
        double drop3 = apexH - gen.heightAt(ax + cx * 3, az + cz * 3, sc);
        System.out.printf(Locale.UK, "section: summit sample (%.0f, %.0f) y=%.1f, apex width (within 1 block of top) %d blocks, drop over 3 blocks across = %.1f (%.0f deg)%n",
                ax, az, apexH, width, drop3, Math.toDegrees(Math.atan(drop3 / 3)));
    }

    /** Raw float32 little-endian grid, row-major, size*size, for external analysis. */
    static void writeGrid(RegionHeightmap m, File file) throws IOException {
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocate(m.height.length * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.asFloatBuffer().put(m.height);
        java.nio.file.Files.write(file.toPath(), bb.array());
    }

    /** Mountain mask over a square of the world, no inland ramp (the diagnostic has no vanilla noise). */
    static BufferedImage maskOverview(MountainSettings s, long seed, int sizeBlocks, int blocksPerPixel) {
        int n = sizeBlocks / blocksPerPixel;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        com.atlasmountains.core.RegionCache cache = new com.atlasmountains.core.RegionCache(s, seed, null);
        RidgeSample sc = new RidgeSample();
        for (int pz = 0; pz < n; pz++) {
            for (int px = 0; px < n; px++) {
                double x = (px - n / 2.0) * blocksPerPixel, z = (pz - n / 2.0) * blocksPerPixel;
                double m = cache.mask(x, z);
                double h = cache.generator().baseHeightAt(x, z, sc);
                double t = (h - s.valleyFloorHeight) / s.relief();
                int r = (int) (150 * (1 - m) + m * (120 + 125 * t));
                int g = (int) (165 * (1 - m) + m * (100 + 145 * t));
                int b = (int) (120 * (1 - m) + m * (80 + 170 * t));
                img.setRGB(px, pz, (r << 16) | (g << 8) | b);
            }
        }
        cache.shutdown();
        return img;
    }

    // ---- Numbers ------------------------------------------------------------------
    /** Prints height range, mean and the share of samples in each slope class, for comparing settings by number. */
    static void stats(RegionHeightmap m, MountainSettings s) {
        int o = s.overlapSamples, n = s.samplesPerRegion();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        int[] bands = new int[9];
        int summits = 0, total = 0, floor = 0;
        for (int gz = o; gz < o + n; gz++) {
            for (int gx = o; gx < o + n; gx++) {
                float h = m.get(gx, gz);
                min = Math.min(min, h); max = Math.max(max, h);
                double deg = Math.toDegrees(Math.atan(m.slope(gx, gz)));
                int b = deg < 5 ? 0 : deg < 10 ? 1 : deg < 20 ? 2 : deg < 30 ? 3 : deg < 35 ? 4 : deg < 40 ? 5 : deg < 45 ? 6 : deg < 50 ? 7 : 8;
                bands[b]++;
                total++;
                if (h <= s.valleyFloorHeight + 0.5) floor++;
                boolean peak = h > s.rockLineHeight();
                for (int dz = -1; dz <= 1 && peak; dz++)
                    for (int dx = -1; dx <= 1; dx++)
                        if ((dx != 0 || dz != 0) && m.get(gx + dx, gz + dz) >= h) { peak = false; break; }
                if (peak) summits++;
            }
        }
        System.out.printf(Locale.UK, "height min %.0f max %.0f (relief %.0f); flat floor %.0f%% of area; local summits above rock line: %d%n",
                min, max, max - min, 100.0 * floor / total, summits);
        String[] names = {"<5", "5-10", "10-20", "20-30", "30-35", "35-40", "40-45", "45-50", ">50"};
        StringBuilder sb = new StringBuilder("slope bands (deg, % of area): ");
        for (int i = 0; i < 9; i++) sb.append(String.format(Locale.UK, "%s=%.0f ", names[i], 100.0 * bands[i] / total));
        System.out.println(sb);
    }
}
