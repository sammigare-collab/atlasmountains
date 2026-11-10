package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator;
import net.minecraft.world.level.levelgen.material.condition.MaterialCondition;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import java.util.function.DoubleSupplier;

/**
 * Material condition "atlasmountains:snow_cover": the patchy snow below the snow line.
 *
 * <h2>What it adds</h2>
 * Above the snow line every top block is snow; that rule is unchanged and keeps the summits
 * white. Real mountains do not stop being white along one contour, though: below the line,
 * snow lies on ledges, shoulders and gentle slopes, and slides off steep faces. This condition
 * is true for exactly those top blocks, from {@code lowest} upwards,
 * so the white of the summits frays downwards into patches instead of ending in a straight edge.
 *
 * <h2>The test, for a top block at height y</h2>
 * <ol>
 *   <li>{@code t = (y - lowest) / (snow_line - lowest)}: 0 at the bottom of the band, 1 at the
 *       snow line, and more than 1 above it. Below {@code lowest} the condition is false. Above
 *       the snow line the snow line rule usually places snow first; where its jitter leaves a
 *       block uncovered, this condition is all but certain to cover it.</li>
 *   <li>A patch value: the 2D noise {@code atlasmountains:snow_patches} (blobs about a dozen
 *       blocks across, defined in the datapack) plus a small per-block jitter, so patch edges
 *       are ragged rather than smooth curves.</li>
 *   <li>A threshold that falls with height: {@code 0.9 - 1.6 t}. Near the bottom of the band
 *       only the strongest noise blobs pass, so snow is rare; near the snow line almost
 *       everything passes, so the patches merge into the solid cover above.</li>
 *   <li>Steepness, so snow lies where it would settle: the threshold is shifted by
 *       {@code steep_penalty * clamp((slope - ledge_slope) / 15, -1, 1)}. Ground 15 degrees or
 *       more gentler than {@code ledge_slope} gets the full bonus, so on shoulders, cols and
 *       benches the snow reaches furthest down; ground 15 degrees or more steeper gets the full
 *       penalty, so steep faces only carry a dusting. In between the effect is proportional.
 *       That is the look of the reference: bare rock faces between white ledges.</li>
 * </ol>
 * The slope comes from our terrain heightmap ({@link com.atlasmountains.core.RegionCache#slopeDegrees}),
 * the same one that decides where rock shows, so snow and rock agree about which ground is steep.
 * {@code mixed} picks the preset's terrain cache as in {@link TerrainHeightFunction}.
 *
 * @param snowLine     the height of the snow line rule, where the patches reach full cover
 * @param lowest       the lowest height a patch can reach
 * @param ledgeSlope   the slope, in degrees, at which the slope has no effect either way
 * @param steepPenalty the largest shift of the threshold from slope, either way (noise units;
 *                     the noise runs roughly from -1 to 1)
 * @param crestWidth   radius, in blocks, of the ring used to measure how far ground stands above its surroundings (0 = off)
 * @param crestBonus   the largest drop in the threshold on crests and spurs, so snow runs further down them
 * @param mixed        which preset's terrain cache to read
 */
