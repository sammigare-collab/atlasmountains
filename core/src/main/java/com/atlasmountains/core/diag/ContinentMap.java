package com.atlasmountains.core.diag;

import com.atlasmountains.core.MountainMask;
import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.ChainLayout;
import com.atlasmountains.core.Continents;
import com.atlasmountains.core.SpineLayout;
import com.atlasmountains.core.RangeLayout;
import com.atlasmountains.core.SettingsOverrides;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * PNG preview of the plate continents with the mountain zones drawn on them, from the same
 * code the mod runs. No erosion, no regions: continentalness and mask only, so a 64 km map
 * takes a few seconds.
 *
 *     java -cp build/classes com.atlasmountains.core.diag.ContinentMap <seed> [spanBlocks] [blocksPerPixel] [centreX] [centreZ] [out.png] [name=value ...]
 *
 * Colours: deep ocean to shallow sea in blues, beach band in sand, land in greens darkening
 * inland, mountain zones in brown (opacity = mask value, so the coast fade is visible),
 * dashed grey lines mark the plate boundaries themselves (for reference only; nothing in the
 * world is drawn along them). A 10 km bar and the parameters are printed in the corner.
 */
public final class ContinentMap {

    public static void main(String[] args) throws IOException {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 1;
        int span = args.length > 1 ? Integer.parseInt(args[1]) : 64000;
        int bpp = args.length > 2 ? Integer.parseInt(args[2]) : 64;
        int cx = args.length > 3 ? Integer.parseInt(args[3]) : 0;
        int cz = args.length > 4 ? Integer.parseInt(args[4]) : 0;
        String out = args.length > 5 ? args[5] : "out/continents_seed_" + seed + ".png";
        MountainSettings s = new MountainSettings();
        for (int i = 6; i < args.length; i++) SettingsOverrides.apply(s, args[i]);

        Continents plates = Continents.create(s, seed);
        SpineLayout layout = s.chainLayout ? new ChainLayout(s, seed) : new RangeLayout(s, seed);
        MountainMask mask = new MountainMask(s, layout, plates, seed);
        String info = render(s, plates, mask, seed, span, bpp, cx, cz, new File(out));
        System.out.println(info);
        System.out.println("wrote " + out);
    }

    /** Renders the map and writes it to {@code out}; returns the legend line. */
    public static String render(MountainSettings s, Continents plates, MountainMask mask, long seed,
                                int span, int bpp, int cx, int cz, File out) throws IOException {
        int n = span / bpp;
        BufferedImage img = new BufferedImage(n, n, BufferedImage.TYPE_INT_RGB);
        double[] scratch = new double[2];
        double landPx = 0, zonePx = 0, deepPx = 0;
        for (int py = 0; py < n; py++) {
            for (int px = 0; px < n; px++) {
                double x = cx - span / 2.0 + (px + 0.5) * bpp;
                double z = cz - span / 2.0 + (py + 0.5) * bpp;
                double c = plates.continentalness(x, z);
                int rgb = landColour(c);
                if (c > -0.19) {
                    landPx++;
                    double m = mask.zone(x, z, scratch) * mask.inland(x, z);
                    if (m > 0.01) {
                        zonePx += m;
                        rgb = blend(rgb, 0x5a3a1e, m);
                    }
                }
                if (c <= -0.455) deepPx++;
                img.setRGB(px, py, rgb);
            }
        }
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // scale bar and legend
        int bar = 10000 / bpp;
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(3));
        g.drawLine(20, n - 24, 20 + bar, n - 24);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(12, n / 60)));
        g.drawString("10 km", 20, n - 32);
        String info = String.format("seed %d   span %d km   %s   land %.0f%%   mountain zones %.0f%% of land   deep ocean %.0f%%",
                seed, span / 1000, s.noiseContinents ? String.format("noise continents %.0f km", s.continentWavelength / 1000)
                        : String.format("plates ~%.0f km, %d%% land", s.plateSizeBlocks / 1000, Math.round(s.plateLandFraction * 100)),
                100 * landPx / (n * (double) n), 100 * zonePx / Math.max(1, landPx), 100 * deepPx / (n * (double) n));
        g.setColor(new Color(0, 0, 0, 160));
        g.fillRect(10, 10, g.getFontMetrics().stringWidth(info) + 20, g.getFontMetrics().getHeight() + 10);
        g.setColor(Color.WHITE);
        g.drawString(info, 20, 15 + g.getFontMetrics().getAscent());
        g.dispose();

        if (out.getParentFile() != null) out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        return info;
    }

    public static int landColour(double c) {
        if (c <= -0.455) return lerpColour(0x0b1f4d, 0x14407a, (c + 1.0) / (1.0 - 0.455));         // deep ocean
        if (c <= -0.19) return lerpColour(0x14407a, 0x3f86c2, (c + 0.455) / (0.455 - 0.19));      // ocean, shallowing
        if (c <= -0.11) return 0xe3d6a3;                                                            // beach band
        if (c <= 0.03) return lerpColour(0x9fc46a, 0x6fa84a, (c + 0.11) / (0.11 + 0.03));         // near inland
        if (c <= 0.3) return lerpColour(0x6fa84a, 0x4e8a3a, (c - 0.03) / (0.3 - 0.03));           // mid inland
        return lerpColour(0x4e8a3a, 0x3a6b2e, Math.min(1, (c - 0.3) / 0.4));                      // far inland
    }

    static int lerpColour(int a, int b, double t) {
        t = Math.max(0, Math.min(1, t));
        return blend(a, b, t);
    }

    public static int blend(int a, int b, double t) {
        int r = (int) Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int gg = (int) Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (gg << 8) | bl;
    }
}
