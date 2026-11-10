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
 * Material rule "atlasmountains:rock_gradient": exposed rock that shades from one stone to the
 * next with depth below its own peak (in the shipped datapack: dark near each summit, lighter
 * towards the foot of the face), with no stripes and no hard lines.
 *
 * <h2>Why</h2>
 * Two looks were tried before this and rejected. A per-column noise choice gave large flat
 * blotches; horizontal beds (strata) read as stripes. A gradient is what a weathered face looks
 * like from a distance: the colour drifts with height, and up close the two neighbouring rocks
 * are mixed in small clumps.
 *
 * <h2>How a block is chosen</h2>
 * <ol>
 *   <li><b>Stops.</b> {@code stops} lists rocks by height, lowest first. Below the first stop the
 *       first rock is used, above the last the last one. With {@code relative_to_peak} (the
 *       shipped setting) a stop's y is a percentage of the height of this column's own face:
 *       0 at the valley floor ({@code floor}), 100 at the top of the face's rock, which is the
 *       summit the column belongs to ({@link com.atlasmountains.core.RegionCache#maskedPeakHeight})
 *       or {@code top_cap} (the snow line) if the summit stands above it. Every peak, large or
 *       small, then shades through the whole gradient from where its rock begins, so the dark
 *       band always sits directly under the snow (or at the top of a peak with no snow),
 *       instead of all peaks sharing one set of heights. Without it, y is absolute.</li>
 *   <li><b>Wobble.</b> The height is shifted by {@code wobble} blocks times a slow 2D noise
 *       ({@code atlasmountains:rock_wobble}), so the change from one rock to the next happens
 *       higher on some faces than others instead of along one contour.</li>
 *   <li><b>Blend.</b> Between two stops, the position t (0 at the lower stop, 1 at the upper) is
 *       the chance of the upper rock. The roll is not pure per-block randomness, which would
 *       look like salt and pepper: it is a small 3D noise ({@code atlasmountains:rock_mottle},
 *       clumps a few blocks across) plus a little per-block jitter, so the mix comes in small
 *       patches. With {@code blend_width} below 1 the change is concentrated in the middle of
 *       each interval, so most of a face is one rock with a softer mixed band between.</li>
 * </ol>
 *
 * <h2>Cost</h2>
 * Per block: a search over a handful of stops, one cached 2D noise read per column, one 3D noise
 * read and one positional random. It only runs on the top few blocks of steep faces.
 *
 * @param stops       the rocks and the heights at which each is pure, lowest first
 * @param wobble      the largest up or down shift of the gradient, in blocks
 * @param blendWidth  the fraction of each interval over which the two rocks are mixed, centred
 *                    on the middle (1 = mixed across the whole interval)
 * @param relativeToPeak measure the stops as a percentage of each column's own face height instead of as y
 * @param topCap      with relativeToPeak: the highest the face's top can be (the snow line), since rock above it is under snow
 * @param floor       with relativeToPeak: the height counted as 0 percent (the valley floor)
 * @param mixed       which preset's terrain cache to read the summit from (as in TerrainHeightFunction)
 */
public record RockGradientRule(List<Stop> stops, float wobble, float blendWidth, boolean relativeToPeak,
                               int topCap, int floor, boolean mixed) implements MaterialRule {

    /**
     * One rock and the height at which it is pure.
     *
     * @param state the block
     * @param y     the height (y) of this stop
     */
    public record Stop(BlockState state, int y) {
        public static final Codec<Stop> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(Stop::state),
                Codec.INT.fieldOf("y").forGetter(Stop::y)
        ).apply(i, Stop::new));
    }

    public static final MapCodec<RockGradientRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Stop.CODEC.listOf().fieldOf("stops").forGetter(RockGradientRule::stops),
            Codec.FLOAT.optionalFieldOf("wobble", 16.0f).forGetter(RockGradientRule::wobble),
            Codec.FLOAT.optionalFieldOf("blend_width", 0.7f).forGetter(RockGradientRule::blendWidth),
            Codec.BOOL.optionalFieldOf("relative_to_peak", false).forGetter(RockGradientRule::relativeToPeak),
            Codec.INT.optionalFieldOf("top_cap", 10000).forGetter(RockGradientRule::topCap),
            Codec.INT.optionalFieldOf("floor", 64).forGetter(RockGradientRule::floor),
            Codec.BOOL.optionalFieldOf("mixed", true).forGetter(RockGradientRule::mixed)
    ).apply(i, RockGradientRule::new));

    /** Scratch for the peak lookup, one per thread. */
    private static final ThreadLocal<com.atlasmountains.core.RidgeSample> SCRATCH =
            ThreadLocal.withInitial(com.atlasmountains.core.RidgeSample::new);
    /** Last column's peak height, per thread: the rule is asked for several blocks of one column in a row. */
    private static final ThreadLocal<double[]> LAST_PEAK = ThreadLocal.withInitial(() -> new double[]{Double.NaN, 0, 0});

    /** Slow 2D noise that moves the gradient up and down; data/atlasmountains/worldgen/noise/rock_wobble.json. */
    private static final ResourceKey<NormalNoise> WOBBLE_NOISE =
            ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("rock_wobble"));
    /** Small 3D noise that clumps the mix; data/atlasmountains/worldgen/noise/rock_mottle.json. */
    private static final ResourceKey<NormalNoise> MOTTLE_NOISE =
            ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("rock_mottle"));

    /** Stretch applied to the mottle noise so its values cover -1..1 instead of bunching near 0. */
    private static final double MOTTLE_SPREAD = 2.5;

    /** Weight of the per-block jitter against the mottle noise in the blend roll. */
    private static final double JITTER = 0.25;

    @Override
    public MapCodec<? extends MaterialRule> codec() { return CODEC; }

    /** This column's summit height (see RegionCache.maskedPeakHeight), cached for the last column asked. */
    private double peakHeight(int x, int z) {
        double[] last = LAST_PEAK.get();
        if (last[1] == x && last[2] == z && !Double.isNaN(last[0])) return last[0];
        double p = AtlasMountainsMod.cache(mixed).maskedPeakHeight(x, z, SCRATCH.get());
        last[0] = p; last[1] = x; last[2] = z;
        return p;
    }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier wobbleNoise = context.getNoiseSampler(WOBBLE_NOISE, false);   // per column
        DoubleSupplier mottleNoise = context.getNoiseSampler(MOTTLE_NOISE, true);    // per block
        PositionalRandomFactory randoms = context.getOrCreateRandomFactory(AtlasMountainsMod.id("rock_gradient"));
        int n = stops.size();
        return (x, y, z) -> {
            if (n == 1) return stops.get(0).state();
            double h = y + wobble * wobbleNoise.getAsDouble();
            if (relativeToPeak) {
                // percent of the way from the valley floor (0) up to the top of this face's rock
                // (100): the summit, or the snow line if the summit stands above it
                double top = Math.min(peakHeight(x, z), topCap);
                h = 100 * (h - floor) / Math.max(8, top - floor);
            }
            if (h <= stops.get(0).y()) return stops.get(0).state();
            if (h >= stops.get(n - 1).y()) return stops.get(n - 1).state();
            int k = 0;
            while (k < n - 2 && h >= stops.get(k + 1).y()) k++;
            Stop lo = stops.get(k), hi = stops.get(k + 1);
            double t = (h - lo.y()) / Math.max(1, hi.y() - lo.y());
            // squeeze the mixed part into the middle blendWidth of the interval
            double w = Math.max(1e-3, blendWidth);
            t = Math.max(0, Math.min(1, (t - 0.5) / w + 0.5));
            // roll in 0..1, clumped: mottle noise mapped to 0..1, plus jitter. The noise mostly
            // sits within about +/-0.4, so it is stretched by MOTTLE_SPREAD and clamped; without
            // that every roll lands near 0.5 and the mix collapses into a narrow band.
            double mottle = Math.max(-1, Math.min(1, mottleNoise.getAsDouble() * MOTTLE_SPREAD));
            double roll = 0.5 + 0.5 * (mottle * (1 - JITTER)
                    + (randoms.at(x, y, z).nextDouble() * 2 - 1) * JITTER);
            return roll < t ? hi.state() : lo.state();
        };
    }
}
