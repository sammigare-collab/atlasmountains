package com.atlasmountains.core;

import com.atlasmountains.core.erosion.HydraulicErosion;
import com.atlasmountains.core.erosion.ThermalErosion;
import com.atlasmountains.core.erosion.ValleyFill;

import java.util.Locale;

/**
 * The full per-region pipeline: generate (steps 1 to 4), thermal, hydraulic, valley fill,
 * thermal again. This is what the mod runs once per region and caches; the diagnostic tool
 * runs exactly the same thing.
 *
 * <h2>Why this order</h2>
 * <ol>
 *   <li><b>Generate</b> the analytic mountain ({@link HeightmapGenerator}).</li>
 *   <li><b>Thermal</b> first, so the spikes of the detail noise are knocked down before water
 *       runs over them; otherwise droplets get trapped in single-sample pits.</li>
 *   <li><b>Hydraulic</b> carves the gullies and deposits the sediment lower down.</li>
 *   <li><b>Valley fill</b> restores the flat floors and lakes: deposition would otherwise
 *       leave lumpy sediment fans across them.</li>
 *   <li><b>Thermal</b> again, to bring the gully walls back to the face angle, and a second
 *       fill to undo what that pass spilled onto the floors.</li>
 * </ol>
 * Every step is deterministic from the seed and the region coordinates, so a region eroded on
 * one machine is identical to the same region eroded on another; the disk cache relies on this.
 */
public final class RegionPipeline {

    private final MountainSettings s;
    private final long seed;
    private final HeightmapGenerator generator;
    private final ThermalErosion thermal;
    private final HydraulicErosion hydraulic;
    private final ValleyFill fill;
    /** Print per-step timings to stdout (the diagnostic tool turns this on). */
    public boolean verbose = false;

    public RegionPipeline(MountainSettings settings, long seed) {
        this.s = settings;
        this.seed = seed;
        this.generator = new HeightmapGenerator(settings, seed);
        this.thermal = new ThermalErosion(settings);
        this.hydraulic = new HydraulicErosion(settings);
        this.fill = new ValleyFill(settings);
    }

    public HeightmapGenerator generator() { return generator; }
    public MountainSettings settings() { return s; }

    /** The region before any erosion: for previews comparing before and after. */
    public RegionHeightmap raw(int rx, int rz) {
        return generator.generate(rx, rz);
    }

    /** The region after the whole pipeline; see the class comment for the order and why. */
    public RegionHeightmap eroded(int rx, int rz) {
        long t0 = System.nanoTime();
        RegionHeightmap m = generator.generate(rx, rz);
        long t1 = System.nanoTime();
        double moved1 = thermal.run(m, s.thermalPassesBefore);
        long t2 = System.nanoTime();
        hydraulic.run(m, seed);
        long t3 = System.nanoTime();
        fill.run(m, generator);
        double moved2 = thermal.run(m, s.thermalPassesAfter);
        fill.run(m, generator);
        long t4 = System.nanoTime();
        if (verbose) {
            System.out.printf(Locale.UK,
                    "region %d,%d: generate %.2fs, thermal x%d %.2fs (last pass moved %.1f), hydraulic %d droplets %.2fs, fill+thermal x%d %.2fs (last pass moved %.1f)%n",
                    rx, rz, (t1 - t0) / 1e9, s.thermalPassesBefore, (t2 - t1) / 1e9, moved1,
                    s.dropletsPerRegion, (t3 - t2) / 1e9, s.thermalPassesAfter, (t4 - t3) / 1e9, moved2);
        }
        return m;
    }
}
