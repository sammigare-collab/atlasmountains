package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

/** Surface-rule condition "atlasmountains:in_mountains": the mountain mask is at least the threshold here. */
public record InMountainsCondition(float threshold) implements MaterialRules.MaterialCondition {

    public static final MapCodec<InMountainsCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("threshold", 0.5f).forGetter(InMountainsCondition::threshold)
    ).apply(i, InMountainsCondition::new));
    public static final CodecHolder<InMountainsCondition> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    @Override
    public CodecHolder<? extends MaterialRules.MaterialCondition> codec() { return CODEC_HOLDER; }

    @Override
    public MaterialRules.BooleanSupplier apply(MaterialRules.MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache().mask(context.blockX, context.blockZ) >= threshold;
    }
}
