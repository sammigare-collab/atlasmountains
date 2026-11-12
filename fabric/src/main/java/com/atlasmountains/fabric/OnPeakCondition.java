package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;

/**
 * Material condition "atlasmountains:on_peak": true where the ground is one of the peaks rather
 * than vanilla's land: the peaks stand at least {@code min_above} blocks above vanilla's target
 * height for the land at this column ({@link PeakStrength#aboveLand}), and this block is no more
 * than {@code tolerance} blocks above the level the peaks reach here.
 * <p>
 * With {@code above_fraction} it is only true that far up the piece that forms the ground here, as
 * a fraction of its height (0 its base, 1 its summit): used for the light dusting of snow on the
 * very tops of peaks in warm country.
 */
public record OnPeakCondition(int tolerance, double minAbove, double aboveFraction) implements MaterialCondition {
    public static final MapCodec<OnPeakCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("tolerance", 3).forGetter(OnPeakCondition::tolerance),
            Codec.DOUBLE.optionalFieldOf("min_above", 2.0).forGetter(OnPeakCondition::minAbove),
            Codec.DOUBLE.optionalFieldOf("above_fraction", -1000.0).forGetter(OnPeakCondition::aboveFraction)
    ).apply(i, OnPeakCondition::new));

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> {
            HornsFunction horns = HornsFunction.current;
            if (horns == null) return false;
            double[] c = horns.column(context.blockX(), context.blockZ());
            if (c[0] <= HornsFunction.NO_PEAK + 1 || c[2] <= 0) return false;
            if (PeakStrength.aboveLand(context.blockX(), context.blockZ()) < minAbove) return false;
            int y = context.blockY();
            if (y > 128 + 128 * c[0] + tolerance) return false;
            return (y - (128 + 128 * c[1])) / c[2] >= aboveFraction;
        };
    }
}
