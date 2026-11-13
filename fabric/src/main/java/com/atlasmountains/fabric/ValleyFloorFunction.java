package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:valley_floor": the high valley floor the peaks stand in
 * ({@link HornsFunction#valleyFloor}). With {@code part} "level" it is the floor's level in
 * vanilla's offset units (y = 128 + 128 * value), far underground where there are no peaks about;
 * with "distance" it is how far off the peaks are, in blocks. The data pack (build_overhaul.py,
 * step 2) raises vanilla's land to the level where the peaks are near enough and the sea is not.
 */
public record ValleyFloorFunction(String part) implements ColumnFunction {
    public static final MapCodec<ValleyFloorFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.fieldOf("part").forGetter(ValleyFloorFunction::part)
    ).apply(i, ValleyFloorFunction::new));

    /** Returned for the level where there is no floor: far below the world. */
    private static final double NONE = -8.0;

    @Override
    public double at(int blockX, int blockZ) {
        HornsFunction horns = HornsFunction.current;
        boolean level = part.equals("level");
        if (horns == null) return level ? NONE : HornsFunction.FLOOR_NONE;
        double[] f = horns.valleyFloor(blockX, blockZ);
        if (!level) return f[1];
        return Double.isNaN(f[0]) ? NONE : (f[0] - 128.0) / 128.0;
    }

    @Override
    public Interval range() { return part.equals("level") ? Interval.of(-8.0f, 4.0f) : Interval.of(0.0f, (float) HornsFunction.FLOOR_NONE); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
