package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:mountain_mask": 0 outside the mountain zones, 1 inside, soft edge.
 * {@code kind: "inner"} gives the inner mask instead: 1 where vanilla terrain is replaced outright
 * (valley floors and the flanks), 0 just outside, so vanilla terrain can carry on under the feet.
 */
public record MountainMaskFunction(String kind) implements ColumnFunction {

    public static final MapCodec<MountainMaskFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("kind", "zone").forGetter(MountainMaskFunction::kind)
    ).apply(i, MountainMaskFunction::new));

    @Override
    public double at(int x, int z) {
        var cache = AtlasMountainsMod.cache();
        if (kind.equals("inner")) return cache.innerMask(x, z);
        return cache.mask(x, z);
    }

    @Override
    public Interval range() { return Interval.of(0, 1); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
