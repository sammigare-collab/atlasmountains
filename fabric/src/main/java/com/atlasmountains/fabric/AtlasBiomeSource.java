package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.densityfunction.DensityFunction;

import java.util.stream.Stream;

/**
 * Biome source "atlasmountains:mask": inside the mountain mask one of three biomes by the
 * temperature noise (cold below coldBelow, warm above warmAbove, temperate between); outside,
 * whatever the wrapped vanilla biome source says.
 */
public final class AtlasBiomeSource extends BiomeSource {

    public static final MapCodec<AtlasBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("vanilla").forGetter(b -> b.vanilla),
            Biome.REGISTRY_CODEC.fieldOf("cold").forGetter(b -> b.cold),
            Biome.REGISTRY_CODEC.fieldOf("temperate").forGetter(b -> b.temperate),
            Biome.REGISTRY_CODEC.fieldOf("warm").forGetter(b -> b.warm),
            Codec.DOUBLE.optionalFieldOf("cold_below", -0.2).forGetter(b -> b.coldBelow),
            Codec.DOUBLE.optionalFieldOf("warm_above", 0.25).forGetter(b -> b.warmAbove),
            Codec.DOUBLE.optionalFieldOf("mask_threshold", 0.5).forGetter(b -> b.maskThreshold)
    ).apply(i, AtlasBiomeSource::new));

    private final BiomeSource vanilla;
    private final RegistryEntry<Biome> cold, temperate, warm;
    private final double coldBelow, warmAbove, maskThreshold;

    public AtlasBiomeSource(BiomeSource vanilla, RegistryEntry<Biome> cold, RegistryEntry<Biome> temperate, RegistryEntry<Biome> warm,
                            double coldBelow, double warmAbove, double maskThreshold) {
        this.vanilla = vanilla;
        this.cold = cold;
        this.temperate = temperate;
        this.warm = warm;
        this.coldBelow = coldBelow;
        this.warmAbove = warmAbove;
        this.maskThreshold = maskThreshold;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.concat(vanilla.getBiomes().stream(), Stream.of(cold, temperate, warm));
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        int bx = x << 2, bz = z << 2;   // biome coordinates are 4-block quarts
        if (AtlasMountainsMod.cache().mask(bx, bz) < maskThreshold) {
            return vanilla.getBiome(x, y, z, noise);
        }
        double t = noise.temperature().sample(new DensityFunction.UnblendedNoisePos(bx, y << 2, bz));
        if (t < coldBelow) return cold;
        if (t > warmAbove) return warm;
        return temperate;
    }
}
