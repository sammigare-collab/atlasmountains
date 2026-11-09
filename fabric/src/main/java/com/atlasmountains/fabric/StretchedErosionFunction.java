package com.atlasmountains.fabric;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.synth.Noise;

/**
 * Density function "atlasmountains:stretched_erosion": the mod's erosion noise sampled in a
 * frame rotated to the range direction and stretched {@code erosionStretch} times along it (1 =
 * no stretch). Vanilla's own erosion is noise at xz_scale 0.25 with a small shift; the shift is
 * dropped here. Also read directly by the mountain layout for vanilla's mountain footprint.
 */
public enum StretchedErosionFunction implements ColumnFunction {
    INSTANCE;

    public static final MapCodec<StretchedErosionFunction> CODEC = MapCodec.unit(INSTANCE);

    private static volatile Noise sampler;
    private static volatile double cos = 1, sin = 0, stretch = 1;

    /**
     * Installed when the overworld loads. The frame is rotated by a seed-chosen angle (the old
     * parallel layout's range direction). With erosionStretch 1 the stretch does nothing, but the
     * rotation still decides where the erosion field, and with it every mountain province, lies.
     */
    public static void install(Noise s, long seed, double stretchRatio) {
        double angle = com.atlasmountains.core.Hash.unit(com.atlasmountains.core.Hash.hash(seed, 101)) * Math.PI;
        cos = Math.cos(angle);
        sin = Math.sin(angle);
        stretch = stretchRatio;
        sampler = s;
    }

    public static boolean installed() { return sampler != null; }

    /** Erosion at a block position, on vanilla's scale. 0 (flat) until installed. */
    public static double erosion(double x, double z) {
        Noise s = sampler;
        if (s == null) return 0;
        double along = x * cos + z * sin;      // along the ranges: stretched
        double across = -x * sin + z * cos;
        return s.get(along / stretch * 0.25, 0, across * 0.25);
    }

    @Override
    public double at(int x, int z) { return erosion(x, z); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
