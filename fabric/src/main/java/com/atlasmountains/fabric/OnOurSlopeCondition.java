package com.atlasmountains.fabric;

import com.atlasmountains.core.RidgeSample;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * Material condition "atlasmountains:on_our_slope": true on our valley floors and where our
 * terrain stands more than {@code above_floor} blocks over the valley floor, i.e. on our flanks.
 * False at the foot of an outer flank, where vanilla's surface takes over.
 */
public record OnOurSlopeCondition(float aboveFloor) implements MaterialCondition {

    public static final MapCodec<OnOurSlopeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("above_floor", 6.0f).forGetter(OnOurSlopeCondition::aboveFloor)
    ).apply(i, OnOurSlopeCondition::new));
    private static final ThreadLocal<RidgeSample> SCRATCH = ThreadLocal.withInitial(RidgeSample::new);

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache().onOurSlope(context.blockX(), context.blockZ(), aboveFloor, SCRATCH.get());
    }
}
