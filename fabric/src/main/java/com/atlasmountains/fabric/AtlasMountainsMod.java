package com.atlasmountains.fabric;

import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AtlasMountainsMod implements ModInitializer {

    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** The per-server terrain caches: [0] parallel spines (the "everywhere" preset), [1] chains (the mixed preset). Null until a server has started. */
    private static volatile RegionCache[] caches;
    private static volatile long seed;
    private static volatile Path dir;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Whether the mixed "Atlas Mountains" preset lays its mountains as chains (true) or as vanilla's mountain footprint with parallel ranges (false). */
    public static final boolean MIXED_USES_CHAINS = true;

    /** The cache that generates the mixed "Atlas Mountains" preset. */
    public static RegionCache cache() { return cache(MIXED_USES_CHAINS); }

    /** The terrain cache for a layout, created lazily (in memory, seed 0, if sampled before a server has started). */
    public static RegionCache cache(boolean chains) {
        RegionCache[] cs = caches;
        if (cs == null) {
            synchronized (AtlasMountainsMod.class) {
                if (caches == null) {
                    LOGGER.warn("terrain sampled before the server started; using seed 0 in memory");
                    caches = new RegionCache[2];
                }
                cs = caches;
            }
        }
        int i = chains ? 1 : 0;
        RegionCache c = cs[i];
        if (c == null) {
            synchronized (AtlasMountainsMod.class) {
                if (cs[i] == null) {
                    MountainSettings s = new MountainSettings();
                    s.chainLayout = chains;
                    Path d = dir == null ? null : dir.resolve(chains ? "chains" : "parallel");
                    RegionCache n = new RegionCache(s, seed, d);
                    n.log = msg -> LOGGER.info(msg);
                    cs[i] = n;
                    LOGGER.info("terrain cache ready ({}), seed {}, dir {}", chains ? "chains" : "parallel", seed, d);
                }
                c = cs[i];
            }
        }
        return c;
    }

    /** The cache that generates a given world: chains for the mixed preset (mask biome source), parallel otherwise. */
    public static RegionCache cacheFor(net.minecraft.server.level.ServerLevel world) {
        return cache(world.getChunkSource().getGenerator().getBiomeSource() instanceof AtlasBiomeSource && MIXED_USES_CHAINS);
    }

    private static void shutdownCaches() {
        RegionCache[] cs = caches;
        if (cs != null) for (RegionCache c : cs) if (c != null) c.shutdown();
        caches = null;
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("terrain_height"), TerrainHeightFunction.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("mountain_mask"), MountainMaskFunction.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("on_our_slope"), OnOurSlopeCondition.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("continents"), ContinentsFunction.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("stretched_erosion"), StretchedErosionFunction.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("slope_above"), SlopeAboveCondition.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("in_mountains"), InMountainsCondition.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("mask"), AtlasBiomeSource.CODEC);

        // Once the overworld exists, give the chain layout the world's erosion noise so chains
        // are only placed in the biome layer's hilly provinces.
        ServerLevelEvents.LOAD.register((server, world) -> {
            if (world.dimension() != Level.OVERWORLD) return;
            var sampler = world.getChunkSource().randomState().getOrCreateNoise(
                    ResourceKey.create(Registries.NOISE, id("erosion")));
            MountainSettings s = new MountainSettings();
            StretchedErosionFunction.install(sampler, seed, s.erosionStretch);
            if (!(world.getChunkSource().getGenerator().getBiomeSource() instanceof AtlasBiomeSource)) return;
            RegionCache mixed = cache();
            mixed.layout().setErosion(StretchedErosionFunction::erosion);
            mixed.mountainMask().setErosion(StretchedErosionFunction::erosion);
            LOGGER.info("mountain zones are the overworld's mountain footprint (erosion below {}, stretched {}x), filled with {}",
                    s.erosionMountainBelow, s.erosionStretch, mixed.layout().getClass().getSimpleName());
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            synchronized (AtlasMountainsMod.class) {
                shutdownCaches();
                seed = server.getWorldGenSettings().options().seed();
                dir = server.getWorldPath(LevelResource.ROOT).resolve(MOD_ID);
                caches = new RegionCache[2];
            }
            LOGGER.info("world seed {}, terrain caches under {}", seed, dir);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (AtlasMountainsMod.class) {
                shutdownCaches();
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> InfoCommand.register(dispatcher));
    }
}
