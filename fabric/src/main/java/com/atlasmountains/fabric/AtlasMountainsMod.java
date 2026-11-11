package com.atlasmountains.fabric;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Atlas Mountains, restarted from scratch on 11 Nov 2026.
 *
 * The mod no longer has a terrain generator of its own. It is a data pack in a jar: it changes
 * the settings vanilla's own mountain generation reads (under resources/data), so the mountains
 * are vanilla's, in vanilla's places, with vanilla's biomes, rivers and caves, only reshaped.
 * This class only registers one debug command, /atlasmountains mountains (see FindCommand).
 */
public final class AtlasMountainsMod implements ModInitializer {
    public static final String MOD_ID = "atlasmountains";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> FindCommand.register(dispatcher));
        LOGGER.info("Atlas Mountains loaded (vanilla mountain overhaul)");
    }
}
