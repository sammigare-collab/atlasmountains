package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:peak_rocky": 1 where a mountain's surface is bare rock at this
 * column, 0 where it is grass or there is no mountain ({@link ErodedPeaks#rocky}). The data pack
 * uses it to keep vanilla's own biome, with its grass and trees, on the gentle lower slopes; the
 * surface rule asks ErodedPeaks the same question, so biome and surface agree.
 */
public record PeakRockyFunction() implements ColumnFunction {
    public static final MapCodec<PeakRockyFunction> CODEC = MapCodec.unit(new PeakRockyFunction());

    @Override
    public double at(int blockX, int blockZ) {
        HornsFunction horns = HornsFunction.current;
        return horns == null ? 0.0 : ErodedPeaks.rocky(horns, blockX, blockZ);
    }

    @Override
    public Interval range() { return Interval.of(0.0f, 1.0f); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
