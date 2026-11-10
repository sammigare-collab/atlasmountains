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

/**
 * The mod's entry point: registers our worldgen types, manages the terrain caches, and connects
 * them to the running server.
 *
 * <h2>What is registered</h2>
 * Density functions (terrain_height, mountain_mask, continents, stretched_erosion), material
 * conditions and rules (on_our_slope, slope_above, snow_cover, strata), the biome source (mask),
 * and the /atlasmountains command. The datapack in resources/data refers to these by id.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>SERVER_STARTING: remember the world seed and folder; drop any caches of a previous world
 *       (a single-player client can open several worlds in one session).</li>
 *   <li>First terrain request: the cache for that preset is created lazily, with the world's seed
 *       and its own folder under {@code <world>/atlasmountains/}.</li>
 *   <li>Overworld LOAD: the world's erosion noise exists now, so it is handed to the field
 *       layout, which from then on decides mountain cells from vanilla's footprint.</li>
 *   <li>SERVER_STOPPED: erosion threads are shut down.</li>
 * </ol>
 * Everything is static because density functions and material rules are created from JSON by
 * the game and have no other way to reach the mod.
 */
public final class AtlasMountainsMod implements ModInitializer {

    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * The per-server terrain caches: [0] the "everywhere" preset, [1] the mixed "Atlas Mountains"
     * preset. Only the mixed one is gated by vanilla's mountain footprint. Null until a server
     * has started.
     */
    private static volatile RegionCache[] caches;
    /** The running world's seed, set at SERVER_STARTING. */
    private static volatile long seed;
    /** The running world's cache folder, {@code <world>/atlasmountains}. */
    private static volatile Path dir;

    /** An identifier in the mod's namespace, e.g. id("strata") = atlasmountains:strata. */
    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** The cache that generates the mixed "Atlas Mountains" preset. */
    public static RegionCache cache() { return cache(true); }

    /**
     * The terrain cache for the mixed preset ({@code mixed}) or the "everywhere" preset, created
     * lazily (in memory, seed 0, if sampled before a server has started).
     */
    public static RegionCache cache(boolean mixed) {
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
        int i = mixed ? 1 : 0;
        RegionCache c = cs[i];
        if (c == null) {
            synchronized (AtlasMountainsMod.class) {
                if (cs[i] == null) {
                    MountainSettings s = new MountainSettings();
                    Path d = dir == null ? null : dir.resolve(mixed ? "mixed" : "everywhere");
                    RegionCache n = new RegionCache(s, seed, d, !mixed);
                    n.log = msg -> LOGGER.info(msg);
                    cs[i] = n;
                    LOGGER.info("terrain cache ready ({}), seed {}, dir {}", mixed ? "mixed" : "everywhere", seed, d);
                }
                c = cs[i];
            }
        }
        return c;
    }

    /** The cache that generates a given world: the mixed preset's if it uses our biome source, the "everywhere" preset's otherwise. */
    public static RegionCache cacheFor(net.minecraft.server.level.ServerLevel world) {
        return cache(world.getChunkSource().getGenerator().getBiomeSource() instanceof AtlasBiomeSource);
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
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("snow_cover"), SnowCoverCondition.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_RULE_TYPE, id("strata"), StrataRule.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("mask"), AtlasBiomeSource.CODEC);

        // Once the overworld exists, hand the world's erosion noise to the mixed preset's layout:
        // its mountain cells are the ones whose site lies in vanilla's mountain footprint.
        ServerLevelEvents.LOAD.register((server, world) -> {
            if (world.dimension() != Level.OVERWORLD) return;
            var sampler = world.getChunkSource().randomState().getOrCreateNoise(
                    ResourceKey.create(Registries.NOISE, id("erosion")));
            MountainSettings s = new MountainSettings();
            StretchedErosionFunction.install(sampler, seed, s.erosionStretch);
            if (!(world.getChunkSource().getGenerator().getBiomeSource() instanceof AtlasBiomeSource)) return;
            RegionCache mixed = cache();
            final double k = s.footprintErosionScale;
            mixed.layout().setErosion((x, z) -> StretchedErosionFunction.erosion(x * k, z * k));
            LOGGER.info("mountain provinces are the overworld's mountain footprint (erosion below {}, read at {}x the biome frequency)",
                    s.erosionMountainBelow, k);
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
