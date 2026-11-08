package com.atlasmountains.fabric;

import com.atlasmountains.core.RangeLayout;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:stretched_erosion": the mod's erosion noise sampled in a
 * frame rotated to the range direction and stretched {@code erosionStretch} times along it, so
 * every erosion-driven province (flat, hilly, mountainous) is elongated the same way and the
 * mountain footprint runs along the ranges. Vanilla's own erosion is shifted_noise at xz_scale
 * 0.25; the small shift is dropped here.
 */
public final class StretchedErosionFunction implements DensityFunction.Base {

    public static final StretchedErosionFunction INSTANCE = new StretchedErosionFunction();
    public static final MapCodec<StretchedErosionFunction> MAP_CODEC = MapCodec.unit(INSTANCE);
    public static final CodecHolder<StretchedErosionFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private static volatile DoublePerlinNoiseSampler sampler;
    private static volatile double cos = 1, sin = 0, stretch = 1;

    private StretchedErosionFunction() {}

    /** Installed when the overworld loads. */
    public static void install(DoublePerlinNoiseSampler s, long seed, double stretchRatio) {
        double angle = RangeLayout.angle(seed);
        cos = Math.cos(angle);
        sin = Math.sin(angle);
        stretch = stretchRatio;
        sampler = s;
    }

    public static boolean installed() { return sampler != null; }

    /** Erosion at a block position, on vanilla's scale. 0 (flat) until installed. */
    public static double erosion(double x, double z) {
        DoublePerlinNoiseSampler s = sampler;
        if (s == null) return 0;
        double along = x * cos + z * sin;      // along the ranges: stretched
        double across = -x * sin + z * cos;
        return s.sample(along / stretch * 0.25, 0, across * 0.25);
    }

    @Override
    public double sample(NoisePos pos) {
        return erosion(pos.blockX(), pos.blockZ());
    }

    @Override
    public double minValue() { return -1; }

    @Override
    public double maxValue() { return 1; }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() { return CODEC_HOLDER; }
}
