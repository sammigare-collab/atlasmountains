package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.List;

/**
 * The peaks: a field of separate pyramids, as a density function of (x, z).
 *
 * The value is the height the peaks reach at this column, in vanilla's "offset" units (ground at
 * about y = 128 + 128 * value), or a very low number where there is no peak. The data pack takes
 * whichever is higher, this or vanilla's land. Each peak is a solid of its own, built up from the
 * land's level under its summit; it is not added on top of the land column by column, which let
 * every bump in the land show through on its faces.
 *
 * Each peak is a true pyramid: 3 or 4 flat planes meeting at a point. The ground under a peak
 * is its summit height minus the largest of "how far down this plane has got here", so every
 * side is one big flat face, every edge between two faces is a straight line and the summit is
 * a point. The planes face unevenly spaced directions and each has its own steepness, so no two
 * pyramids are alike and none is regular.
 * <ul>
 *   <li>one peak site per square of {@code cell} blocks, jittered;</li>
 *   <li>{@code sides}: how many faces (a range); {@code slope}: how steep a face is, in blocks
 *       down per block out, give or take {@code slope_var}; {@code side_jitter}: how unevenly
 *       the faces are turned;</li>
 *   <li>an edge between two faces is always shallower than the faces, and between faces turned
 *       far apart it would run on for hundreds of blocks; {@code edge_min} stops an edge being
 *       shallower than that fraction of {@code slope};</li>
 *   <li>peaks differ in height, and each is scaled by how strongly mountain country holds at its
 *       own summit ({@link PeakStrength}), never column by column, so its angles never change;
 *       one shorter than {@code min_height} is left out;</li>
 *   <li>{@code warp} moves the ground a few blocks before all this, so edges are not ruler lines;</li>
 *   <li>the terrain is the highest of the peaks in reach, so neighbours meet in a notch.</li>
 * </ul>
 * How it got here, all from what Sammi saw in the game on 12 Nov 2026. The first shape was a cone
 * with ridges and scooped hollows, torn up by vanilla's 3-D noise. Then faces were made flat by
 * bending a cone's angle, which gave shallow creases ("very smooth"). Then ridges with V gullies
 * and ribs ("more star like than pyramidal", "way too many sides"). Then flat again but with the
 * ridge angles bent to make them uneven, which curved every face ("they all seem rounded", "I
 * want big flat side"). Planes are the answer to all four.
 */
