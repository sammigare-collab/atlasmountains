package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:peak_cap": the level, in vanilla's offset units (y = 128 + 128 *
 * value), where the snow cap of the peak at this column begins. The cap is the top {@code cap}
 * fraction of the height of whichever piece (a peak's main pyramid or one of its shoulders) forms
 * the ground here, so every peak and shoulder has a cap in proportion to its own size. Where no
 * peak reaches it returns 8, far above the world, so nothing there is ever in a cap.
 * <p>
 * Used by the biome climate (build_overhaul.py, step 6): above this line the biomes see vanilla's
 * own temperature, so a peak in cold country has vanilla's cold peak biome (and so snow) in its
 * cap only, and bare rock biome below it.
 */
public record PeakCapFunction(double cap) implements ColumnFunction {
    public static final MapCodec<PeakCapFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("cap").forGetter(PeakCapFunction::cap)
    ).apply(i, PeakCapFunction::new));

    /** Returned where no peak reaches: far above the world's top. */
    private static final double NONE = 8.0;

    @Override
    public double at(int blockX, int blockZ) {
        HornsFunction horns = HornsFunction.current;
        if (horns == null) return NONE;
        double[] c = horns.column(blockX, blockZ);
        if (c[0] <= HornsFunction.NO_PEAK + 1 || c[2] <= 0) return NONE;
        return c[1] + (1 - cap) * c[2] / 128.0;
    }

    @Override
    public Interval range() { return Interval.of(-4.0f, (float) NONE); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
