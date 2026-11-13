package com.atlasmountains.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The debug commands.
 * <p>
 * /atlasmountains heightmap &lt;size&gt; &lt;step&gt;: the real surface heights around the player, as a
 * file, without generating chunks.
 * <p>
 * /atlasmountains snapshot &lt;size&gt;: generates a square round the player and writes each
 * column's top block and height to a file (tools/render_snapshot.py draws it).
 * <p>
 * /atlasmountains mountains &lt;radiusBlocks&gt;: where vanilla's tall mountains are.
 *
 * Vanilla only builds tall mountains where three of its climate maps line up: erosion very low
 * (below about -0.78 for the tallest tier), far enough inland (continentalness above about 0.3)
 * and on a peak line (weirdness near +/- 2/3). This reads those maps straight from the world's
 * own climate sampler, without generating any chunks, and lists the strongest spots around the
 * player, so a test or a screenshot can be aimed at real mountains instead of at a peaks biome
 * that happens to sit on a low hill.
 */
public final class FindCommand {
    private FindCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("atlasmountains")
                .then(Commands.literal("mountains")
                        .then(Commands.argument("radiusBlocks", IntegerArgumentType.integer(512, 30000)).executes(ctx -> {
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel world = src.getLevel();
                    Vec3 p = src.getPosition();
                    int radius = IntegerArgumentType.getInteger(ctx, "radiusBlocks");
                    RandomState rstate = world.getChunkSource().randomState();
                    var climate = rstate.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.EMPTY_UNCACHED);
                    int cx = (int) Math.floor(p.x), cz = (int) Math.floor(p.z), step = 64;
                    // one best spot per 1024-block square, so the list is spread over separate ranges
                    java.util.Map<Long, double[]> best = new java.util.HashMap<>();
                    int tall = 0, total = 0;
                    for (int z = cz - radius; z <= cz + radius; z += step) {
                        for (int x = cx - radius; x <= cx + radius; x += step) {
                            double e = climate.erosion().sampleValue(x, 64, z);
                            double c = climate.continentalness().sampleValue(x, 64, z);
                            double w = climate.weirdness().sampleValue(x, 64, z);
                            double pv = 1 - Math.abs(3 * Math.abs(w) - 2);
                            total++;
                            if (e < -0.78 && c > 0.3) tall++;
                            if (e > -0.6 || c < 0.15) continue;
                            // lower erosion, further inland and nearer a peak line all score higher
                            double score = (-e) + 0.5 * c + 0.3 * pv;
                            long key = (((long) Math.floorDiv(x, 1024)) << 32) ^ (Math.floorDiv(z, 1024) & 0xffffffffL);
                            double[] cur = best.get(key);
                            if (cur == null || score > cur[0]) best.put(key, new double[] { score, x, z, e, c, w });
                        }
                    }
                    List<double[]> list = new ArrayList<>(best.values());
                    list.sort((a, b) -> Double.compare(b[0], a[0]));
                    StringBuilder sb = new StringBuilder(String.format(Locale.UK,
                            "tall-mountain climate (E < -0.78, C > 0.3) on %.1f%% of the %d-block square. Best spots:", 100.0 * tall / total, 2 * radius));
                    for (int i = 0; i < Math.min(8, list.size()); i++) {
                        double[] s = list.get(i);
                        sb.append(String.format(Locale.UK, "\n  x %d z %d  E %.2f  C %.2f  W %.2f", (int) s[1], (int) s[2], s[3], s[4], s[5]));
                    }
                    if (list.isEmpty()) sb.append(" none (no erosion below -0.6 inland)");
                    String msg = sb.toString();
                    AtlasMountainsMod.LOGGER.info(msg);
                    src.sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                })))
                .then(Commands.literal("df")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string()).executes(ctx -> {
                    // A registered density function sampled at the player's column (at y 64 and y 200;
                    // the 2-D ones give the same value at both). For checking the data pack by number.
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    String idText = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                    var reg = src.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DENSITY_FUNCTION);
                    var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DENSITY_FUNCTION, net.minecraft.resources.Identifier.parse(idText));
                    var holder = reg.get(key);
                    if (holder.isEmpty()) { src.sendFailure(Component.literal("no density function " + idText)); return 0; }
                    RandomState rs = src.getLevel().getChunkSource().randomState();
                    int x = (int) Math.floor(p.x), z = (int) Math.floor(p.z);
                    String msg = String.format(Locale.UK, "%s at %d,%d: y64=%.3f y200=%.3f", idText, x, z,
                            rs.sampleBlockValueUncached(holder.get().value(), x, 64, z), rs.sampleBlockValueUncached(holder.get().value(), x, 200, z));
                    src.sendSuccess(() -> Component.literal(msg), false);
                    return 1;
                })))
                .then(Commands.literal("dfmap")
                        .then(Commands.argument("id", com.mojang.brigadier.arguments.StringArgumentType.string())
                        .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(256, 400000))
                        .then(Commands.argument("stepBlocks", IntegerArgumentType.integer(1, 1024)).executes(ctx -> {
                    // A registered density function over a square around the player, at y 64, written
                    // as raw little-endian floats (n x n, row by row) to <world>/atlasmountains/.
                    // For maps of the climate fields (land and ocean, temperature) over huge areas.
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    String idText = com.mojang.brigadier.arguments.StringArgumentType.getString(ctx, "id");
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks"), step = IntegerArgumentType.getInteger(ctx, "stepBlocks");
                    var reg = src.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.DENSITY_FUNCTION);
                    var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DENSITY_FUNCTION, net.minecraft.resources.Identifier.parse(idText));
                    var holder = reg.get(key);
                    if (holder.isEmpty()) { src.sendFailure(Component.literal("no density function " + idText)); return 0; }
                    var fn = holder.get().value();
                    RandomState rs = src.getLevel().getChunkSource().randomState();
                    int cx = (int) Math.floor(p.x), cz = (int) Math.floor(p.z), n = size / step;
                    src.sendSuccess(() -> Component.literal("sampling " + n + " x " + n + " points..."), false);
                    Thread worker = new Thread(() -> {
                        try {
                            java.nio.ByteBuffer raw = java.nio.ByteBuffer.allocate(n * n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            for (int j = 0; j < n; j++)
                                for (int i = 0; i < n; i++)
                                    raw.putFloat((float) rs.sampleBlockValueUncached(fn, cx - size / 2 + i * step, 64, cz - size / 2 + j * step));
                            java.nio.file.Path dir = src.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                            java.nio.file.Files.createDirectories(dir);
                            java.nio.file.Path out = dir.resolve("dfmap_" + idText.replaceAll("[^a-z0-9_]", "_") + "_" + cx + "_" + cz + "_" + size + "_" + step + ".f32");
                            java.nio.file.Files.write(out, raw.array());
                            AtlasMountainsMod.LOGGER.info("dfmap written to {}", out);
                        } catch (Exception e) {
                            AtlasMountainsMod.LOGGER.error("dfmap failed", e);
                        }
                    }, "atlasmountains-dfmap");
                    worker.setDaemon(true);
                    worker.start();
                    return 1;
                })))))
                .then(Commands.literal("biomemap")
                        .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(256, 400000))
                        .then(Commands.argument("stepBlocks", IntegerArgumentType.integer(4, 1024)).executes(ctx -> {
                    // The biome the generator would pick, over a square around the player, sampled high
                    // above the ground so cave biomes stay out of it. Written as n x n little-endian
                    // ints to <world>/atlasmountains/, with the biome names in a .txt beside it.
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel world = src.getLevel();
                    Vec3 p = src.getPosition();
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks"), step = IntegerArgumentType.getInteger(ctx, "stepBlocks");
                    int cx = (int) Math.floor(p.x), cz = (int) Math.floor(p.z), n = size / step;
                    RandomState rstate = world.getChunkSource().randomState();
                    var biomes = world.getChunkSource().getGenerator().getBiomeSource().createUncachedResolver(rstate);
                    src.sendSuccess(() -> Component.literal("sampling " + n + " x " + n + " biomes..."), false);
                    Thread worker = new Thread(() -> {
                        try {
                            java.util.Map<String, Integer> ids = new java.util.LinkedHashMap<>();
                            java.nio.ByteBuffer raw = java.nio.ByteBuffer.allocate(n * n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            for (int j = 0; j < n; j++)
                                for (int i = 0; i < n; i++) {
                                    String name = biomes.getNoiseBiome((cx - size / 2 + i * step) >> 2, 300 >> 2, (cz - size / 2 + j * step) >> 2).getRegisteredName();
                                    Integer id = ids.get(name);
                                    if (id == null) { id = ids.size(); ids.put(name, id); }
                                    raw.putInt(id);
                                }
                            java.nio.file.Path dir = src.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                            java.nio.file.Files.createDirectories(dir);
                            String base = "biomemap_" + cx + "_" + cz + "_" + size + "_" + step;
                            java.nio.file.Files.write(dir.resolve(base + ".i32"), raw.array());
                            java.nio.file.Files.write(dir.resolve(base + ".txt"), String.join("\n", ids.keySet()).getBytes());
                            AtlasMountainsMod.LOGGER.info("biomemap written to {}", dir.resolve(base + ".i32"));
                        } catch (Exception e) {
                            AtlasMountainsMod.LOGGER.error("biomemap failed", e);
                        }
                    }, "atlasmountains-biomemap");
                    worker.setDaemon(true);
                    worker.start();
                    return 1;
                }))))
                .then(Commands.literal("peaks").executes(ctx -> {
                    // The peaks nearest the player, each with a teleport to just above its summit.
                    CommandSourceStack src = ctx.getSource();
                    Vec3 p = src.getPosition();
                    HornsFunction horns = HornsFunction.current;
                    if (horns == null) { src.sendFailure(Component.literal("no peak field loaded")); return 0; }
                    java.util.List<double[]> all = horns.peaksNear(p.x, p.z, 6000);
                    all.sort(java.util.Comparator.comparingDouble(a -> Math.hypot(a[1] - p.x, a[2] - p.z)));
                    if (all.isEmpty()) { src.sendSuccess(() -> Component.literal("No peaks within 6,000 blocks."), false); return 1; }
                    if (HornsFunction.FOOT_SAMPLER) {
                        // Sampler build: the three nearest good-sized peaks of each look.
                        src.sendSuccess(() -> Component.literal("Sampler build. Nearest peaks of each look (click or type the /tp):"), false);
                        if (ErodedPeaks.eroded > 0) {
                            String timing = String.format("Erosion so far: %d regions with mountains, %d ms each on average, slowest %d ms.", ErodedPeaks.eroded, ErodedPeaks.erodedMillis / ErodedPeaks.eroded, ErodedPeaks.slowestBuildMillis);
                            src.sendSuccess(() -> Component.literal(timing), false);
                        }
                        for (int foot = 0; foot < HornsFunction.FOOT_NAMES.length; foot++) {
                            int shown = 0;
                            for (double[] hit : all) {
                                if ((int) hit[0] != foot || hit[3] < 110) continue;
                                String line = String.format("  %s:  /tp @s %d %d %d  (%d blocks away, %d tall)", HornsFunction.FOOT_NAMES[foot], (int) hit[1], (int) hit[4] + 20, (int) hit[2], (int) Math.hypot(hit[1] - p.x, hit[2] - p.z), (int) hit[3]);
                                src.sendSuccess(() -> Component.literal(line), false);
                                if (++shown == 3) break;
                            }
                        }
                        return 1;
                    }
                    src.sendSuccess(() -> Component.literal("Nearest peaks:"), false);
                    for (double[] hit : all.subList(0, Math.min(6, all.size()))) {
                        String line = String.format("  /tp @s %d %d %d  (%d blocks away, %d tall)", (int) hit[1], (int) hit[4] + 20, (int) hit[2], (int) Math.hypot(hit[1] - p.x, hit[2] - p.z), (int) hit[3]);
                        src.sendSuccess(() -> Component.literal(line), false);
                    }
                    return 1;
                }))
                .then(Commands.literal("snapshot")
                        .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(16, 2048)).executes(ctx -> {
                    // Generates every chunk of a square round the player (fully, features and all)
                    // and writes the top block of each column and its height to a file, so the
                    // surface can be checked and drawn from real generated chunks. Runs on the
                    // server thread and can take minutes for a large square.
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel world = src.getLevel();
                    Vec3 p = src.getPosition();
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks");
                    int x0 = (int) Math.floor(p.x) - size / 2, z0 = (int) Math.floor(p.z) - size / 2;
                    java.util.Map<String, Integer> ids = new java.util.LinkedHashMap<>();
                    java.nio.ByteBuffer raw = java.nio.ByteBuffer.allocate(size * size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                    net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
                    for (int j = 0; j < size; j++)
                        for (int i = 0; i < size; i++) {
                            int x = x0 + i, z = z0 + j;
                            world.getChunk(x >> 4, z >> 4);
                            int y = world.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                            String name = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos.set(x, y, z)).getBlock()).toString();
                            Integer id = ids.get(name);
                            if (id == null) { id = ids.size(); ids.put(name, id); }
                            raw.putShort((short) y);
                            raw.putShort((short) (int) id);
                        }
                    try {
                        java.nio.file.Path dir = src.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                        java.nio.file.Files.createDirectories(dir);
                        String base = "snapshot_" + x0 + "_" + z0 + "_" + size;
                        java.nio.file.Files.write(dir.resolve(base + ".bin"), raw.array());
                        java.nio.file.Files.write(dir.resolve(base + ".txt"), String.join("\n", ids.keySet()).getBytes());
                        src.sendSuccess(() -> Component.literal("snapshot written: " + base), false);
                    } catch (java.io.IOException e) {
                        src.sendFailure(Component.literal("snapshot failed: " + e));
                    }
                    return 1;
                })))
                .then(Commands.literal("heightmap")
                        .then(Commands.argument("sizeBlocks", IntegerArgumentType.integer(256, 8192))
                        .then(Commands.argument("stepBlocks", IntegerArgumentType.integer(1, 32)).executes(ctx -> {
                    // The real surface height over a square around the player, asked of the chunk
                    // generator column by column without generating any chunks, written as raw
                    // little-endian floats (n x n, row by row) to <world>/atlasmountains/. A look at
                    // the terrain's shape in a couple of minutes instead of generating the area.
                    CommandSourceStack src = ctx.getSource();
                    ServerLevel world = src.getLevel();
                    Vec3 p = src.getPosition();
                    int size = IntegerArgumentType.getInteger(ctx, "sizeBlocks"), step = IntegerArgumentType.getInteger(ctx, "stepBlocks");
                    int cx = (int) Math.floor(p.x), cz = (int) Math.floor(p.z), n = size / step;
                    var gen = world.getChunkSource().getGenerator();
                    RandomState rstate = world.getChunkSource().randomState();
                    src.sendSuccess(() -> Component.literal("sampling " + n + " x " + n + " columns..."), false);
                    Thread worker = new Thread(() -> {
                        try {
                            java.nio.ByteBuffer raw = java.nio.ByteBuffer.allocate(n * n * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN);
                            for (int j = 0; j < n; j++)
                                for (int i = 0; i < n; i++)
                                    raw.putFloat(gen.getBaseHeight(cx - size / 2 + i * step, cz - size / 2 + j * step,
                                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, world, rstate));
                            java.nio.file.Path dir = src.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT).resolve(AtlasMountainsMod.MOD_ID);
                            java.nio.file.Files.createDirectories(dir);
                            java.nio.file.Path out = dir.resolve("heightmap_" + cx + "_" + cz + "_" + size + "_" + step + ".f32");
                            java.nio.file.Files.write(out, raw.array());
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
