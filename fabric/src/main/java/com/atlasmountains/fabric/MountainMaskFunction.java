package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/** Density function "atlasmountains:mountain_mask": 0 outside the mountain zones, 1 inside, soft edge. */
public final class MountainMaskFunction implements DensityFunction.Base {

    public static final MountainMaskFunction INSTANCE = new MountainMaskFunction();
    public static final MapCodec<MountainMaskFunction> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final CodecHolder<MountainMaskFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private MountainMaskFunction() {}

    @Override
    public double sample(NoisePos pos) {
        return AtlasMountainsMod.cache().mask(pos.blockX(), pos.blockZ());
    }

    @Override
    public double minValue() { return 0; }

    @Override
    public double maxValue() { return 1; }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() { return CODEC_HOLDER; }
}
