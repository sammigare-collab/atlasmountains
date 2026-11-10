package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

/**
 * Surface-rule condition "atlasmountains:slope_above": true where the terrain heightmap's
 * slope at the column is at least the given number of degrees. Vanilla's "steep" only
 * triggers at about 63 degrees, which is useless for 45-degree faces.
 * Needs the access widener for MaterialRuleContext.blockX/blockZ and BooleanSupplier.
 */
public record SlopeAboveCondition(float degrees, boolean chains) implements MaterialRules.MaterialCondition {

    public static final MapCodec<SlopeAboveCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.fieldOf("degrees").forGetter(SlopeAboveCondition::degrees),
            Codec.BOOL.optionalFieldOf("chains", true).forGetter(SlopeAboveCondition::chains)
    ).apply(i, SlopeAboveCondition::new));
    public static final CodecHolder<SlopeAboveCondition> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    @Override
    public CodecHolder<? extends MaterialRules.MaterialCondition> codec() {
        return CODEC_HOLDER;
    }

    @Override
    public MaterialRules.BooleanSupplier apply(MaterialRules.MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache(chains).slopeDegrees(context.blockX, context.blockZ) >= degrees;
    }
}
