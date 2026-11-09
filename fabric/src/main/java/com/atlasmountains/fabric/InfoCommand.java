package com.atlasmountains.fabric;

import com.atlasmountains.core.MountainSettings;
import com.atlasmountains.core.RegionCache;
import com.atlasmountains.core.diag.ContinentMap;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.commands.Commands;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * The /atlasmountains debug commands: info, df, scan, continents, fields and heightmap (see the
 * README for what each prints or writes).
 */
public final class InfoCommand {
    private InfoCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atlasmountains")
                .then(Commands.literal("info").executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    RegionCache cache = AtlasMountainsMod.cacheFor(src.getLevel());
                    MountainSettings s = cache.settings();
                    int rx = (int) Math.floor(p.x / s.regionSizeBlocks);
                    int rz = (int) Math.floor(p.z / s.regionSizeBlocks);
                    boolean cached = cache.isCached(rx, rz);
                    double cont = cache.continents().continentalness(p.x, p.z);
                    String line1 = String.format(Locale.UK, "region %d,%d  cached: %s  seed %d  continentalness %.2f (%s)  mask %.2f",
                            rx, rz, cached ? "yes" : "no", cache.seed(), cont,
                            cont < -0.455 ? "deep ocean" : cont < -0.19 ? "ocean" : cont < -0.11 ? "coast" : cont < 0.03 ? "near inland" : cont < 0.3 ? "mid inland" : "far inland",
                            cache.mask(p.x, p.z));
                    String line2;
                    if (cached) {
                        double h = cache.baseHeight(p.x, p.z);
                        double slope = cache.slopeDegrees(p.x, p.z);
                        line2 = String.format(Locale.UK, "terrain height %.1f (you are at y %.1f), slope %.0f deg, rock line %.0f, floor %.0f",
                                h, p.y, slope, s.rockLineHeight(), s.valleyFloorHeight);
                    } else {
                        line2 = "region not generated yet (" + cache.cachedRegionNames().size() + " regions cached)";
                    }
                    src.sendSuccess(() -> Component.literal(line1), false);
                    src.sendSuccess(() -> Component.literal(line2), false);
                    return 1;
                }))
                .then(Commands.literal("df")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
                    // Sample a registered density function at the player's column: y = -64, 64, 128, 200, 300 (2D functions give the same value at every y)
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    String idText = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                    var reg = src.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DENSITY_FUNCTION);
                    var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DENSITY_FUNCTION, net.minecraft.resources.Identifier.parse(idText));
                    var holder = reg.get(key);
                    if (holder.isEmpty()) { src.sendFailure(Component.literal("no density function " + idText)); return 0; }
                    RandomState rs = src.getLevel().getChunkSource().randomState();
                    StringBuilder sb = new StringBuilder(idText + " at " + (int) p.x + "," + (int) p.z + ":");
                    for (int y : new int[] {-64, 64, 128, 200, 300}) {
                        float v = rs.sampleBlockValueUncached(holder.get().value(), (int) Math.floor(p.x), y, (int) Math.floor(p.z));
                        sb.append(String.format(Locale.UK, "  y%d=%.3f", y, v));
                    }
                    final String msg = sb.toString();
                    src.sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                })))
                .then(Commands.literal("scan").executes(ctx -> {
                    // 8 km square around the player, 200 blocks per character: # mountains, + edge, . vanilla land, ~ sea
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    RegionCache cache = AtlasMountainsMod.cacheFor(src.getLevel());
                    int step = 200, half = 4000;
                    StringBuilder all = new StringBuilder("mountain mask scan, " + step + " blocks per character, north at top, you are at the centre (# mountains, + edge, . land, ~ sea)\n");
                    for (int z = -half; z <= half; z += step) {
                        StringBuilder row = new StringBuilder(String.format(Locale.UK, "z%+6d ", (int) p.z + z));
                        for (int x = -half; x <= half; x += step) {
                            double m = cache.mask((int) p.x + x, (int) p.z + z);
                            boolean sea = cache.mountainMask().continentalnessAt((int) p.x + x, (int) p.z + z) < -0.19;
                            row.append(m >= 0.5 ? '#' : m > 0 ? '+' : sea ? '~' : '.');
                        }
                        all.append(row).append('\n');
                    }
                    AtlasMountainsMod.LOGGER.info(all.toString());
                    src.sendSuccess(() -> Component.literal("mask scan written to the log (latest.log)"), false);
                    return 1;
                }))
                .then(Commands.literal("continents")
                        .then(Commands.argument("spanKm", IntegerArgumentType.integer(10, 400)).executes(ctx -> {
                    // Continents + mountain mask around the player, 1 pixel per (span / 1000) blocks,
                    // written to <world>/atlasmountains/continents_<x>_<z>_<span>km.png. Continents and mask only, no terrain, so it is quick.
                    CommandSourceStack src = ctx.getSource();
                    int spanKm = IntegerArgumentType.getInteger(ctx, "spanKm");
                    Vec3 p = src.getPosition();
                    RegionCache cache = AtlasMountainsMod.cacheFor(src.getLevel());
                    Path dir = src.getServer().getWorldPath(LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                    File out = dir.resolve(String.format(Locale.UK, "continents_%d_%d_%dkm.png", (int) p.x, (int) p.z, spanKm)).toFile();
                    try {
                        ContinentMap.render(cache.settings(), cache.continents(), cache.mountainMask(), cache.seed(),
                                spanKm * 1000, spanKm, (int) p.x, (int) p.z, out);
                    } catch (IOException e) {
                        src.sendFailure(Component.literal("could not write the map: " + e.getMessage()));
                        return 0;
                    }
                    src.sendSuccess(() -> Component.literal("continent map written to " + out.getPath()), false);
                    return 1;
                })))
                .then(Commands.literal("fields")
                        .then(Commands.argument("spanKm", IntegerArgumentType.integer(4, 200))
                        .then(Commands.argument("erosionBelow", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-1, 1))
                        .then(Commands.argument("ridgesAbove", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(-1, 1)).executes(ctx -> {
                    // The world's own biome noises around the player: continents as colour, and in brown the
                    // "biome-level mountain" footprint erosion < erosionBelow AND folded ridges > ridgesAbove
                    // (vanilla's peaks are roughly erosion < -0.375 and folded ridges > 0.05). Raw noises, no floor.
                    CommandSourceStack src = ctx.getSource();
                    int spanKm = IntegerArgumentType.getInteger(ctx, "spanKm");
                    double eBelow = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "erosionBelow");
                    double rAbove = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "ridgesAbove");
                    Vec3 p = src.getPosition();
                    ServerLevel world = src.getLevel();
                    RandomState cfg = world.getChunkSource().randomState();
                    var erosion = cfg.getOrCreateNoise(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.NOISE, AtlasMountainsMod.id("erosion")));
                    var ridge = cfg.getOrCreateNoise(net.minecraft.world.level.levelgen.Noises.RIDGE);
                    RegionCache cache = AtlasMountainsMod.cacheFor(world);
                    int span = spanKm * 1000, bpp = Math.max(1, span / 1000), n = span / bpp;
                    java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(n, n, java.awt.image.BufferedImage.TYPE_INT_RGB);
                    long landPx = 0, mtPx = 0;
                    for (int py = 0; py < n; py++) for (int px = 0; px < n; px++) {
                        double x = p.x - span / 2.0 + (px + 0.5) * bpp, z = p.z - span / 2.0 + (py + 0.5) * bpp;
                        double c = cache.continents().continentalness(x, z);
                        int rgb = ContinentMap.landColour(c);
                        if (c > -0.19) {
                            landPx++;
                            double e = erosion.get(x * 0.25, 0, z * 0.25);
                            double r = ridge.get(x * 0.25, 0, z * 0.25);
                            double folded = -3 * (Math.abs(Math.abs(r) - 2.0 / 3) - 1.0 / 3);
                            if (e < eBelow && folded > rAbove) {
                                mtPx++;
                                double m = Math.min(1, (eBelow - e) / 0.15) * Math.min(1, (folded - rAbove) / 0.15);
                                rgb = ContinentMap.blend(rgb, 0x5a3a1e, 0.4 + 0.6 * m);
                            }
                        }
                        img.setRGB(px, py, rgb);
                    }
                    Path dir = src.getServer().getWorldPath(LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                    File out = dir.resolve(String.format(Locale.UK, "fields_%d_%d_%dkm_e%.2f_r%.2f.png", (int) p.x, (int) p.z, spanKm, eBelow, rAbove)).toFile();
                    try { out.getParentFile().mkdirs(); javax.imageio.ImageIO.write(img, "png", out); }
                    catch (IOException ex) { src.sendFailure(Component.literal("could not write: " + ex.getMessage())); return 0; }
                    final String msg = String.format(Locale.UK, "fields map written to %s (footprint %.0f%% of land)", out.getPath(), 100.0 * mtPx / Math.max(1, landPx));
                    src.sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                })))))
                .then(Commands.literal("heightmap")
                        .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(256, 16384))
                        .then(Commands.argument("stepBlocks", IntegerArgumentType.integer(1, 64)).executes(ctx -> {
                    // Asks the chunk generator for the surface height over a square around the player and writes
                    // <world>/atlasmountains/heightmap_<x>_<z>_<size>.png (hillshade, blue where below sea level).
                    CommandSourceStack src = ctx.getSource();
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks");
                    int step = IntegerArgumentType.getInteger(ctx, "stepBlocks");
                    Vec3 p = src.getPosition();
                    ServerLevel world = src.getLevel();
                    ChunkGenerator gen = world.getChunkSource().getGenerator();
                    RandomState randomState = world.getChunkSource().randomState();
                    int seaLevel = gen.getSeaLevel();
                    int cx = (int) p.x, cz = (int) p.z;
                    src.sendSuccess(() -> Component.literal("sampling " + size + "x" + size + " blocks at " + step + " block steps..."), false);
                    Thread worker = new Thread(() -> {
                        try {
                            int n = size / step;
                            float[] h = new float[n * n];
                            for (int j = 0; j < n; j++)
                                for (int i = 0; i < n; i++)
                                    h[j * n + i] = gen.getBaseHeight(cx - size / 2 + i * step, cz - size / 2 + j * step, Heightmap.Types.WORLD_SURFACE_WG, world, randomState);
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
                                    if (hh < seaLevel) { r = 40; g = 80; b = 170; }
                                    else {
                                        double t = Math.max(0, Math.min(1, (hh - seaLevel) / 560.0));
                                        r = (int) (90 + 160 * t); g = (int) (140 + 110 * t); b = (int) (60 + 190 * t);
                                    }
                                    img.setRGB(i, j, ((int) (r * shade) << 16) | ((int) (g * shade) << 8) | (int) (b * shade));
                                }
                            }
                            java.nio.file.Path dir = src.getServer().getWorldPath(LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
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
