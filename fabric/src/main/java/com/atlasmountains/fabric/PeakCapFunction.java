package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:peak_cap": the level, in vanilla's offset units (y = 128 + 128 *
 * value), above which the biomes on a peak are vanilla's cold peak biomes. It is {@code above}
 * blocks over the snow line at this column ({@link SnowLine}), so the cold biome (snowfall, and
 * vanilla's own thin snow layer on any bare block) only ever covers ground the peaks' own snow
 * has already whitened. Where there are no peaks about it returns 8, far above the world.
 * <p>
 * Used by the biome climate (build_overhaul.py, step 6). The snow itself is placed by the peaks'
 * surface rule ({@link PeakSnowCondition}), not by the biome.
 */
public record PeakCapFunction(double above) implements ColumnFunction {
    public static final MapCodec<PeakCapFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("above").forGetter(PeakCapFunction::above)
    ).apply(i, PeakCapFunction::new));

    /** Returned where there is no snow line: far above the world's top. */
    private static final double NONE = 8.0;

    @Override
    public double at(int blockX, int blockZ) {
        HornsFunction horns = HornsFunction.current;
        if (horns == null) return NONE;
        double line = SnowLine.at(horns, blockX, blockZ);
        return Double.isNaN(line) ? NONE : (line + above - 128.0) / 128.0;
    }

    @Override
    public Interval range() { return Interval.of(-4.0f, (float) NONE); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
