package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/** Material condition "atlasmountains:in_mountains": the mountain mask is at least the threshold here. */
public record InMountainsCondition(float threshold) implements MaterialCondition {

    public static final MapCodec<InMountainsCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("threshold", 0.5f).forGetter(InMountainsCondition::threshold)
    ).apply(i, InMountainsCondition::new));

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache().mask(context.blockX(), context.blockZ()) >= threshold;
    }
}
