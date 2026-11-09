package com.atlasmountains.fabric;

import com.atlasmountains.core.RangeLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:terrain_height": the terrain height at (x, z).
 * <p>
 * {@code eroded: true} (default) returns the eroded, cached, upsampled height and is what the
 * terrain itself is built from. {@code eroded: false} returns the cheap analytic model (no
 * erosion, within a few blocks of the real surface) and is for the biome "depth" input: vanilla
 * samples the biome source thousands of blocks away when it places stronghold rings, and that
 * must not trigger region erosion.
 * <p>
 * {@code mask_relief: true} scales the relief by the mountain mask (mixed world), so ranges fade
 * into hills at the zone edges rather than meeting vanilla terrain as a cliff.
 * <p>
 * {@code chains: true} (default) uses the mixed preset's cache; {@code false} the "everywhere" preset's.
 */
public record TerrainHeightFunction(boolean eroded, boolean maskRelief, boolean chains) implements ColumnFunction {

    public static final MapCodec<TerrainHeightFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("eroded", true).forGetter(TerrainHeightFunction::eroded),
            Codec.BOOL.optionalFieldOf("mask_relief", false).forGetter(TerrainHeightFunction::maskRelief),
            Codec.BOOL.optionalFieldOf("chains", true).forGetter(TerrainHeightFunction::chains)
    ).apply(i, TerrainHeightFunction::new));

    private static final ThreadLocal<RangeLayout.Sample> SCRATCH = ThreadLocal.withInitial(RangeLayout.Sample::new);

    @Override
    public double at(int x, int z) {
        var cache = AtlasMountainsMod.cache(chains);
        if (maskRelief) {
            return eroded ? cache.maskedHeight(x, z) : cache.maskedBaseHeight(x, z, SCRATCH.get());
        }
        return eroded ? cache.height(x, z) : cache.generator().heightAt(x, z, SCRATCH.get());
    }

    @Override
    public Interval range() { return Interval.of(-128, 1024); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
