package com.atlasmountains.fabric;

import com.atlasmountains.core.RangeLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;

/**
 * Surface-rule condition "atlasmountains:on_our_slope": true on a chain's valley floor (between
 * its spines) or where our terrain stands more than {@code above_floor} blocks over the valley
 * floor, i.e. on our flanks. False at the foot of an outer flank, where vanilla's surface takes over.
 */
public record OnOurSlopeCondition(float aboveFloor) implements MaterialRules.MaterialCondition {

    public static final MapCodec<OnOurSlopeCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("above_floor", 6.0f).forGetter(OnOurSlopeCondition::aboveFloor)
    ).apply(i, OnOurSlopeCondition::new));
    public static final CodecHolder<OnOurSlopeCondition> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);
    private static final ThreadLocal<RangeLayout.Sample> SCRATCH = ThreadLocal.withInitial(RangeLayout.Sample::new);

    @Override
    public CodecHolder<? extends MaterialRules.MaterialCondition> codec() { return CODEC_HOLDER; }

    @Override
    public MaterialRules.BooleanSupplier apply(MaterialRules.MaterialRuleContext context) {
        return () -> AtlasMountainsMod.cache().onOurSlope(context.blockX, context.blockZ, aboveFloor, SCRATCH.get());
    }
}
