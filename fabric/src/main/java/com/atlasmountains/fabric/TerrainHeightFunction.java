package com.atlasmountains.fabric;

import com.atlasmountains.core.RangeLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.dynamic.CodecHolder;
import net.minecraft.world.gen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:terrain_height": the terrain height at (x, z).
 * <p>
 * {@code eroded: true} (default) returns the eroded, cached, upsampled height and is what the
 * terrain itself is built from. {@code eroded: false} returns the cheap analytic model (steps
 * 1 to 4, no erosion, within a few blocks of the real surface) and is for the biome "depth"
 * input: vanilla samples the biome source thousands of blocks away when it places stronghold
 * rings, and that must not trigger region erosion.
 * <p>
 * {@code mask_relief: true} scales the relief by the mountain mask (mixed world), so ranges fade
 * into hills at the zone edges rather than meeting vanilla terrain as a cliff.
 * <p>
 * Has no arguments and no y dependence, so DensityFunction.Base's default fill and apply are
 * correct.
 */
public record TerrainHeightFunction(boolean eroded, boolean maskRelief) implements DensityFunction.Base {

    public static final MapCodec<TerrainHeightFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("eroded", true).forGetter(TerrainHeightFunction::eroded),
            Codec.BOOL.optionalFieldOf("mask_relief", false).forGetter(TerrainHeightFunction::maskRelief)
    ).apply(i, TerrainHeightFunction::new));
    public static final CodecHolder<TerrainHeightFunction> CODEC_HOLDER = CodecHolder.of(MAP_CODEC);

    private static final ThreadLocal<RangeLayout.Sample> SCRATCH = ThreadLocal.withInitial(RangeLayout.Sample::new);

    @Override
    public double sample(NoisePos pos) {
        var cache = AtlasMountainsMod.cache();
        if (maskRelief) {
            return eroded ? cache.maskedHeight(pos.blockX(), pos.blockZ())
                          : cache.maskedBaseHeight(pos.blockX(), pos.blockZ(), SCRATCH.get());
        }
        return eroded ? cache.height(pos.blockX(), pos.blockZ())
                      : cache.generator().heightAt(pos.blockX(), pos.blockZ(), SCRATCH.get());
    }

    @Override
    public double minValue() {
        return -64;
    }

    @Override
    public double maxValue() {
        return 1024;
    }

    @Override
    public CodecHolder<? extends DensityFunction> getCodecHolder() {
        return CODEC_HOLDER;
    }
}
