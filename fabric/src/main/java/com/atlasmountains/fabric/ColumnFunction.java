package com.atlasmountains.fabric;

import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;

/**
 * A density function of (x, z) only, for 26.x's compiled sampler pipeline: the value is computed
 * once per column of the requested volume and written down the whole column. No children.
 */
public interface ColumnFunction extends DensityFunction {

    /** The value at a block column. */
    double at(int blockX, int blockZ);

    @Override
    default DensitySampler compileSampler(CompileContext context) {
        return new DensitySampler() {
            @Override
            public float sampleValue(SamplerContext ctx, int x, int y, int z) {
                return (float) at(x, z);
            }

            @Override
            public void sampleVolume(SamplerContext ctx, DensityBuffer out, DensityVolume vol) {
                int sx = vol.sizeX(), sy = vol.sizeY(), sz = vol.sizeZ();
                for (int iz = 0; iz < sz; iz++) {
                    int bz = vol.blockZ(iz);
                    for (int ix = 0; ix < sx; ix++) {
                        float v = (float) at(vol.blockX(ix), bz);
                        int base = vol.indexUnchecked(ix, 0, iz);
                        for (int iy = 0; iy < sy; iy++) out.set(base + iy, v);
                    }
                }
            }
        };
    }

    @Override
    default DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }

    @Override
    default int domainAxes() { return AXIS_X | AXIS_Z; }

    @Override
    default Interval range() { return Interval.of(-1, 1); }
}
