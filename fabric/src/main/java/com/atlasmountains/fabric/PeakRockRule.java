package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Material rule "atlasmountains:peak_rock": the bare rock of a peak, shading from one stone to the
 * next with height up the peak's own face: light at the foot, dark just under the snow cap. This is
 * the gradient Sammi chose on 10 Nov 2026 ("snow to dark to light") and the look of his reference
 * screenshot of 12 Nov 2026 (andesite and stone low down, tuff, then deepslate and smooth basalt
 * under the snow), taken over from the old mod's rock_gradient and measured against the new peaks.
 *
 * <h2>How a block is chosen</h2>
 * <ol>
 *   <li>Height on the face, as a percentage: 100 at the snow line ({@link SnowLine};
 *       {@code top_fraction} of the piece's own height is only the fallback where there is none),
 *       0 at {@code span} blocks below it (less where the peaks round about are small). So the
 *       rocks come in bands of a set depth under the snow: the dark rocks in a band right under
 *       it, mid-grey stone and tuff on the body of the mountain, the lightest only low down.
 *       (0.4.8 measured from a fixed height, {@code floor}, about the valley floors; on a peak
 *       whose foot is well above that, dark rock covered nearly the whole mountain.) In 0.4.7 the 0 was the piece's base
 *       level, the land under its summit, which is often 100 blocks above the valley: everything
 *       below it was the lightest rock, and whole mountainsides came out light grey (Sammi's
 *       screenshots, 12 Nov 2026). Without {@code floor} the piece's base level is still used.</li>
 *   <li>Wobble: the height is shifted up or down by up to {@code wobble} blocks with a slow 2-D
 *       noise, so the change from one rock to the next is not along one contour.</li>
 *   <li>Blend: between two stops the share of the upper rock rises from 0 to 1, over the middle
 *       {@code blend_width} of the gap; the roll is half a small 3-D noise (clumps a few blocks
 *       across) and half per-block jitter. With less jitter and the whole gap blended (0.4.7 and
 *       0.4.8) the mix came out as camouflage blotches close up.</li>
 * </ol>
 */
public record PeakRockRule(List<Stop> stops, float wobble, float blendWidth, double topFraction, int floor, double span) implements MaterialRule {

    /** Peaks this tall or taller have bands of the full span; smaller ones in proportion, down to SMALL_SPAN of it. */
    private static final double FULL_SPAN_HEIGHT = 170.0, SMALL_SPAN = 0.55;
    /** The value of floor that means "measure from each piece's own base level". */
    private static final int NO_FLOOR = -10000;

    /** One rock and the percentage up the face at which it is pure. */
    public record Stop(BlockState state, int at) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(Stop::state),
                Codec.INT.fieldOf("at").forGetter(Stop::at)
        ).apply(i, Stop::new));
    }

    public static final MapCodec<PeakRockRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Stop.CODEC.listOf().fieldOf("stops").forGetter(PeakRockRule::stops),
            Codec.FLOAT.optionalFieldOf("wobble", 8.0f).forGetter(PeakRockRule::wobble),
            Codec.FLOAT.optionalFieldOf("blend_width", 1.0f).forGetter(PeakRockRule::blendWidth),
            Codec.DOUBLE.optionalFieldOf("top_fraction", 0.8).forGetter(PeakRockRule::topFraction),
            Codec.INT.optionalFieldOf("floor", NO_FLOOR).forGetter(PeakRockRule::floor),
            Codec.DOUBLE.optionalFieldOf("span", 0.0).forGetter(PeakRockRule::span)
    ).apply(i, PeakRockRule::new));

    private static final ResourceKey<NormalNoise> WOBBLE_NOISE = ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("rock_wobble"));
    private static final ResourceKey<NormalNoise> MOTTLE_NOISE = ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("rock_mottle"));
    /** The mottle noise mostly sits within +/-0.4; stretched so the rolls cover 0..1. */
    private static final double MOTTLE_SPREAD = 2.5;
    /** Weight of the per-block jitter against the mottle noise. */
    private static final double JITTER = 0.5;

    @Override
    public MapCodec<? extends MaterialRule> codec() { return CODEC; }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier wobbleNoise = context.getNoiseSampler(WOBBLE_NOISE, false);
        DoubleSupplier mottleNoise = context.getNoiseSampler(MOTTLE_NOISE, true);
        PositionalRandomFactory randoms = context.getOrCreateRandomFactory(AtlasMountainsMod.id("peak_rock"));
        int n = stops.size();
        return (x, y, z) -> {
            HornsFunction horns = HornsFunction.current;
            if (horns == null || n == 0) return null;
            double[] c = horns.column(x, z);
            double baseY = 128 + 128 * c[1], capY = SnowLine.at(horns, x, z);
            if (Double.isNaN(capY)) {
                if (c[2] <= 0) return null;
                capY = baseY + topFraction * c[2];
            }
            // the bands are shallower on small peaks, so a small peak is not dark half-way down
            double zero = span > 0 ? capY - span * Math.max(SMALL_SPAN, Math.min(1, SnowLine.peakHeight(horns, x, z) / FULL_SPAN_HEIGHT))
                    : floor == NO_FLOOR ? baseY : floor;
            double h = 100 * (y + wobble * wobbleNoise.getAsDouble() - zero) / Math.max(8, capY - zero);
            if (n == 1 || h <= stops.get(0).at()) return stops.get(0).state();
            if (h >= stops.get(n - 1).at()) return stops.get(n - 1).state();
            int k = 0;
            while (k < n - 2 && h >= stops.get(k + 1).at()) k++;
            Stop lo = stops.get(k), hi = stops.get(k + 1);
            double t = (h - lo.at()) / Math.max(1, hi.at() - lo.at());
            double w = Math.max(1e-3, blendWidth);
            t = Math.max(0, Math.min(1, (t - 0.5) / w + 0.5));
            double mottle = Math.max(-1, Math.min(1, mottleNoise.getAsDouble() * MOTTLE_SPREAD));
            double roll = 0.5 + 0.5 * (mottle * (1 - JITTER) + (randoms.at(x, y, z).nextDouble() * 2 - 1) * JITTER);
            return roll < t ? hi.state() : lo.state();
        };
    }
}
