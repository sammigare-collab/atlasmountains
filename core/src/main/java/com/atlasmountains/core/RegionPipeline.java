package com.atlasmountains.core;

import com.atlasmountains.core.erosion.HydraulicErosion;
import com.atlasmountains.core.erosion.ThermalErosion;
import com.atlasmountains.core.erosion.ValleyFill;

import java.util.Locale;

/**
 * The full per-region pipeline: generate (steps 1 to 4), thermal, hydraulic, valley fill,
 * thermal again. This is what the mod runs once per region and caches; the diagnostic tool
 * runs exactly the same thing.
 */
public final class RegionPipeline {

    private final MountainSettings s;
    private final long seed;
    private final HeightmapGenerator generator;
    private final ThermalErosion thermal;
    private final HydraulicErosion hydraulic;
    private final ValleyFill fill;
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

    public RegionHeightmap raw(int rx, int rz) {
        return generator.generate(rx, rz);
    }

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
