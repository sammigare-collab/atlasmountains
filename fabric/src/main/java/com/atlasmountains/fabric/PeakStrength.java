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
    private static final int MAX_REMEMBERED = 400_000;

    private static volatile RandomState state;
    private static volatile DensityFunction function;
    private static final ConcurrentHashMap<Long, Float> remembered = new ConcurrentHashMap<>();

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
        remembered.clear();
        if (server == null) return;
        var registries = server.registryAccess();
        var overworld = registries.lookupOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        var wanted = registries.lookupOrThrow(Registries.DENSITY_FUNCTION).get(KEY);
        if (overworld.isEmpty() || wanted.isEmpty() || !(overworld.get().value().generator() instanceof NoiseBasedChunkGenerator generator)) {
            AtlasMountainsMod.LOGGER.info("this world's overworld is not built from terrain noise; no peaks");
            return;
        }
        long seed = server.getWorldGenSettings().options().seed();
        state = RandomState.create(registries.lookupOrThrow(Registries.NOISE), seed, generator.generatorSettings().value());
        function = wanted.get().value();
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
}
