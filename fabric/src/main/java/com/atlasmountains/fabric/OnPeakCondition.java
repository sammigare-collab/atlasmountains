package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.function.DoubleSupplier;

/**
 * Material condition "atlasmountains:on_peak": true where the ground gets the peaks' own surface
 * (rock, and snow above the snow line) rather than vanilla's. Two cases:
 * <ol>
 *   <li>the ground is one of the peaks: they stand at least {@code min_above} blocks above
 *       vanilla's target height for the land at this column ({@link PeakStrength#aboveLand}), and
 *       this block is no more than {@code tolerance} blocks above the level the peaks reach;</li>
 *   <li>the rock line (0.4.10): in mountain country, any ground higher than {@code rock_below}
 *       blocks under the snow line ({@link SnowLine}), vanilla's own land included. A peak's
 *       faces are flat planes and vanilla's land under them is not, so here and there a hill of it
 *       comes through a face; with vanilla's own surface that was a white patch (snowy slopes) or
 *       a green one (meadow) high on a rock face. Now grass and trees stop at one height across a
 *       range, with rock above and snow above that. The line wanders with the same noise as the
 *       snow's edge.</li>
 * </ol>
 */
public record OnPeakCondition(int tolerance, double minAbove, double rockBelow) implements MaterialCondition {
    public static final MapCodec<OnPeakCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.optionalFieldOf("tolerance", 3).forGetter(OnPeakCondition::tolerance),
            Codec.DOUBLE.optionalFieldOf("min_above", 2.0).forGetter(OnPeakCondition::minAbove),
            Codec.DOUBLE.optionalFieldOf("rock_below", 75.0).forGetter(OnPeakCondition::rockBelow)
    ).apply(i, OnPeakCondition::new));

    private static final ResourceKey<NormalNoise> EDGE_NOISE = ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("snow_edge"));
    /** The rock line wanders up and down by about this many blocks (the biomes' share of it is SNOW_EDGE in build_overhaul.py). */
    private static final double WANDER = 7.68;
    /** How strongly mountain country must hold (0 to 1) for the rock line to apply. */
    private static final double MOUNTAIN_COUNTRY = 0.45;

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier edgeNoise = context.getNoiseSampler(EDGE_NOISE, false);
        return () -> {
            HornsFunction horns = HornsFunction.current;
            if (horns == null) return false;
            int x = context.blockX(), y = context.blockY(), z = context.blockZ();
            // a tolerance below zero turns this into a different question: is this the open-grass band of a mountain?
            if (tolerance < 0) return HornsFunction.ERODED && ErodedPeaks.cover(horns, x, z) == 1;
            double[] c = horns.column(x, z);
            if (c[0] > HornsFunction.NO_PEAK + 1 && c[2] > 0 && y <= 128 + 128 * c[0] + tolerance
                    && PeakStrength.aboveLand(x, z) >= minAbove
                    && (!HornsFunction.ERODED || ErodedPeaks.rocky(horns, x, z) > 0.5)) return true;
            double line = SnowLine.at(horns, x, z);
            return !Double.isNaN(line) && y + WANDER * edgeNoise.getAsDouble() >= line - rockBelow
                    && PeakStrength.mountainCountry(x, z) >= MOUNTAIN_COUNTRY;
        };
    }
}
