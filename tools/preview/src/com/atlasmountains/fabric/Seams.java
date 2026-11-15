package com.atlasmountains.fabric;

/**
 * How far two neighbouring regions, eroded separately, disagree across the band where one hands over to the other.
 *   java ... Seams <seed> [key=value ...]
 */
public final class Seams {
    public static void main(String[] a) throws Exception {
        AtlasMountainsMod.SEED = Long.parseLong(a[0]);
        int S = ErodedPeaks.SPACING, O = ErodedPeaks.OVERLAP, R = ErodedPeaks.REGION;
        HornsFunction horns = HornsFunction.game();
        for (int i = 1; i < a.length; i++) {
            if (a[i].startsWith("OVERLAP=")) O = Integer.parseInt(a[i].substring(8));
            else if (a[i].startsWith("REGION=")) R = Integer.parseInt(a[i].substring(7));
            else if (a[i].startsWith("OLD")) horns = new HornsFunction(230.0, 3.0, 172.0, 0.42, 60.0);
            else Preview.set(a[i]);
        }
        HornsFunction.current = horns;
        int G = R / S + 2 * O;
        long t0 = System.nanoTime(); int built = 0;
        double sum = 0, worst = 0, sumTall = 0; long count = 0, tall = 0, over5 = 0, over10 = 0;
        for (int rz = -1; rz <= 1; rz++) for (int rx = -2; rx <= 1; rx++) {
            float[][] left = build(horns, rx, rz, G, S, O, R), right = build(horns, rx + 1, rz, G, S, O, R);
            if (left == null || right == null) continue;
            int band = (int) (ErodedPeaks.BLEND / S);
            for (int gz = O; gz < G - O; gz++) for (int k = -band; k < band; k++) {
                // world x = (rx + 1) * R + k * S
                int lx = O + R / S + k, rxi = O + k;
                double d = Math.abs(left[0][gz * G + lx] - right[0][gz * G + rxi]);
                double hgt = Math.max(left[0][gz * G + lx], right[0][gz * G + rxi]);
                if (hgt <= 0) continue;
                sum += d; count++; worst = Math.max(worst, d);
                if (d > 5) over5++;
                if (d > 10) over10++;
                if (hgt > 60) { sumTall += d; tall++; }
            }
        }
        System.out.printf("overlap %d blocks, region %d: %d ms a region. ", O * S, R, (System.nanoTime() - t0) / 1000000 / 24);
        System.out.printf("seams: %d samples on mountains in the hand-over bands; differ by %.2f blocks on average (%.2f where the ground is over 60 high), worst %.1f; over 5 blocks on %.2f%%, over 10 on %.2f%%%n",
                count, sum / Math.max(1, count), sumTall / Math.max(1, tall), worst, 100.0 * over5 / Math.max(1, count), 100.0 * over10 / Math.max(1, count));
    }

    private static float[][] build(HornsFunction horns, int rx, int rz, int G, int S, int O, int R) {
        float[] relief = new float[G * G], carve = new float[G * G], buried = new float[G * G], up = new float[G * G], datum = new float[G * G];
        if (!Massif.build(horns, AtlasMountainsMod.seed(), rx * R - O * S, rz * R - O * S, G, S, relief, carve, buried, up, datum)) return null;
        return new float[][]{relief};
    }
}
