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
 * is true for exactly those top blocks, in a band from {@code lowest} up to {@code snow_line},
 * so the white of the summits frays downwards into patches instead of ending in a straight edge.
 *
 * <h2>The test, for a top block at height y</h2>
 * <ol>
 *   <li>{@code t = (y - lowest) / (snow_line - lowest)}: 0 at the bottom of the band, 1 at the
 *       snow line. Outside the band the condition is false (above it, the snow line rule has
 *       already placed snow).</li>
 *   <li>A patch value: the 2D noise {@code atlasmountains:snow_patches} (blobs about a dozen
 *       blocks across, defined in the datapack) plus a small per-block jitter, so patch edges
 *       are ragged rather than smooth curves.</li>
 *   <li>A threshold that falls with height: {@code 0.9 - 1.6 t}. Near the bottom of the band
 *       only the strongest noise blobs pass, so snow is rare; near the snow line almost
 *       everything passes, so the patches merge into the solid cover above.</li>
 *   <li>Steepness: where our terrain's slope is above {@code ledge_slope} degrees the threshold
 *       is raised by {@code steep_penalty}. Steep faces still get a dusting, but far less than
 *       ledges and gentle ground, which is the look of the reference: bare rock faces between
 *       white ledges.</li>
 * </ol>
 * The slope comes from our terrain heightmap ({@link com.atlasmountains.core.RegionCache#slopeDegrees}),
 * the same one that decides where rock shows, so snow and rock agree about which ground is steep.
 * {@code mixed} picks the preset's terrain cache as in {@link TerrainHeightFunction}.
 *
 * @param snowLine     the height above which everything is snow already (the top of this band)
 * @param lowest       the lowest height a patch can reach
 * @param ledgeSlope   slopes gentler than this, in degrees, hold snow easily
 * @param steepPenalty how much harder it is for snow to settle on steeper ground (noise units;
 *                     the noise runs roughly from -1 to 1)
 * @param mixed        which preset's terrain cache to read
 */
public record SnowCoverCondition(int snowLine, int lowest, float ledgeSlope, float steepPenalty, boolean mixed)
        implements MaterialCondition {

    public static final MapCodec<SnowCoverCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.INT.fieldOf("snow_line").forGetter(SnowCoverCondition::snowLine),
            Codec.INT.fieldOf("lowest").forGetter(SnowCoverCondition::lowest),
            Codec.FLOAT.optionalFieldOf("ledge_slope", 38.0f).forGetter(SnowCoverCondition::ledgeSlope),
            Codec.FLOAT.optionalFieldOf("steep_penalty", 0.45f).forGetter(SnowCoverCondition::steepPenalty),
            Codec.BOOL.optionalFieldOf("mixed", true).forGetter(SnowCoverCondition::mixed)
    ).apply(i, SnowCoverCondition::new));

    /** Blob noise for the patches; defined in data/atlasmountains/worldgen/noise/snow_patches.json. */
    private static final ResourceKey<NormalNoise> PATCH_NOISE =
            ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("snow_patches"));

    /** Largest per-block jitter added to the patch noise, either way. */
    private static final float JITTER = 0.15f;

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier patches = context.getNoiseSampler(PATCH_NOISE, false);
        PositionalRandomFactory randoms = context.getOrCreateRandomFactory(AtlasMountainsMod.id("snow_cover"));
        float span = Math.max(1, snowLine - lowest);
        return () -> {
            int y = context.blockY();
            if (y < lowest || y >= snowLine) return false;
            float t = (y - lowest) / span;
            int x = context.blockX(), z = context.blockZ();
            double threshold = 0.9 - 1.6 * t;
            if (AtlasMountainsMod.cache(mixed).slopeDegrees(x, z) > ledgeSlope) threshold += steepPenalty;
            double jitter = (randoms.at(x, y, z).nextFloat() * 2 - 1) * JITTER;
            return patches.getAsDouble() + jitter > threshold;
        };
    }
}
