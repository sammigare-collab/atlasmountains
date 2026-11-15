package com.atlasmountains.fabric;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.concurrent.ConcurrentHashMap;

/**
 * How strongly mountain country holds at one peak's summit: 0 (none) to 1 (its heart).
 *
 * The data pack describes mountain country as a density function,
 * {@code atlasmountains:overworld/strength} (low erosion, inland, away from rivers). A peak
 * needs that value at ONE place, its summit, so that the whole peak is one size and keeps its
 * angles. The first versions multiplied every column of a peak by the value at that column
 * instead, which squashed any peak standing where the value was changing into a rounded hump
 * (Sammi's screenshots, 12 Nov 2026).
 *
 * A density function can only be asked about the column being generated, so this keeps a
 * terrain sampler of its own, asks it about the summit's column and remembers the answer per
 * peak. The answer depends only on the seed and the place, so it is the same on every thread
 * and every run.
 */
final class PeakStrength {
    private static final ResourceKey<DensityFunction> KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/strength"));
    /**
     * The land's target height (in offset units), which a peak also needs at its summit: see landAt. Vanilla's own,
     * except along rivers in mountain country, which the data pack cuts down to water level since 0.6.1 ("overworld/land").
     */
    private static final ResourceKey<DensityFunction> LAND_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/land"));
    /** Vanilla's own target height, before the mod cuts its rivers, and how far the mod cuts (0.6.1). */
    private static final ResourceKey<DensityFunction> VANILLA_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/vanilla_offset"));
    private static final ResourceKey<DensityFunction> CUT_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/river_cut"));
    /** The river cut is nothing where |weirdness| is this or more. MUST be at least RIVER_CUT_NONE of tools/build_overhaul.py (0.16). */
    private static final double CUT_REACH = 0.2;
    private static volatile DensityFunction vanillaFunction, cutFunction;
    private static final ResourceKey<DensityFunction> ABOVE_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/peak_height"));
    /** Vanilla's weirdness map, whose zero line is where rivers run: see RiverDistance. */
    private static final ResourceKey<DensityFunction> WEIRDNESS_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/ridges"));
    private static final int MAX_REMEMBERED = 400_000;

