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
}
