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
 * <p>
 * Background: in 26.x density functions no longer compute one point at a time. The game compiles
 * the whole density tree into samplers and asks each for a volume (a box of points) at once.
 * Most of our functions only depend on x and z, so computing them per point would repeat the
 * same expensive height lookup for every y in the column. {@link #sampleVolume} computes each
 * column once and fills it. {@link #domainAxes} tells the compiler the value ignores y, so it can
 * cache and reuse it accordingly.
 * <p>
 * Implementations only provide {@link #at}, plus {@link #range} if the value can leave -1..1
 * (the compiler uses the range to simplify min/max/clamp nodes, so a wrong range gives wrong
 * terrain, not just slow terrain).
 */
public interface ColumnFunction extends DensityFunction {

    /** The value at a block column. */
    double at(int blockX, int blockZ);

    @Override
    default DensitySampler compileSampler(CompileContext context) {
        return new DensitySampler() {
            /** One point: used for scattered lookups such as the aquifer and structure checks. */
            @Override
            public float sampleValue(SamplerContext ctx, int x, int y, int z) {
                return (float) at(x, z);
            }

            /** A box of points, as chunk generation asks for: one at() per column, copied down y. */
            @Override
            public void sampleVolume(SamplerContext ctx, DensityBuffer out, DensityVolume vol) {
                int sx = vol.sizeX(), sy = vol.sizeY(), sz = vol.sizeZ();
                for (int iz = 0; iz < sz; iz++) {
                    int bz = vol.blockZ(iz);
                    for (int ix = 0; ix < sx; ix++) {
                        float v = (float) at(vol.blockX(ix), bz);
                        int base = vol.indexUnchecked(ix, 0, iz);   // y is innermost in the buffer, so a column is contiguous
                        for (int iy = 0; iy < sy; iy++) out.set(base + iy, v);
                    }
                }
            }
        };
    }

    /** No child functions, so nothing to rewrite. */
    @Override
    default DensityFunction rewriteChildren(DfRewriteRule rule) { return this; }

    /** Depends on x and z only. */
    @Override
    default int domainAxes() { return AXIS_X | AXIS_Z; }

    /** The value's bounds; override when they are not -1..1. */
    @Override
    default Interval range() { return Interval.of(-1, 1); }
}
