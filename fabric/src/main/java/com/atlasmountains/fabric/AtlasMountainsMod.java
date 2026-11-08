package com.atlasmountains.fabric;

import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionCache;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.minecraft.world.World;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.WorldSavePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public final class AtlasMountainsMod implements ModInitializer {

    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** The per-server terrain cache. Null until a server has started. */
    private static volatile RegionCache cache;

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    /** The active terrain cache, or an in-memory fallback if a density function is sampled before the server starts. */
    public static RegionCache cache() {
        RegionCache c = cache;
        if (c == null) {
            synchronized (AtlasMountainsMod.class) {
                if (cache == null) {
                    LOGGER.warn("terrain sampled before the server started; using seed 0 in memory");
                    cache = new RegionCache(new MountainSettings(), 0, null);
                }
                c = cache;
            }
        }
        return c;
    }

    @Override
    public void onInitialize() {
        Registry.register(Registries.DENSITY_FUNCTION_TYPE, id("terrain_height"), TerrainHeightFunction.CODEC_HOLDER.codec());
        Registry.register(Registries.DENSITY_FUNCTION_TYPE, id("mountain_mask"), MountainMaskFunction.CODEC_HOLDER.codec());
        Registry.register(Registries.MATERIAL_CONDITION, id("slope_above"), SlopeAboveCondition.CODEC_HOLDER.codec());
        Registry.register(Registries.MATERIAL_CONDITION, id("in_mountains"), InMountainsCondition.CODEC_HOLDER.codec());
        Registry.register(Registries.BIOME_SOURCE, id("mask"), AtlasBiomeSource.CODEC);

        // Once the overworld exists, let the mask read vanilla's continentalness so zones fade out at the coast.
        ServerWorldEvents.LOAD.register((server, world) -> {
            if (world.getRegistryKey() != World.OVERWORLD) return;
            DensityFunction continents = world.getChunkManager().getNoiseConfig().getNoiseRouter().continents();
            cache().mountainMask().continentalness = (x, z) ->
                    continents.sample(new DensityFunction.UnblendedNoisePos((int) Math.floor(x), 0, (int) Math.floor(z)));
            LOGGER.info("mountain mask now follows the overworld continentalness");
        });

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            long seed = server.getSaveProperties().getGeneratorOptions().getSeed();
            Path dir = server.getSavePath(WorldSavePath.ROOT).resolve(MOD_ID);
            synchronized (AtlasMountainsMod.class) {
                if (cache != null) cache.shutdown();
                cache = new RegionCache(new MountainSettings(), seed, dir);
                cache.log = msg -> LOGGER.info(msg);
            }
            LOGGER.info("terrain cache ready, seed {}, dir {}", seed, dir);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            synchronized (AtlasMountainsMod.class) {
                if (cache != null) cache.shutdown();
                cache = null;
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> InfoCommand.register(dispatcher));
    }
}