public record SnowCoverCondition(int snowLine, int lowest, float ledgeSlope, float steepPenalty,
                                 float crestWidth, float crestBonus, boolean mixed) implements MaterialCondition {

    public static final MapCodec<SnowCoverCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("snow_line").forGetter(SnowCoverCondition::snowLine),
            Codec.INT.fieldOf("lowest").forGetter(SnowCoverCondition::lowest),
            Codec.FLOAT.optionalFieldOf("ledge_slope", 38.0f).forGetter(SnowCoverCondition::ledgeSlope),
            Codec.FLOAT.optionalFieldOf("steep_penalty", 0.45f).forGetter(SnowCoverCondition::steepPenalty),
            Codec.FLOAT.optionalFieldOf("crest_width", 0f).forGetter(SnowCoverCondition::crestWidth),
            Codec.FLOAT.optionalFieldOf("crest_bonus", 0f).forGetter(SnowCoverCondition::crestBonus),
            Codec.BOOL.optionalFieldOf("mixed", true).forGetter(SnowCoverCondition::mixed)
    ).apply(i, SnowCoverCondition::new));

    /** Blob noise for the patches; defined in data/atlasmountains/worldgen/noise/snow_patches.json. */
    private static final ResourceKey<NormalNoise> PATCH_NOISE =
            ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("snow_patches"));

    /** Degrees either side of ledgeSlope over which the slope effect ramps from full bonus to full penalty. */
    private static final double SLOPE_RAMP = 15;

    /** Largest per-block jitter added to the patch noise, either way. */
    private static final float JITTER = 0.15f;

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    /** Last column's crestness, per thread (asked for every block of a column in turn). */
    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, 0, 0});
    /** Crestness, in blocks, at which the full crestBonus applies. */
    private static final double CREST_FULL = 3.0;

    /**
     * How far, in blocks, the terrain at (x, z) stands above the average of eight points
     * crestWidth blocks around it. Positive on ridges and spurs (convex ground), near 0 on plain
     * faces, negative in gullies. Read from our eroded terrain height, so it finds the crests
     * erosion actually left, not only the layout's ridge lines.
     */
    private double crestness(int x, int z) {
        double[] last = LAST.get();
        if (last[1] == x && last[2] == z && !Double.isNaN(last[0])) return last[0];
        com.atlasmountains.core.RegionCache cache = AtlasMountainsMod.cache(mixed);
        double r = crestWidth, d = crestWidth * 0.7071;
        double ring = cache.maskedHeight(x + r, z) + cache.maskedHeight(x - r, z)
                + cache.maskedHeight(x, z + r) + cache.maskedHeight(x, z - r)
                + cache.maskedHeight(x + d, z + d) + cache.maskedHeight(x - d, z - d)
                + cache.maskedHeight(x + d, z - d) + cache.maskedHeight(x - d, z + d);
        double c = cache.maskedHeight(x, z) - ring / 8;
        last[0] = c; last[1] = x; last[2] = z;
        return c;
    }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier patches = context.getNoiseSampler(PATCH_NOISE, false);
        PositionalRandomFactory randoms = context.getOrCreateRandomFactory(AtlasMountainsMod.id("snow_cover"));
        float span = Math.max(1, snowLine - lowest);
        return () -> {
            int y = context.blockY();
            if (y < lowest) return false;
            // t is not capped at 1: the snow line rule above this one jitters its line a dozen or
            // so blocks upwards, and in that strip this condition carries on, ever more certain,
            // so there is no bare band between the patches and the solid cover
            float t = (y - lowest) / span;
            int x = context.blockX(), z = context.blockZ();
            double threshold = 0.9 - 1.6 * t;
            // Gradual: 15 degrees gentler than ledgeSlope gives the full bonus (snow reaches further
            // down), 15 degrees steeper gives the full penalty (only a dusting).
            double steepness = (AtlasMountainsMod.cache(mixed).slopeDegrees(x, z) - ledgeSlope) / SLOPE_RAMP;
            threshold += steepPenalty * Math.max(-1, Math.min(1, steepness));
            // Along crests snow settles further down: where the ground stands above its
            // surroundings (a main ridge or a spur between two gullies) the threshold drops by up
            // to crestBonus, so white lines run down the ridges from the snow line, thinning out
            // towards lowest.
            if (crestWidth > 0 && crestBonus > 0) {
                double c = crestness(x, z);
                if (c > 0) threshold -= crestBonus * Math.min(1, c / CREST_FULL);
            }
            double jitter = (randoms.at(x, y, z).nextFloat() * 2 - 1) * JITTER;
            return patches.getAsDouble() + jitter > threshold;
        };
    }
}
