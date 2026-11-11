package com.atlasmountains.core;

/**
 * What {@link FieldLayout#sample} reports for one column: the nearest ridge and the next one.
 * The crest and cross profiles only ever see this, so the mountain design does not depend on how
 * the ridges are laid out. Reused per thread as scratch, so it is mutable.
 * <p>
 * The older layouts (plate continents, parallel ranges, chains) filled the same fields, which is
 * how the layout could be swapped three times without touching the crest or the cross profile.
 */
public final class RidgeSample {
    /** Relief multiplier for the crest at this column (the field layout's height scale). */
    public double scale = 1.0;
    /** Id of the nearest ridge (seeds its row of summits). */
    public int ridge;
    /** Distance to the nearest ridge, blocks, always >= 0. */
    public double distance;
    /** Position along the nearest ridge, blocks. */
    public double along;
    /** Distance to the second-nearest ridge (the other side of a valley, or the other ridge at a junction). */
    public double distance2;
    /** Id of that second ridge, and the position along it. */
    public int ridge2;
    public double along2;
    /** Distance to the third-nearest ridge (MAX_VALUE if none): where it overtakes the second, legs of the second fade out. */
    public double distance3 = Double.MAX_VALUE;
    /**
     * For each of the two spines: the unclamped position along it (alongRaw; {@code along} is this
     * clamped to the spine), the perpendicular distance to its line (perp; {@code distance} also
     * counts the run past an end), its half-length, and whether its low / high end is a free end.
     */
    public double alongRaw, perp, half, alongRaw2, perp2, half2;
    public boolean leafLo, leafHi, leafLo2, leafHi2;
    /** Relief of each spine relative to a main one (1 for a main spine, less for a side ridge). */
    public double rel = 1, rel2 = 1;
    /**
     * Every spine within a half-width, for the generator's union (the two-nearest fields above
     * are not enough where three or more meet, at an elbow with a side ridge): count and, per
     * spine, ridge id, distance, clamped along, half-length, relief, free ends.
     */
    public int n;
    public int[] nRidge = new int[32];
    public double[] nDist = new double[32], nAlong = new double[32], nHalf = new double[32], nRel = new double[32];
    public boolean[] nLeafLo = new boolean[32], nLeafHi = new boolean[32];

    void push(int ridge, double dist, double along, double half, double rel, boolean lo, boolean hi) {
        if (n == nRidge.length) {
            int m = n * 2;
            nRidge = java.util.Arrays.copyOf(nRidge, m); nDist = java.util.Arrays.copyOf(nDist, m);
            nAlong = java.util.Arrays.copyOf(nAlong, m); nHalf = java.util.Arrays.copyOf(nHalf, m);
            nRel = java.util.Arrays.copyOf(nRel, m); nLeafLo = java.util.Arrays.copyOf(nLeafLo, m); nLeafHi = java.util.Arrays.copyOf(nLeafHi, m);
        }
        nRidge[n] = ridge; nDist[n] = dist; nAlong[n] = along; nHalf[n] = half; nRel[n] = rel; nLeafLo[n] = lo; nLeafHi[n] = hi; n++;
    }
}
