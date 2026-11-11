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
 *
 * <h2>Choosing a biome inside the mountains</h2>
 * <ol>
 *   <li>Outside the mask, or at the foot of an outer flank where vanilla's surface shows
 *       ({@link com.atlasmountains.core.RegionCache#onOurSlope}), vanilla decides.</li>
 *   <li>Above the snow line the cold biome; the snow line rises with temperature (see the
 *       comment in getBiome), so cold regions are snowy down to the valleys and warm ones only
 *       near the summits.</li>
 *   <li>Otherwise warm or temperate by temperature.</li>
 *   <li>Above {@code treeline_y} each biome has a treeless twin ("_high"): biomes are 3D, so the
 *       same column switches biome with height, and trees stop at the treeline.</li>
 * </ol>
 * The three biomes are vanilla copies (snowy taiga, meadow, savanna) with the vegetation
 * replaced by sparse spruce, built by tools/build_datapack.py.
 *
 * <h2>Saved settings</h2>
 * Every field below is written into each world's level.dat when the world is created and read
 * back from there afterwards, so a world keeps the values it was made with even after the mod
 * changes its defaults. Changing a default only affects new worlds; changing the formula
 * changes old worlds, so the formula is left exactly as it is.
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

    /** Scratch for the flank test, one per thread. */
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

    /** Every biome this source can return: vanilla's plus ours. The game uses it to plan feature placement. */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> ours = Stream.of(cold, temperate, warm, coldHigh, temperateHigh, warmHigh).filter(java.util.Objects::nonNull);
        return Stream.concat(vanilla.possibleBiomes().stream(), ours);
    }

    /** 26.x builds one resolver per climate sampler; ours wraps vanilla's so both read the same noise. */
    @Override
    public BiomeResolver createResolver(Climate.Sampler sampler) {
        BiomeResolver base = vanilla.createResolver(sampler);
        return (x, y, z) -> getBiome(x, y, z, sampler, base);
    }

    private static final double OUR_BIOME_ABOVE = 30;

    private Holder<Biome> getBiome(int x, int y, int z, Climate.Sampler noise, BiomeResolver base) {
        int bx = x << 2, bz = z << 2;   // biome coordinates are 4-block quarts
        var cache = AtlasMountainsMod.cache();
        // Our biomes start OUR_BIOME_ABOVE blocks up the mountain; the foot below that, and the
        // ground between ranges, keeps whatever vanilla biome the land around has, so a mountain's
        // skirt matches its surroundings (Sammi's foot-ring diagram, Nov 2026).
        if (cache.mask(bx, bz) <= 0
                || cache.maskedBaseHeight(bx, bz, SCRATCH.get()) < cache.settings().valleyFloorHeight + OUR_BIOME_ABOVE) {
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
