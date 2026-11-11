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
                }))));
    }
}
