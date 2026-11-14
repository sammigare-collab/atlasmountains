package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

/**
 * Density function "atlasmountains:river_wall": the river walls' settings, and the outermost
 * limit they set. Its value is how far above the land anything of a peak may stand at this column,
 * in vanilla's offset units (blocks / 128): 0 along a river channel, rising by {@code max_slope}
 * blocks for every block of distance from the nearest channel.
 * <p>
 * The walls themselves are applied piece by piece inside {@link HornsFunction}: each piece of a
 * peak has a flat wall of its own, at {@code slope} (the angle of the peaks' faces) unless its
 * summit stands so close to a river that it needs a steeper one, never steeper than
 * {@code max_slope}. See HornsFunction.wallSlope.
 * <p>
 * Up to 0.4.7 the limit was a multiple of vanilla's weirdness map itself. That map is a noise, so
 * the limit's steepness swung between about 22 and 71 degrees and its surface was lumpy; and it
 * decided the ground on most of the peak area (70% of a 384-block square on Sammi's seed, taking a
 * median 83 blocks off), which made the fins, towers and domes in his screenshots of 12 Nov 2026.
 * Real distance from the river gives flat walls.
 */
public record RiverWallFunction(double channel, double slope, double maxSlope, double ribRoom) implements ColumnFunction {
    public static final MapCodec<RiverWallFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("channel").forGetter(RiverWallFunction::channel),
            Codec.DOUBLE.fieldOf("slope").forGetter(RiverWallFunction::slope),
            Codec.DOUBLE.fieldOf("max_slope").forGetter(RiverWallFunction::maxSlope),
            Codec.DOUBLE.optionalFieldOf("rib_room", 0.0).forGetter(RiverWallFunction::ribRoom)
    ).apply(i, RiverWallFunction::new));

    /** The walls most recently built from the data pack; the peaks read their settings from it. */
    static volatile RiverWallFunction current;

    public RiverWallFunction {
        current = this;
    }

    /** Taller than any peak: added to the limit where a river is covered. */
    static final double COVERED_ROOM = 400;
    private static final ThreadLocal<double[]> OUT = ThreadLocal.withInitial(() -> new double[2]);

    @Override
    public double at(int blockX, int blockZ) {
        // room above the steepest wall for the ribs a wall carries, which fade out at the river
        double river = RiverDistance.at(blockX, blockZ, channel);
        double limit = river * maxSlope + ribRoom * Math.min(1, river / HornsFunction.WALL_RIB_FADE);
        // 0.5.6: no limit where the river runs between two peaks of a group and is covered (Massif.BURY_FROM)
        HornsFunction horns = HornsFunction.current;
        if (HornsFunction.ERODED && horns != null && limit < COVERED_ROOM) {
            double[] o = OUT.get();
            ErodedPeaks.sample(horns, blockX, blockZ, o);       // makes sure the region is there to ask
            if (o[0] > 0) limit += COVERED_ROOM * ErodedPeaks.buried(horns, blockX, blockZ);
        }
        return limit / 128.0;
    }

    @Override
    public Interval range() { return Interval.of(0.0f, (float) ((RiverDistance.REACH * maxSlope + ribRoom + COVERED_ROOM) / 128.0)); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
