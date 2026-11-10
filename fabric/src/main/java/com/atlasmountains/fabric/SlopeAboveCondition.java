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
 * 63 degrees, which is useless for 45-degree faces. {@code mixed} picks the preset's cache as in
 * {@link TerrainHeightFunction}.
 * <p>
 * The slope comes from our smooth terrain height, not from the placed blocks, so a face reads as
 * one slope however its voxel steps fall; see {@link com.atlasmountains.core.RegionCache#slopeDegrees}.
 */
public record SlopeAboveCondition(float degrees, boolean mixed) implements MaterialCondition {

    public static final MapCodec<SlopeAboveCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("degrees").forGetter(SlopeAboveCondition::degrees),
            Codec.BOOL.optionalFieldOf("mixed", true).forGetter(SlopeAboveCondition::mixed)
    ).apply(i, SlopeAboveCondition::new));

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache(mixed).slopeDegrees(context.blockX(), context.blockZ()) >= degrees;
    }
}
