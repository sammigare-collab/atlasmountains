package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import java.util.stream.Stream;

/**
 * Biome source "atlasmountains:mask": inside the mountain mask one of three biomes by the
 * temperature noise (cold below coldBelow, warm above warmAbove, temperate between); outside,
 * whatever the wrapped vanilla biome source says.
 */
public final class AtlasBiomeSource extends BiomeSource {

    public static final MapCodec<AtlasBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiomeSource.CODEC.fieldOf("vanilla").forGetter(b -> b.vanilla),
            Biome.CODEC.fieldOf("cold").forGetter(b -> b.cold),
            Biome.CODEC.fieldOf("temperate").forGetter(b -> b.temperate),
            Biome.CODEC.fieldOf("warm").forGetter(b -> b.warm),
            Biome.CODEC.optionalFieldOf("cold_high").forGetter(b -> java.util.Optional.ofNullable(b.coldHigh)),
            Biome.CODEC.optionalFieldOf("temperate_high").forGetter(b -> java.util.Optional.ofNullable(b.temperateHigh)),
            Biome.CODEC.optionalFieldOf("warm_high").forGetter(b -> java.util.Optional.ofNullable(b.warmHigh)),
            Codec.INT.optionalFieldOf("treeline_y", 100000).forGetter(b -> b.treelineY),
            Codec.DOUBLE.optionalFieldOf("cold_below", -0.2).forGetter(b -> b.coldBelow),
            Codec.DOUBLE.optionalFieldOf("warm_above", 0.25).forGetter(b -> b.warmAbove),
            Codec.DOUBLE.optionalFieldOf("mask_threshold", 0.5).forGetter(b -> b.maskThreshold),
            Codec.DOUBLE.optionalFieldOf("snow_line_base", 64.0).forGetter(b -> b.snowLineBase),
            Codec.DOUBLE.optionalFieldOf("snow_line_scale", 700.0).forGetter(b -> b.snowLineScale),
            Codec.DOUBLE.optionalFieldOf("snow_line_frozen", -0.2).forGetter(b -> b.snowLineFrozen),
            Codec.DOUBLE.optionalFieldOf("snow_line_jitter", 25.0).forGetter(b -> b.snowLineJitter)
    ).apply(i, AtlasBiomeSource::new));

    private static final ThreadLocal<com.atlasmountains.core.RidgeSample> SCRATCH = ThreadLocal.withInitial(com.atlasmountains.core.RidgeSample::new);
    private final BiomeSource vanilla;
    private final Holder<Biome> cold, temperate, warm, coldHigh, temperateHigh, warmHigh;
    private final int treelineY;
    private final double coldBelow, warmAbove, maskThreshold;
    private final double snowLineBase, snowLineScale, snowLineFrozen, snowLineJitter;

    public AtlasBiomeSource(BiomeSource vanilla, Holder<Biome> cold, Holder<Biome> temperate, Holder<Biome> warm,
                            java.util.Optional<Holder<Biome>> coldHigh, java.util.Optional<Holder<Biome>> temperateHigh,
                            java.util.Optional<Holder<Biome>> warmHigh, int treelineY,
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
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> ours = Stream.of(cold, temperate, warm, coldHigh, temperateHigh, warmHigh).filter(java.util.Objects::nonNull);
        return Stream.concat(vanilla.possibleBiomes().stream(), ours);
    }

    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        BiomeResolver base = vanilla.createResolver(sampler);
        return (x, y, z) -> getBiome(x, y, z, sampler, base);
    }

    private Holder<Biome> getBiome(int x, int y, int z, Climate.Sampler noise, BiomeResolver base) {
        int bx = x << 2, bz = z << 2;   // biome coordinates are 4-block quarts
        var cache = AtlasMountainsMod.cache();
        if (cache.mask(bx, bz) < maskThreshold || !cache.onOurSlope(bx, bz, 6.0, SCRATCH.get())) {
            return base.getNoiseBiome(x, y, z);
        }
        double t = noise.temperature().sampleValue(bx, y << 2, bz);
        int by = y << 2;
        boolean high = by >= treelineY;   // above the treeline: the treeless twin
        // Snow line: the cold (snowy) biome starts at a height that rises with the temperature, so
        // snow wraps the mountains as a ragged contour instead of a hard line across the valleys.
        // Below snow_line_frozen it is at the floor (snow everywhere); at cold_below it sits
        // (cold_below - frozen) * scale above the base. A noise roughens the edge.
        // value2 is -1..1, so the jitter spans -3 to +1 times snow_line_jitter (on average one
        // jitter below the base). These parameters are stored in each world's level.dat, so the
        // formula is kept exactly as the worlds were created with it.
        double n = com.atlasmountains.core.Hash.value2(0x5A0DL, bx / 80.0, bz / 80.0);
        double jitter = (2 * n - 1) * snowLineJitter;
        double snowY = snowLineBase + (t - snowLineFrozen) * snowLineScale + jitter;
        if (by >= snowY) return high && coldHigh != null ? coldHigh : cold;
        if (t > warmAbove) return high && warmHigh != null ? warmHigh : warm;
        return high && temperateHigh != null ? temperateHigh : temperate;
    }
}
