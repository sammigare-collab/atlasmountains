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
 * (see HornsFunction), remembers the world seed for it, and registers the debug commands.
 */
public final class AtlasMountainsMod implements ModInitializer {
    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /** The running world's seed, set as the server starts; the peak layout is derived from it. */
    private static volatile long seed;

    public static long seed() { return seed; }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, Identifier.fromNamespaceAndPath(MOD_ID, "horns"), HornsFunction.CODEC);
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            seed = server.getWorldGenSettings().options().seed();
            LOGGER.info("world seed {}", seed);
        });
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FindCommand.register(dispatcher));
        LOGGER.info("Atlas Mountains loaded (vanilla mountain overhaul)");
    }
}
