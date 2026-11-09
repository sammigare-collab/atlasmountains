package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * Material condition "atlasmountains:slope_above": true where the terrain heightmap's slope at
 * the column is at least the given number of degrees. Vanilla's "steep" only triggers at about
 * 63 degrees, which is useless for 45-degree faces.
 */
public record SlopeAboveCondition(float degrees, boolean chains) implements MaterialCondition {

    public static final MapCodec<SlopeAboveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("degrees").forGetter(SlopeAboveCondition::degrees),
            Codec.BOOL.optionalFieldOf("chains", true).forGetter(SlopeAboveCondition::chains)
    ).apply(i, SlopeAboveCondition::new));

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache(chains).slopeDegrees(context.blockX(), context.blockZ()) >= degrees;
    }
}
