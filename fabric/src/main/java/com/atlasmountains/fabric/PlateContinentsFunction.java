package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:plate_continents": continentalness on vanilla's scale from
 * the Voronoi plate continents (-1 deep ocean .. 0.7 plate interior). Replaces vanilla's
 * continentalness noise in the mixed preset's noise router, so vanilla's terrain splines,
 * biome table and spawn search all see the plates. No y dependence.
 */
public final class PlateContinentsFunction implements DensityFunction.Base {

    public static final PlateContinentsFunction INSTANCE = new PlateContinentsFunction();
    public static final MapCodec<PlateContinentsFunction> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final CodecHolder<PlateContinentsFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private PlateContinentsFunction() {}

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
