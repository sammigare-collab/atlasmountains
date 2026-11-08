package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:continents": continentalness on vanilla's scale from
 * the mod's continents (noise continents, or Voronoi plates) (-1 deep ocean .. 0.7 plate interior). Replaces vanilla's
 * continentalness noise in the mixed preset's noise router, so vanilla's terrain splines,
 * biome table and spawn search all see the plates. No y dependence.
 */
public final class ContinentsFunction implements DensityFunction.Base {

    public static final ContinentsFunction INSTANCE = new ContinentsFunction();
    public static final MapCodec<ContinentsFunction> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final CodecHolder<ContinentsFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private ContinentsFunction() {}

    @Override
    public double sample(NoisePos pos) {
        return AtlasMountainsMod.cache().continents().continentalness(pos.blockX(), pos.blockZ());
    }

    @Override
    public double minValue() { return -1; }

    @Override
    public double maxValue() { return 1; }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() { return CODEC_HOLDER; }
}
