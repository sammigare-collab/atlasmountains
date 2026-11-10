package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.PositionalRandomFactory;
import net.minecraft.world.level.levelgen.material.MaterialRuleContext;
import net.minecraft.world.level.levelgen.material.rule.MaterialRule;
import net.minecraft.world.level.levelgen.material.rule.RuleEvaluator;

import java.util.List;
import java.util.function.DoubleSupplier;

/**
 * Material rule "atlasmountains:strata": paints exposed rock as horizontal beds of several
 * stone types, the way sedimentary rock shows on a real mountain face.
 *
 * <h2>Why a custom rule</h2>
 * The vanilla toolbox has two ways of mixing rock blocks, and neither looks like rock:
 * <ul>
 *   <li>{@code noise_threshold} on the {@code minecraft:surface} noise picks a block per column
 *       from a noise with a wavelength of roughly 60 blocks. On a 150-block face that gives a few
 *       large flat-coloured blobs (the orange packed-mud patches of earlier builds).</li>
 *   <li>{@code minecraft:bandlands} makes exactly the layered look we want, but its palette is
 *       hard-coded to terracotta.</li>
 * </ul>
 * This rule is bandlands with our own palette, plus two touches that stop the beds looking
 * printed on: the beds bend up and down across the map, and their edges are speckled.
 *
 * <h2>How a block is chosen</h2>
 * <ol>
 *   <li><b>Band table.</b> On compile, a repeating column of {@code period} entries is filled
 *       with beds. Each bed takes a layer from {@code layers}, chosen at random by weight, and a
 *       thickness between {@code min_thickness} and that layer's {@code max_thickness}. The same
 *       layer never makes two beds in a row, so every boundary is visible. The random source comes
 *       from the world seed ({@link MaterialRuleContext#getOrCreateRandomFactory}), so the
 *       sequence is the same in every chunk of a world and different between worlds.</li>
 *   <li><b>Warp.</b> The height used to look up the table is shifted by
 *       {@code warp * noise(x, z)}, where the noise is {@code atlasmountains:strata_warp}, a slow
 *       2D noise defined in the datapack. Beds therefore climb and dip by up to {@code warp}
 *       blocks over a few hundred blocks, so on a long ridge they read as tilted, folded
 *       strata instead of contour lines.</li>
 *   <li><b>Speckle.</b> With probability {@code speckle}, a block reads its bed from up to two
 *       blocks above or below. Boundaries become ragged, and thin beds break into lenses, the way
 *       weathered rock looks. The draw is a positional random, so it is stable: regenerating a
 *       chunk gives identical blocks.</li>
 * </ol>
 *
 * <h2>Cost</h2>
 * The rule runs only where the material rules reach it: the top few blocks of steep mountain
 * faces. Each call is a table lookup, one cached 2D noise read per column, and one positional
 * random. The table is rebuilt per chunk, which costs about a hundred random draws.
 *
 * @param layers        the rock types and how often and how thick each appears
 * @param minThickness  the thinnest bed, in blocks
 * @param period        the height after which the band table repeats; long enough that the
 *                      repeat is never visible on one face
 * @param warp          the largest up or down shift of the beds, in blocks
 * @param speckle       the chance, 0 to 1, that a block takes a neighbouring bed's rock
 */
public record StrataRule(List<Layer> layers, int minThickness, int period, float warp, float speckle) implements MaterialRule {

    /**
     * One rock type in the strata.
     *
     * @param state         the block placed
     * @param weight        relative chance that a new bed is this layer (any positive number;
     *                      only the ratios between layers matter)
     * @param maxThickness  the thickest bed of this layer, in blocks; pale accent rocks such as
     *                      calcite are kept thin so they read as seams, not as walls
     */
    public record Layer(BlockState state, float weight, int maxThickness) {
        public static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockState.CODEC.fieldOf("state").forGetter(Layer::state),
                Codec.FLOAT.optionalFieldOf("weight", 1.0f).forGetter(Layer::weight),
                Codec.INT.optionalFieldOf("max_thickness", 4).forGetter(Layer::maxThickness)
        ).apply(i, Layer::new));
    }

    public static final MapCodec<StrataRule> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Layer.CODEC.listOf().fieldOf("layers").forGetter(StrataRule::layers),
            Codec.INT.optionalFieldOf("min_thickness", 1).forGetter(StrataRule::minThickness),
            Codec.INT.optionalFieldOf("period", 192).forGetter(StrataRule::period),
            Codec.FLOAT.optionalFieldOf("warp", 12.0f).forGetter(StrataRule::warp),
            Codec.FLOAT.optionalFieldOf("speckle", 0.15f).forGetter(StrataRule::speckle)
    ).apply(i, StrataRule::new));

    /** The 2D noise that bends the beds; defined in data/atlasmountains/worldgen/noise/strata_warp.json. */
    private static final ResourceKey<net.minecraft.world.level.levelgen.synth.NormalNoise> WARP_NOISE =
            ResourceKey.create(Registries.NOISE, AtlasMountainsMod.id("strata_warp"));

    @Override
    public MapCodec<? extends MaterialRule> codec() { return CODEC; }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        PositionalRandomFactory randoms = context.getOrCreateRandomFactory(AtlasMountainsMod.id("strata"));
        BlockState[] table = bandTable(randoms.fromHashOf("bands"));
        // 2D sampler: evaluated at the context's current column, cached by the context per column.
        DoubleSupplier warpNoise = context.getNoiseSampler(WARP_NOISE, false);
        int n = table.length;
        return (x, y, z) -> {
            int shifted = y + (int) Math.round(warp * warpNoise.getAsDouble());
            if (speckle > 0) {
                RandomSource r = randoms.at(x, y, z);
                if (r.nextFloat() < speckle) shifted += r.nextInt(5) - 2;   // -2..+2
            }
            return table[Math.floorMod(shifted, n)];
        };
    }

    /**
     * Fills one period of beds, bottom to top. Package-private so tests can check it. The
     * result depends only on the random source, so equal seeds give equal tables.
     */
    BlockState[] bandTable(RandomSource random) {
        BlockState[] table = new BlockState[Math.max(1, period)];
        double total = 0;
        for (Layer l : layers) total += Math.max(0, l.weight());
        int previous = -1;
        int y = 0;
        while (y < table.length) {
            // Weighted pick, re-drawn if it matches the bed below (two beds of the same rock
            // would merge into one, wasting a boundary). With a single layer there is no choice.
            int pick;
            int guard = 0;
            do {
                double roll = random.nextDouble() * total;
                pick = 0;
                while (pick < layers.size() - 1 && (roll -= Math.max(0, layers.get(pick).weight())) >= 0) pick++;
            } while (pick == previous && layers.size() > 1 && ++guard < 16);
            Layer layer = layers.get(pick);
            int hi = Math.max(minThickness, layer.maxThickness());
            int thickness = minThickness + random.nextInt(hi - minThickness + 1);
            for (int k = 0; k < thickness && y < table.length; k++) table[y++] = layer.state();
            previous = pick;
        }
        return table;
    }
}
