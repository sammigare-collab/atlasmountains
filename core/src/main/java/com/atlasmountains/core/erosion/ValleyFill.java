package com.atlasmountains.core.erosion;

import com.atlasmountains.core.HeightmapGenerator;
import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RangeLayout;
import com.atlasmountains.core.RegionHeightmap;

/**
 * Step 7: valley fill. The flat floor and lake beds come from the analytic model; erosion
 * dumps sediment on them and droplets can scar them. This pass restores the floor: within the
 * floor zone (where the un-noised model is at or below the valley floor) the height is
 * clamped to the model's floor from below (sediment fills scars) and capped a little above it
 * (fans are flattened), so the floor stays broad, flat and U-shaped with the lake in it.
 */
public final class ValleyFill {

    private final MountainSettings s;
    /** Sediment is allowed to build this high above the floor before being flattened. */
    public double maxFanHeight = 2.0;

    public ValleyFill(MountainSettings settings) {
        this.s = settings;
    }

    public void run(RegionHeightmap m, HeightmapGenerator gen) {
        RangeLayout.Sample sc = new RangeLayout.Sample();
        for (int gz = 0; gz < m.size; gz++) {
            double z = m.originZ + gz * (double) m.spacing;
            for (int gx = 0; gx < m.size; gx++) {
                double x = m.originX + gx * (double) m.spacing;
                double base = gen.baseHeightAt(x, z, sc);
                if (base > s.valleyFloorHeight + 1e-6) continue;
                double model = gen.heightAt(x, z, sc);  // floor with lake dip
                double h = m.get(gx, gz);
                if (h < model) h = model;
                if (h > model + maxFanHeight) h = model + maxFanHeight;
                m.set(gx, gz, (float) h);
            }
        }
    }
}
