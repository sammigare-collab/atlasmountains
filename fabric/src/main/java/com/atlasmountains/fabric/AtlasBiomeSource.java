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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceKey;

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
 *   <li>With {@code match_surroundings} set (the mixed preset), before the step above: above its
 *       snow line our cold biome, and below it the biome vanilla would put here,
 *       so a mountain matches the land around it (grass colour, temperature, weather, mobs, trees,
 *       and through vanilla's ground rules sand, podzol or terracotta). Above {@code treeline_y} it
 *       is that biome's treeless twin. Biomes with no twin (rivers, beaches, oceans, caves) fall
 *       through to the next step.</li>
 *   <li>Otherwise warm or temperate by temperature.</li>
 *   <li>Above {@code treeline_y} each of our own biomes has a treeless twin ("_high"): biomes are
 *       3D, so the same column switches biome with height, and trees stop at the treeline.</li>
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
            Codec.DOUBLE.optionalFieldOf("snow_line_jitter", 25.0).forGetter(b -> b.snowLineJitter),
            Match.CODEC.optionalFieldOf("match_surroundings").forGetter(b -> java.util.Optional.ofNullable(b.match))
    ).apply(i, AtlasBiomeSource::new));

    /**
     * Settings for matching the surroundings (the mixed preset from Nov 2026; absent in older worlds,
     * which keep the three-biome behaviour). {@code snowLine}: the height above which the mountain
     * is our cold summit biome, the same height at which the material rules lay snow. Below it the
     * mountain takes the surrounding biome (a snowy one where the land around is snowy).
     */
    public record Match(int snowLine, List<Twin> twins) {
        public static final Codec<Match> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("snow_line").forGetter(Match::snowLine),
                Twin.CODEC.listOf().fieldOf("twins").forGetter(Match::twins)
        ).apply(i, Match::new));
    }

    /**
     * A vanilla land biome and its treeless twin (a copy with the trees taken out, generated by
     * tools/build_datapack.py). With twins listed, a mountain takes the biome of its surroundings:
     * the vanilla biome below the treeline, its twin above it.
     */
    public record Twin(Holder<Biome> vanilla, Holder<Biome> high) {
        public static final Codec<Twin> CODEC = RecordCodecBuilder.create(i -> i.group(
                Biome.CODEC.fieldOf("vanilla").forGetter(Twin::vanilla),
                Biome.CODEC.fieldOf("high").forGetter(Twin::high)
        ).apply(i, Twin::new));
    }

    /** Scratch for the flank test, one per thread. */
    private static final ThreadLocal<com.atlasmountains.core.RidgeSample> SCRATCH = ThreadLocal.withInitial(com.atlasmountains.core.RidgeSample::new);
    private final BiomeSource vanilla;
    private final Holder<Biome> cold, temperate, warm, coldHigh, temperateHigh, warmHigh;
    private final int treelineY;
    private final double coldBelow, warmAbove, maskThreshold;
    private final double snowLineBase, snowLineScale, snowLineFrozen, snowLineJitter;
    private final Match match;
    /** Twins by vanilla biome key; empty in worlds made before matching the surroundings existed. */
    private final Map<ResourceKey<Biome>, Holder<Biome>> twins = new HashMap<>();

    public AtlasBiomeSource(BiomeSource vanilla, Holder<Biome> cold, Holder<Biome> temperate, Holder<Biome> warm,
                            java.util.Optional<Holder<Biome>> coldHigh, java.util.Optional<Holder<Biome>> temperateHigh,
                            java.util.Optional<Holder<Biome>> warmHigh, int treelineY,
                            double coldBelow, double warmAbove, double maskThreshold,
                            double snowLineBase, double snowLineScale, double snowLineFrozen, double snowLineJitter,
                            java.util.Optional<Match> match) {
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
        this.match = match.orElse(null);
        if (this.match != null)
            for (Twin t : this.match.twins()) t.vanilla().unwrapKey().ifPresent(k -> twins.put(k, t.high()));
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() { return CODEC; }

    /** Every biome this source can return: vanilla's plus ours. The game uses it to plan feature placement. */
    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        Stream<Holder<Biome>> ours = Stream.of(cold, temperate, warm, coldHigh, temperateHigh, warmHigh).filter(java.util.Objects::nonNull);
        Stream<Holder<Biome>> twinBiomes = match == null ? Stream.empty() : match.twins().stream().map(Twin::high);
        return Stream.concat(Stream.concat(vanilla.possibleBiomes().stream(), ours), twinBiomes);
    }

    /** 26.x builds one resolver per climate sampler; ours wraps vanilla's so both read the same noise. */
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
        // Match the surroundings: above the snow line our cold summit biome; below it the biome
        // vanilla would put here, so grass colour, temperature, weather, mobs, trees and ground
        // (sand, podzol, terracotta, snow in snowy biomes...) are those of the land around the
        // mountain, and above the treeline its treeless twin. Biomes without a twin (rivers,
        // beaches, oceans, caves) fall through to our own three below.
        if (match != null) {
            if (by >= match.snowLine()) return high && coldHigh != null ? coldHigh : cold;
            Holder<Biome> v = base.getNoiseBiome(x, y, z);
            Holder<Biome> twin = v.unwrapKey().map(twins::get).orElse(null);
            if (twin != null) return high ? twin : v;
            // no twin (a river, beach or cave biome inside the mountain): our own biome for the
            // climate, without the temperature snow line, which would put snow far below the real one
            if (t < coldBelow) return high && coldHigh != null ? coldHigh : cold;
            if (t > warmAbove) return high && warmHigh != null ? warmHigh : warm;
            return high && temperateHigh != null ? temperateHigh : temperate;
        }
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
