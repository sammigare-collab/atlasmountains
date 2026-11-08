package com.atlasmountains.fabric;

import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionCache;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Locale;

/** /atlasmountains info: region id, cached or not, heightmap value and slope at the player. */
public final class InfoCommand {
    private InfoCommand() {}

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("atlasmountains")
                .then(CommandManager.literal("info").executes(ctx -> {
                    ServerCommandSource src = ctx.getSource();
                    Vec3d p = src.getPosition();
                    RegionCache cache = AtlasMountainsMod.cache();
                    MountainSettings s = cache.settings();
                    int rx = (int) Math.floor(p.x / s.regionSizeBlocks);
                    int rz = (int) Math.floor(p.z / s.regionSizeBlocks);
                    boolean cached = cache.isCached(rx, rz);
                    String line1 = String.format(Locale.UK, "region %d,%d  cached: %s  seed %d", rx, rz, cached ? "yes" : "no", cache.seed());
                    String line2;
                    if (cached) {
                        double h = cache.baseHeight(p.x, p.z);
                        double slope = cache.slopeDegrees(p.x, p.z);
                        line2 = String.format(Locale.UK, "terrain height %.1f (you are at y %.1f), slope %.0f deg, rock line %.0f, floor %.0f",
                                h, p.y, slope, s.rockLineHeight(), s.valleyFloorHeight);
                    } else {
                        line2 = "region not generated yet (" + cache.cachedRegionNames().size() + " regions cached)";
                    }
                    src.sendFeedback(() -> Text.literal(line1), false);
                    src.sendFeedback(() -> Text.literal(line2), false);
                    return 1;
                }))
                .then(CommandManager.literal("scan").executes(ctx -> {
                    // 8 km square around the player, 200 blocks per character: # mountains, + edge, . vanilla
                    ServerCommandSource src = ctx.getSource();
                    Vec3d p = src.getPosition();
                    RegionCache cache = AtlasMountainsMod.cache();
                    int step = 200, half = 4000;
                    StringBuilder all = new StringBuilder("mountain mask scan, " + step + " blocks per character, north at top, you are at the centre\n");
                    for (int z = -half; z <= half; z += step) {
                        StringBuilder row = new StringBuilder(String.format(Locale.UK, "z%+6d ", (int) p.z + z));
                        for (int x = -half; x <= half; x += step) {
                            double m = cache.mask((int) p.x + x, (int) p.z + z);
                            row.append(m >= 0.5 ? '#' : m > 0 ? '+' : '.');
                        }
                        all.append(row).append('\n');
                    }
                    AtlasMountainsMod.LOGGER.info(all.toString());
                    src.sendFeedback(() -> Text.literal("mask scan written to the log (latest.log)"), false);
                    return 1;
                }))
                .then(CommandManager.literal("heightmap")
                        .then(CommandManager.argument("sizeBlocks", IntegerArgumentType.integer(256, 16384))
                        .then(CommandManager.argument("stepBlocks", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                    // Asks the chunk generator for the surface height over a square around the player and writes
                    // <world>/atlasmountains/heightmap_<x>_<z>_<size>.png (hillshade, blue where below sea level).
                    ServerCommandSource src = ctx.getSource();
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks");
                    int step = IntegerArgumentType.getInteger(ctx, "stepBlocks");
                    Vec3d p = src.getPosition();
                    ServerWorld world = src.getWorld();
                    ChunkGenerator gen = world.getChunkManager().getChunkGenerator();
                    NoiseConfig noiseConfig = world.getChunkManager().getNoiseConfig();
                    int seaLevel = gen.getSeaLevel();
                    int cx = (int) p.x, cz = (int) p.z;
                    src.sendFeedback(() -> Text.literal("sampling " + size + "x" + size + " blocks at " + step + " block steps..."), false);
                    Thread worker = new Thread(() -> {
                        try {
                            int n = size / step;
                            float[] h = new float[n * n];
                            for (int j = 0; j < n; j++)
                                for (int i = 0; i < n; i++)
                                    h[j * n + i] = gen.getHeight(cx - size / 2 + i * step, cz - size / 2 + j * step, Heightmap.Type.WORLD_SURFACE_WG, world, noiseConfig);
                            java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_RGB);
                            double el = Math.toRadians(45), lx = -Math.cos(el) / Math.sqrt(2), ly = Math.sin(el), lz = lx;
                            for (int j = 0; j < n; j++) {
                                for (int i = 0; i < n; i++) {
                                    double dx = (h[j * n + Math.min(n - 1, i + 1)] - h[j * n + Math.max(0, i - 1)]) / (2.0 * step);
                                    double dz = (h[Math.min(n - 1, j + 1) * n + i] - h[Math.max(0, j - 1) * n + i]) / (2.0 * step);
                                    double len = Math.sqrt(dx * dx + dz * dz + 1);
                                    double shade = 0.25 + 0.75 * Math.max(0, (-dx * lx + ly - dz * lz) / len);
                                    float hh = h[j * n + i];
                                    int r, g, b;
                                    if (hh <= seaLevel) { r = 40; g = 80; b = 170; }
                                    else {
                                        double t = Math.max(0, Math.min(1, (hh - seaLevel) / 560.0));
                                        r = (int) (90 + 160 * t); g = (int) (140 + 110 * t); b = (int) (60 + 190 * t);
                                    }
                                    img.setRGB(i, j, ((int) (r * shade) << 16) | ((int) (g * shade) << 8) | (int) (b * shade));
                                }
                            }
                            java.nio.file.Path dir = src.getServer().getSavePath(WorldSavePath.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                            java.nio.file.Files.createDirectories(dir);
                            java.io.File out = dir.resolve("heightmap_" + cx + "_" + cz + "_" + size + ".png").toFile();
                            javax.imageio.ImageIO.write(img, "png", out);
                            AtlasMountainsMod.LOGGER.info("heightmap written to {}", out);
                        } catch (Exception e) {
                            AtlasMountainsMod.LOGGER.error("heightmap failed", e);
                        }
                    }, "atlasmountains-heightmap");
                    worker.setDaemon(true);
                    worker.start();
                    return 1;
                })))));
    }
}
