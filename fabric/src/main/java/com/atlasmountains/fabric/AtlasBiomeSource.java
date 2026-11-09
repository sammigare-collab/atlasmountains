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
            Codec.DOUBLE.optionalFieldOf("mask_threshold", 0.5).forGetter(b -> b.maskThreshold),
            Codec.DOUBLE.optionalFieldOf("snow_line_base", 64.0).forGetter(b -> b.snowLineBase),
            Codec.DOUBLE.optionalFieldOf("snow_line_scale", 300.0).forGetter(b -> b.snowLineScale),
            Codec.DOUBLE.optionalFieldOf("snow_line_frozen", -0.2).forGetter(b -> b.snowLineFrozen),
            Codec.DOUBLE.optionalFieldOf("snow_line_jitter", 25.0).forGetter(b -> b.snowLineJitter)
    ).apply(i, AtlasBiomeSource::new));

    private static final ThreadLocal<com.atlasmountains.core.RangeLayout.Sample> SCRATCH = ThreadLocal.withInitial(com.atlasmountains.core.RangeLayout.Sample::new);
    private final BiomeSource vanilla;
    private final RegistryEntry<Biome> cold, temperate, warm, coldHigh, temperateHigh, warmHigh;
    private final int treelineY;
    private final double coldBelow, warmAbove, maskThreshold;
    private final double snowLineBase, snowLineScale, snowLineFrozen, snowLineJitter;

    public AtlasBiomeSource(BiomeSource vanilla, RegistryEntry<Biome> cold, RegistryEntry<Biome> temperate, RegistryEntry<Biome> warm,
                            java.util.Optional<RegistryEntry<Biome>> coldHigh, java.util.Optional<RegistryEntry<Biome>> temperateHigh,
                            java.util.Optional<RegistryEntry<Biome>> warmHigh, int treelineY,
                            double coldBelow, double warmAbove, double maskThreshold,
                            double snowLineBase, double snowLineScale, double snowLineFrozen, double snowLineJitter) {
        this.vanilla = vanilla;
        this.cold = cold;
        this.temperate = temperate;
        this.warm = warm;
        this.coldHigh = coldHigh.orElse(null);
        this.temperateHigh = temperateHigh.orElse(null);
        this.warmHigh = warmHigh.orElse(null);
        this.treelineY = treelineY;
        this.coldBelow = coldBelow;
        this.snowLineBase = snowLineBase; this.snowLineScale = snowLineScale;
        this.snowLineFrozen = snowLineFrozen; this.snowLineJitter = snowLineJitter;
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
        int by = y << 2;
        boolean high = by >= treelineY;   // above the treeline: the treeless twin
        // Snow line: the cold (snowy) biome starts at a height that rises with the temperature, so
        // snow wraps the mountains as a ragged contour instead of a hard line across the valleys.
        // Below snow_line_frozen it is at the floor (snow everywhere); at cold_below it sits
        // (cold_below - frozen) * scale above the base. A small noise roughens the edge.
        double jitter = (com.atlasmountains.core.Hash.value2(0x5A0DL, bx / 80.0, bz / 80.0) - 0.5) * 2 * snowLineJitter;
        double snowY = snowLineBase + (t - snowLineFrozen) * snowLineScale + jitter;
        if (by >= snowY) return high && coldHigh != null ? coldHigh : cold;
        if (t > warmAbove) return high && warmHigh != null ? warmHigh : warm;
        return high && temperateHigh != null ? temperateHigh : temperate;
    }
}
