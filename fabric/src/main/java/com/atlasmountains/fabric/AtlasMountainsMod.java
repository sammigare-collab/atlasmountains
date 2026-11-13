package com.atlasmountains.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlas Mountains, restarted from scratch on 11 Nov 2026.
 *
 * The mod reshapes vanilla's own mountains. Almost all of it is a data pack in the jar (under
 * resources/data, written by tools/build_overhaul.py) that changes the settings vanilla's
 * terrain reads, so the mountains stay in vanilla's places with vanilla's biomes, rivers and
 * caves. The one thing a data pack cannot express is the shape of the peaks themselves, so
 * this class registers one extra density function for the pack to use, atlasmountains:horns
 * (see HornsFunction), and from 0.4.7 the pieces of the peaks' own surface: the snow-cap line
 * (PeakCapFunction), "is this block on a peak" (OnPeakCondition) and the rock gradient
 * (PeakRockRule). It remembers the world seed and registers the debug commands.
 */
public final class AtlasMountainsMod implements ModInitializer {
    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** The running world's seed, set as the server starts; the peak layout is derived from it. */
    private static volatile long seed;

    public static long seed() { return seed; }

    /** An identifier in the mod's namespace. */
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("horns"), HornsFunction.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("peak_cap"), PeakCapFunction.CODEC);
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, id("river_wall"), RiverWallFunction.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("on_peak"), OnPeakCondition.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_CONDITION_TYPE, id("peak_snow"), PeakSnowCondition.CODEC);
        Registry.register(BuiltInRegistries.MATERIAL_RULE_TYPE, id("peak_rock"), PeakRockRule.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            seed = server.getWorldGenSettings().options().seed();
            PeakStrength.world(server);
            LOGGER.info("world seed {}", seed);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PeakStrength.world(null));
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FindCommand.register(dispatcher));
        LOGGER.info("Atlas Mountains loaded (vanilla mountain overhaul)");
    }
}
