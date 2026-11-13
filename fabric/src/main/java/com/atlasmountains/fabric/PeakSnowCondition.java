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
 * Material condition "atlasmountains:peak_snow": true where a block on a peak is snow-covered.
 * <p>
 * Snow the way it lies on real rock peaks, as far as blocks allow:
 * <ol>
 *   <li>it starts at about one height across a whole range ({@link SnowLine}), not at a share of
 *       each peak's own height;</li>
 *   <li>the edge is ragged: it wanders up and down by {@code edge} blocks with a slow noise;</li>
 *   <li>it lies lower in the gullies and is blown off the ribs: the line is {@code gully} blocks
 *       lower in the bottom of a full-depth gully and as much higher on a rib's crest, so tongues
 *       of snow run down the gullies below the cap and dark ribs stand out of its lower part;</li>
 *   <li>just above the line it is in patches, filling in over the next {@code fringe} blocks up;
 *       above that the cover is complete.</li>
 * </ol>
 * 0.4.7 and 0.4.8 put a cap on the top 28% of every peak and shoulder, with a few holes in it.
 */
public record PeakSnowCondition(double edge, double gully, double fringe) implements MaterialCondition {
    public static final MapCodec<PeakSnowCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("edge", 9.0).forGetter(PeakSnowCondition::edge),
            Codec.DOUBLE.optionalFieldOf("gully", 14.0).forGetter(PeakSnowCondition::gully),
            Codec.DOUBLE.optionalFieldOf("fringe", 12.0).forGetter(PeakSnowCondition::fringe)
    ).apply(i, PeakSnowCondition::new));

    private static final ResourceKey<NormalNoise> EDGE_NOISE = ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("snow_edge"));
    private static final ResourceKey<NormalNoise> PATCH_NOISE = ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("snow_patches"));
    /** A gully or rib this many blocks deep counts as full depth for the snow. */
    private static final double FULL_RIB = 12.0;

    @Override
    public MapCodec<? extends MaterialCondition> codec() { return CODEC; }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier edgeNoise = context.getNoiseSampler(EDGE_NOISE, false);   // 2-D: the same up a whole column
        DoubleSupplier patchNoise = context.getNoiseSampler(PATCH_NOISE, true);
        return () -> {
            HornsFunction horns = HornsFunction.current;
            if (horns == null) return false;
            int x = context.blockX(), z = context.blockZ();
            double line = SnowLine.at(horns, x, z);
            if (Double.isNaN(line)) return false;
            double above = context.blockY() - line;
            if (above < -(edge + gully) * 1.5) return false;                         // far below: skip the noises
            // on the eroded mountains: real gullies and sheltered hollows both hold snow lower down
            double depth = HornsFunction.ERODED ? horns.column(x, z)[3] + 3 * ErodedPeaks.shelter(horns, x, z) : horns.column(x, z)[3];
            double rib = Math.max(-1, Math.min(1, depth / FULL_RIB));
            above += edge * Math.max(-1.5, Math.min(1.5, 2 * edgeNoise.getAsDouble())) + gully * rib;
            if (above < 0) return false;
            if (above >= fringe) return true;
            // patches: about a quarter covered right at the line, filling in with height
            return patchNoise.getAsDouble() > 0.25 - 0.85 * above / fringe;
        };
    }
}
