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
            Biome.REGISTRY_CODEC.optionalFieldOf("cold_high").forGetter(b -> java.util.Optional.ofNullable(b.coldHigh)),
            Biome.REGISTRY_CODEC.optionalFieldOf("temperate_high").forGetter(b -> java.util.Optional.ofNullable(b.temperateHigh)),
            Biome.REGISTRY_CODEC.optionalFieldOf("warm_high").forGetter(b -> java.util.Optional.ofNullable(b.warmHigh)),
            Codec.INT.optionalFieldOf("treeline_y", 100000).forGetter(b -> b.treelineY),
            Codec.DOUBLE.optionalFieldOf("cold_below", -0.2).forGetter(b -> b.coldBelow),
            Codec.DOUBLE.optionalFieldOf("warm_above", 0.25).forGetter(b -> b.warmAbove),
            Codec.DOUBLE.optionalFieldOf("mask_threshold", 0.5).forGetter(b -> b.maskThreshold)
    ).apply(i, AtlasBiomeSource::new));

    private static final ThreadLocal<com.atlasmountains.core.RangeLayout.Sample> SCRATCH = ThreadLocal.withInitial(com.atlasmountains.core.RangeLayout.Sample::new);
    private final BiomeSource vanilla;
    private final RegistryEntry<Biome> cold, temperate, warm, coldHigh, temperateHigh, warmHigh;
    private final int treelineY;
    private final double coldBelow, warmAbove, maskThreshold;

    public AtlasBiomeSource(BiomeSource vanilla, RegistryEntry<Biome> cold, RegistryEntry<Biome> temperate, RegistryEntry<Biome> warm,
                            java.util.Optional<RegistryEntry<Biome>> coldHigh, java.util.Optional<RegistryEntry<Biome>> temperateHigh,
                            java.util.Optional<RegistryEntry<Biome>> warmHigh, int treelineY,
                            double coldBelow, double warmAbove, double maskThreshold) {
        this.vanilla = vanilla;
        this.cold = cold;
        this.temperate = temperate;
        this.warm = warm;
        this.coldHigh = coldHigh.orElse(null);
        this.temperateHigh = temperateHigh.orElse(null);
        this.warmHigh = warmHigh.orElse(null);
        this.treelineY = treelineY;
        this.coldBelow = coldBelow;
        this.warmAbove = warmAbove;
        this.maskThreshold = maskThreshold;
    }

    @Override
    protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }

    @Override
    protected Stream<RegistryEntry<Biome>> biomeStream() {
        Stream<RegistryEntry<Biome>> ours = Stream.of(cold, temperate, warm, coldHigh, temperateHigh, warmHigh).filter(java.util.Objects::nonNull);
        return Stream.concat(vanilla.getBiomes().stream(), ours);
    }

    @Override
    public RegistryEntry<Biome> getBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise) {
        int bx = x << 2, bz = z << 2;   // biome coordinates are 4-block quarts
        var cache = AtlasMountainsMod.cache();
        if (cache.mask(bx, bz) < maskThreshold || !cache.onOurSlope(bx, bz, 6.0, SCRATCH.get())) {
            return vanilla.getBiome(x, y, z, noise);
        }
        double t = noise.temperature().sample(new DensityFunction.UnblendedNoisePos(bx, y << 2, bz));
        boolean high = (y << 2) >= treelineY;   // above the treeline: the treeless twin
        if (t < coldBelow) return high && coldHigh != null ? coldHigh : cold;
        if (t > warmAbove) return high && warmHigh != null ? warmHigh : warm;
        return high && temperateHigh != null ? temperateHigh : temperate;
    }
}