public record HornsFunction(double cell, double jitter, List<Integer> sides, double sideJitter,
                            double slope, double slopeVar, double edgeMin,
                            double warp, double warpLength,
                            double height, double heightVar, double minHeight) implements ColumnFunction {

    /** What the function returns where no peak reaches: far below any land. */
    private static final double NO_PEAK = -8.0;

    public static final MapCodec<HornsFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("cell").forGetter(HornsFunction::cell),
            Codec.DOUBLE.fieldOf("jitter").forGetter(HornsFunction::jitter),
            Codec.INT.listOf().fieldOf("sides").forGetter(HornsFunction::sides),
            Codec.DOUBLE.fieldOf("side_jitter").forGetter(HornsFunction::sideJitter),
            Codec.DOUBLE.fieldOf("slope").forGetter(HornsFunction::slope),
            Codec.DOUBLE.fieldOf("slope_var").forGetter(HornsFunction::slopeVar),
            Codec.DOUBLE.fieldOf("edge_min").forGetter(HornsFunction::edgeMin),
            Codec.DOUBLE.fieldOf("warp").forGetter(HornsFunction::warp),
            Codec.DOUBLE.fieldOf("warp_length").forGetter(HornsFunction::warpLength),
            Codec.DOUBLE.fieldOf("height").forGetter(HornsFunction::height),
            Codec.DOUBLE.fieldOf("height_var").forGetter(HornsFunction::heightVar),
            Codec.DOUBLE.fieldOf("min_height").forGetter(HornsFunction::minHeight)
    ).apply(i, HornsFunction::new));

    // ---- hashing and noise ---------------------------------------------------------------

    /** A number in [0, 1) from two integers and a seed; the same on every thread and every run. */
    private static double hash(long ix, long iz, long seed) {
        long n = ix * 0x9E3779B97F4A7C15L + iz * 0xC2B2AE3D27D4EB4FL + seed * 0x165667B19E3779F9L;
        n ^= n >>> 29; n *= 0xBF58476D1CE4E5B9L;
        n ^= n >>> 32; n *= 0x94D049BB133111EBL;
        n ^= n >>> 29;
        return (n >>> 11) * 0x1.0p-53;
    }

    /** Smooth value noise in -1..1. */
    private static double valueNoise(double x, double z, long seed) {
        double fx0 = Math.floor(x), fz0 = Math.floor(z);
        long ix = (long) fx0, iz = (long) fz0;
        double fx = x - fx0, fz = z - fz0;
        double ux = fx * fx * (3 - 2 * fx), uz = fz * fz * (3 - 2 * fz);
        double a = hash(ix, iz, seed), b = hash(ix + 1, iz, seed), c = hash(ix, iz + 1, seed), d = hash(ix + 1, iz + 1, seed);
        return (a + (b - a) * ux + (c - a) * uz + (a - b - c + d) * ux * uz) * 2 - 1;
    }

    /** A few octaves of value noise, each half the size and half the strength of the last, in -1..1. */
    private static double fbm(double x, double z, long seed, int octaves) {
        double sum = 0, amp = 1, freq = 1, total = 0;
        for (int o = 0; o < octaves; o++) {
            sum += amp * valueNoise(x * freq, z * freq, seed + 17L * o);
            total += amp; amp *= 0.5; freq *= 2;
        }
        return sum / total;
    }

    // ---- the peaks ------------------------------------------------------------------------

    @Override
    public double at(int blockX, int blockZ) {
        long seed = AtlasMountainsMod.seed();
        double x = blockX, z = blockZ;
        double wx = x + warp * fbm(x / warpLength, z / warpLength, seed + 1, 3);
        double wz = z + warp * fbm(x / warpLength, z / warpLength, seed + 2, 3);
        long cx = (long) Math.floor(wx / cell), cz = (long) Math.floor(wz / cell);
        int sMin = sides.get(0), sMax = sides.get(1);
        // beyond this distance even the tallest peak, along its shallowest possible edge, has reached the ground
        double maxReach = height / (slope * (1 - slopeVar) * edgeMin);
        double best = NO_PEAK;
        for (int dj = -2; dj <= 2; dj++) {
            for (int di = -2; di <= 2; di++) {
                long ix = cx + di, iz = cz + dj;
                double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * jitter) * cell;
                double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * jitter) * cell;
                double dx = wx - sx, dz = wz - sz;
                double r = Math.hypot(dx, dz);
                if (r > maxReach) continue;
                // The peak's height is its own draw times how strongly mountain country holds at its
                // summit. The slopes are never scaled, so a weaker peak is lower and narrower with the
                // same angles; one that would come out shorter than min_height is left out altogether.
                double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
                h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                if (h < minHeight) continue;
                int k = (int) Math.floor(sMin + hash(ix, iz, seed + 6) * (sMax - sMin + 1));
                double turnAll = hash(ix, iz, seed + 7) * 2 * Math.PI, sector = 2 * Math.PI / k;
                // the planes: face f looks outward in direction a and drops s blocks for every block out
                // along a. Under any point, the plane that has dropped furthest is the surface.
                double fall = edgeMin * slope * r;                       // no edge shallower than this
                for (int f = 0; f < k; f++) {
                    double a = turnAll + (f + (hash(ix, iz, seed + 20 + f) - 0.5) * sideJitter) * sector;
                    double s = slope * (1 + slopeVar * (2 * hash(ix, iz, seed + 30 + f) - 1));
                    double down = (dx * Math.cos(a) + dz * Math.sin(a)) * s;
                    if (down > fall) fall = down;
                }
                double top = PeakStrength.landAt(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz)) + (h - fall) / 128.0;
                if (top > best) best = top;
            }
        }
        return best;
    }

    @Override
    public Interval range() { return Interval.of((float) NO_PEAK, (float) (4.0 + height / 128.0)); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
