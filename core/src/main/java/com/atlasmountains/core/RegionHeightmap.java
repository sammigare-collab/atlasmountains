package com.atlasmountains.core;

/**
 * One region's heightmap at sample resolution, including the overlap border.
 * Grid index (gx, gz) maps to world block (originX + gx * spacing, originZ + gz * spacing),
 * where the origin is the region's corner minus the overlap.
 * <p>
 * Storage is one float array in row-major order (z rows of x samples), which is also the layout
 * of the on-disk cache file. Floats keep a region at about 1.2 MB in memory; heights only need
 * a small fraction of a block of precision.
 */
public final class RegionHeightmap {
    /** Which region this is, in region coordinates (block coordinate / regionSizeBlocks, floored). */
    public final int regionX, regionZ;
    public final int size;        // samples per side including overlap
    public final int spacing;     // blocks per sample
    public final int originX, originZ;   // world block of sample (0, 0)
    /** Heights (y, fractional), size * size samples. */
    public final float[] height;
    /**
     * The un-eroded model height where the analytic base is at or below the valley floor (the
     * floor zone), NaN elsewhere. Filled by the generator so the valley-fill pass needn't
     * re-sample the layout; null if the map wasn't made by the generator.
     */
    public float[] floorModel;

    public RegionHeightmap(MountainSettings s, int regionX, int regionZ) {
        this.regionX = regionX;
        this.regionZ = regionZ;
        this.size = s.gridSize();
        this.spacing = s.sampleSpacing;
        this.originX = regionX * s.regionSizeBlocks - s.overlapSamples * s.sampleSpacing;
        this.originZ = regionZ * s.regionSizeBlocks - s.overlapSamples * s.sampleSpacing;
        this.height = new float[size * size];
    }

    public float get(int gx, int gz) { return height[gz * size + gx]; }
    public void set(int gx, int gz, float h) { height[gz * size + gx] = h; }

    /** Height at a grid index, with indices outside the grid clamped to the nearest edge sample. */
    private float clamped(int gx, int gz) {
        gx = Math.max(0, Math.min(size - 1, gx));
        gz = Math.max(0, Math.min(size - 1, gz));
        return height[gz * size + gx];
    }

    private static double cubic(double p0, double p1, double p2, double p3, double t) {
        // Catmull-Rom spline through p1 (t = 0) and p2 (t = 1), with tangents from p0 and p3.
        // Unlike a B-spline it passes through the samples, so peaks are not flattened.
        return p1 + 0.5 * t * (p2 - p0 + t * (2 * p0 - 5 * p1 + 4 * p2 - p3 + t * (3 * (p1 - p2) + p3 - p0)));
    }

    /** Bicubic (Catmull-Rom) height at a world block position. No blur: passes through every sample. */
    public double sample(double worldX, double worldZ) {
        double gx = (worldX - originX) / spacing;
        double gz = (worldZ - originZ) / spacing;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        double tx = gx - ix, tz = gz - iz;
        // interpolate four rows along x, then the four results along z
        double[] rows = new double[4];
        for (int j = -1; j <= 2; j++) {
            rows[j + 1] = cubic(clamped(ix - 1, iz + j), clamped(ix, iz + j), clamped(ix + 1, iz + j), clamped(ix + 2, iz + j), tx);
        }
        return cubic(rows[0], rows[1], rows[2], rows[3], tz);
    }

    /** Slope magnitude (tan) at a grid cell by central differences; used by the erosion passes. */
    public double slope(int gx, int gz) {
        double dx = (clamped(gx + 1, gz) - clamped(gx - 1, gz)) / (2.0 * spacing);
        double dz = (clamped(gx, gz + 1) - clamped(gx, gz - 1)) / (2.0 * spacing);
        return Math.sqrt(dx * dx + dz * dz);
    }
}
