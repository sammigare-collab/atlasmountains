package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:mountain_mask": 0 outside the mountains, 1 inside, soft edge (mixed preset).
 * <p>
 * The datapack uses it to blend our terrain with vanilla's (see "mixed blend" in
 * tools/build_datapack.py) and to push vanilla's own terrain down where ours stands. The value is
 * {@link com.atlasmountains.core.RegionCache#mask}, so it agrees exactly with the masked height.
 */
public enum MountainMaskFunction implements ColumnFunction {
    INSTANCE;

    public static final MapCodec<MountainMaskFunction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double at(int x, int z) {
        return AtlasMountainsMod.cache().mask(x, z);
    }

    @Override
    public Interval range() { return Interval.of(0, 1); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
