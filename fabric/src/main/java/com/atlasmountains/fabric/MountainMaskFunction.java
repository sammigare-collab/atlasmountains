package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:mountain_mask": 0 outside the mountain zones, 1 inside, soft edge.
 * {@code kind: "inner"} gives the inner mask instead: 1 only between the spines of a chain (the
 * flat valley floor), 0 just outside its outermost spine, so vanilla terrain can carry on under
 * the outer flanks.
 */
public record MountainMaskFunction(String kind) implements DensityFunction.Base {

    public static final MapCodec<MountainMaskFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("kind", "zone").forGetter(MountainMaskFunction::kind)
    ).apply(i, MountainMaskFunction::new));
    public static final CodecHolder<MountainMaskFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    @Override
    public double sample(NoisePos pos) {
        var cache = AtlasMountainsMod.cache();
        if (kind.equals("inner")) return cache.innerMask(pos.blockX(), pos.blockZ());
        return cache.mask(pos.blockX(), pos.blockZ());
    }

    @Override
    public double minValue() { return 0; }

    @Override
    public double maxValue() { return 1; }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() { return CODEC_HOLDER; }
}