    private static volatile RandomState state;
    private static volatile DensityFunction function;
    private static volatile DensityFunction landFunction;
    private static volatile DensityFunction aboveFunction;
    private static volatile DensityFunction weirdnessFunction;
    private static volatile DensityFunction temperatureFunction;
    private static volatile DensityFunction countryFunction;
    private static final ResourceKey<DensityFunction> COUNTRY_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.fromNamespaceAndPath(AtlasMountainsMod.MOD_ID, "overworld/amp"));
    private static final ResourceKey<DensityFunction> TEMPERATURE_KEY =
            ResourceKey.create(Registries.DENSITY_FUNCTION, Identifier.withDefaultNamespace("overworld/temperature"));
    private static final ConcurrentHashMap<Long, Float> remembered = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Long, Float> rememberedLand = new ConcurrentHashMap<>();

    private PeakStrength() {
    }

    /**
     * Called as a server starts, and with null as it stops. Builds a terrain sampler of its own
     * from the overworld's settings and the seed. It cannot wait and borrow the world's: the
     * game starts placing strongholds (which looks at biomes, which look at peaks) while the
     * overworld is still being put together.
     */
    static void world(MinecraftServer server) {
        state = null;
        function = null;
        landFunction = null;
        vanillaFunction = null;
        cutFunction = null;
        aboveFunction = null;
        weirdnessFunction = null;
        temperatureFunction = null;
        countryFunction = null;
        RiverDistance.reset();
        HornsFunction.forget();
        Massif.forget();
        ErodedPeaks.reset();
        remembered.clear();
        rememberedLand.clear();
        if (server == null) return;
        var registries = server.registryAccess();
        var overworld = registries.lookupOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        var wanted = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(KEY);
        var land = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(LAND_KEY);
        var above = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(ABOVE_KEY);
        var weird = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(WEIRDNESS_KEY);
        if (overworld.isEmpty() || wanted.isEmpty() || land.isEmpty() || !(overworld.get().value().generator() instanceof NoiseBasedChunkGenerator generator)) {
            AtlasMountainsMod.LOGGER.info("this world's overworld is not built from terrain noise; no peaks");
            return;
        }
        long seed = server.getWorldGenSettings().options().seed();
        state = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, generator.generatorSettings().value());
        function = wanted.get().value();
        landFunction = land.get().value();
        vanillaFunction = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(VANILLA_KEY).map(h -> h.value()).orElse(null);
        cutFunction = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(CUT_KEY).map(h -> h.value()).orElse(null);
        aboveFunction = above.map(h -> h.value()).orElse(null);
        weirdnessFunction = weird.map(h -> h.value()).orElse(null);
        temperatureFunction = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(TEMPERATURE_KEY).map(h -> h.value()).orElse(null);
        countryFunction = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(COUNTRY_KEY).map(h -> h.value()).orElse(null);
        // and once more now the maps can be read: anything worked out by another thread while they could not is wrong
        RiverDistance.reset();
        HornsFunction.forget();
        Massif.forget();
        ErodedPeaks.reset();
        remembered.clear();
        rememberedLand.clear();
    }

    /** Strength for the peak of cell (ix, iz), whose summit is at block (x, z). */
    static double at(long ix, long iz, int x, int z) {
        long key = (ix << 32) ^ (iz & 0xFFFFFFFFL);
        Float known = remembered.get(key);
        if (known != null) return known;
        RandomState rs = state;
        DensityFunction fn = function;
        if (rs == null || fn == null) return 0.0;
        float value = rs.sampleBlockValueUncached(fn, x, 64, z);
        if (remembered.size() > MAX_REMEMBERED) remembered.clear();
        remembered.put(key, value);
        return value;
    }

    /** How strongly mountain country holds at any column (not remembered); 0 with no world loaded. */
    static double strength(int x, int z) {
        RandomState rs = state;
        DensityFunction fn = function;
        return (rs == null || fn == null) ? 0.0 : rs.sampleBlockValueUncached(fn, x, 64, z);
    }

    /**
     * The land's own height under the summit of the peak of cell (ix, iz), in offset units. A peak
     * is built up from this one level, as a solid of its own, and is not laid over the land column
     * by column: laid over, every bump in the land shows through on its faces.
     */
    static double landAt(long ix, long iz, int x, int z) {
        long key = (ix << 32) ^ (iz & 0xFFFFFFFFL);
        Float known = rememberedLand.get(key);
        if (known != null) return known;
        RandomState rs = state;
        DensityFunction fn = landFunction;
        if (rs == null || fn == null) return 0.0;
        float value = rs.sampleBlockValueUncached(fn, x, 64, z);
        if (rememberedLand.size() > MAX_REMEMBERED) rememberedLand.clear();
        rememberedLand.put(key, value);
        return value;
    }

    /**
     * The land's target height at a column, in offset units (y = 128 + 128 * value): vanilla's own, less the cut the
     * mod makes along rivers in mountain country. (Away from rivers, where there is no cut, vanilla's own is read
     * alone: the land with its cut costs twice as much to work out, and this is asked for every column of a mountain.)
     */
    static double land(int x, int z) {
        RandomState rs = state;
        DensityFunction fn = landFunction, v = vanillaFunction;
        if (rs == null || fn == null) return 0.0;
        if (v != null && Math.abs(weirdness(x, z)) >= CUT_REACH) return rs.sampleBlockValueUncached(v, x, 64, z);
        return rs.sampleBlockValueUncached(fn, x, 64, z);
    }

    /** Vanilla's own target height at a column (out[0]) and the land's with the mod's river cut (out[1]), both in offset units. */
    static void lands(int x, int z, double[] out) {
        RandomState rs = state;
        DensityFunction v = vanillaFunction, cut = cutFunction;
        if (rs == null || v == null) { out[0] = out[1] = land(x, z); return; }
        out[0] = out[1] = rs.sampleBlockValueUncached(v, x, 64, z);
        if (cut != null && Math.abs(weirdness(x, z)) < CUT_REACH) out[1] += rs.sampleBlockValueUncached(cut, x, 64, z);
    }

    /** Vanilla's own target height at a column, before the mod's river cut, in offset units. */
    static double vanillaLand(int x, int z) {
        RandomState rs = state;
        DensityFunction v = vanillaFunction;
        return (rs == null || v == null) ? land(x, z) : rs.sampleBlockValueUncached(v, x, 64, z);
    }

    /** Vanilla's own temperature at a column (its climate map, before the mod touches what the biomes see); 0 with no world loaded. */
    static double temperature(int x, int z) {
        RandomState rs = state;
        DensityFunction fn = temperatureFunction;
        return (rs == null || fn == null) ? 0.0 : rs.sampleBlockValueUncached(fn, x, 64, z);
    }

    /** How strongly mountain country holds at a column, 0 to 1 (low erosion, inland: the data pack's "amp"); 0 with no world loaded. */
    static double mountainCountry(int x, int z) {
        RandomState rs = state;
        DensityFunction fn = countryFunction;
        return (rs == null || fn == null) ? 0.0 : rs.sampleBlockValueUncached(fn, x, 64, z);
    }

    /** True once a world's terrain sampler is ready. */
    static boolean ready() {
        return state != null && weirdnessFunction != null;
    }

    /** Vanilla's weirdness at a column (rivers run where it is near zero); 1 with no world loaded. */
    static float weirdness(int x, int z) {
        RandomState rs = state;
        DensityFunction fn = weirdnessFunction;
        return (rs == null || fn == null) ? 1.0f : rs.sampleBlockValueUncached(fn, x, 64, z);
    }

    /** The last column asked about by aboveLand, per thread. */
    private static final ThreadLocal<double[]> LAST_ABOVE = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, 0, 0});

    /**
     * How far the peaks stand above vanilla's own target height for the land at this column, in
     * blocks (0 where they do not): the same number the terrain adds to the land (peak_height).
     * The surface rules use it to tell a peak from vanilla land. Comparing a block with the
     * peaks' level alone is not enough: vanilla's 3-D noise often puts the real ground below the
     * level of a peak's buried skirt, and in 0.4.7's first test that painted 60% of the land
     * between peaks as peak rock. Sampled uncached (one lookup per column; the surface rules ask
     * about the same column several times in a row, so the last answer is kept per thread).
     */
    static double aboveLand(int x, int z) {
        double[] last = LAST_ABOVE.get();
        RandomState rs = state;
        double world = System.identityHashCode(rs);
        if (last[0] == x && last[1] == z && last[3] == world) return last[2];
        DensityFunction fn = aboveFunction;
        double v = (rs == null || fn == null) ? 0.0 : 128.0 * rs.sampleBlockValueUncached(fn, x, 64, z);
        last[0] = x; last[1] = z; last[2] = v; last[3] = world;
        return v;
    }
}
