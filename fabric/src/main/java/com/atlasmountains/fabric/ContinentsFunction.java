package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:continents": continentalness on vanilla's scale from the
 * mod's continents (-1 deep ocean .. 0.7 interior). Replaces vanilla's continentalness noise in
 * the mixed preset, so vanilla's terrain splines, biome table and spawn search all see them.
 * Range is the default -1..1, which covers the whole scale.
 */
public enum ContinentsFunction implements ColumnFunction {
    INSTANCE;

    public static final MapCodec<ContinentsFunction> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public double at(int x, int z) {
        return AtlasMountainsMod.cache().continents().continentalness(x, z);
    }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
