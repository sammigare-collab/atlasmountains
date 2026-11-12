package com.atlasmountains.fabric;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;

import java.util.List;

/**
 * The horn peaks: a field of separate pyramid peaks, as a density function of (x, z).
 *
 * The value is the height of the peaks above the land they stand on, in vanilla's "offset"
 * units (blocks / 128), so the data pack can add it straight to vanilla's terrain offset. The
 * shape was first worked out in tools/prototype_horns.py against Sammi's WorldPainter references
 * (11 Nov 2026) and has moved on from it in step with what he saw in the game:
 * <ul>
 *   <li>one peak site per square of {@code cell} blocks, jittered;</li>
 *   <li>each peak is a pyramid of 3 or 4 sides ({@code aretes}); its edges are the ridges, and
 *       {@code facet} says how flat the faces between them are;</li>
 *   <li>it is a little steeper at the summit ({@code slope_top + slope_far}) than down its skirt
 *       ({@code slope_far}), and the edges fall more slowly than the faces;</li>
 *   <li>the edges are unevenly spaced round the summit and wander slightly ({@code warp}), so no
 *       two pyramids are alike;</li>
 *   <li>peaks differ in height, and each is scaled by how strongly mountain country holds at its
 *       own summit, never column by column; one shorter than {@code min_height} is left out;</li>
 *   <li>the terrain is the highest of the peaks in reach, so neighbours meet in a notch.</li>
 * </ul>
 * Where mountain country is, is decided in the data pack (tools/build_overhaul.py): it supplies
 * {@code atlasmountains:overworld/strength}, which each peak reads at its own summit (see
 * {@link PeakStrength}).
 */
public record HornsFunction(double cell, double jitter, double slopeTop, double slopeFar, double r0,
                            List<Integer> aretes, double areteDepth, double areteReach,
                            double warp, double warpLength, double gully,
                            double height, double heightVar, double minHeight, double facet, double fold) implements ColumnFunction {

    /** How many gullies the gully noise makes round a peak (about twice this). Fixed: a codec holds 16 settings at most. */
    private static final double GULLY_CYCLES = 3.0;

    public static final MapCodec<HornsFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("cell").forGetter(HornsFunction::cell),
            Codec.DOUBLE.fieldOf("jitter").forGetter(HornsFunction::jitter),
            Codec.DOUBLE.fieldOf("slope_top").forGetter(HornsFunction::slopeTop),
            Codec.DOUBLE.fieldOf("slope_far").forGetter(HornsFunction::slopeFar),
            Codec.DOUBLE.fieldOf("r0").forGetter(HornsFunction::r0),
            Codec.INT.listOf().fieldOf("aretes").forGetter(HornsFunction::aretes),
            Codec.DOUBLE.fieldOf("arete_depth").forGetter(HornsFunction::areteDepth),
            Codec.DOUBLE.fieldOf("arete_reach").forGetter(HornsFunction::areteReach),
            Codec.DOUBLE.fieldOf("warp").forGetter(HornsFunction::warp),
            Codec.DOUBLE.fieldOf("warp_length").forGetter(HornsFunction::warpLength),
            Codec.DOUBLE.fieldOf("gully").forGetter(HornsFunction::gully),
            Codec.DOUBLE.fieldOf("height").forGetter(HornsFunction::height),
            Codec.DOUBLE.fieldOf("height_var").forGetter(HornsFunction::heightVar),
            Codec.DOUBLE.optionalFieldOf("min_height", 0.0).forGetter(HornsFunction::minHeight),
            Codec.DOUBLE.optionalFieldOf("facet", 0.0).forGetter(HornsFunction::facet),
            Codec.DOUBLE.optionalFieldOf("fold", 0.0).forGetter(HornsFunction::fold)
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
        int aMin = aretes.get(0), aMax = aretes.get(1);
        // beyond this distance even the tallest peak, along a ridge, has reached the ground
        double maxReach = height / (slopeFar * (1 - areteDepth) * (1 - gully));
        double best = 0;
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
                double k = Math.floor(aMin + hash(ix, iz, seed + 6) * (aMax - aMin + 1));
                double phi = hash(ix, iz, seed + 7) * 2 * Math.PI;
                double th = Math.atan2(dz, dx);
                // ridges: the fall is slowest along k directions, with a sharp crease there; they
                // are strongest near the summit and fade down the skirt
                double q = r / areteReach;
                double ad = areteDepth * (0.35 + 0.65 * Math.exp(-q * q));
                // Going round the summit the ground falls slowest along the k ridges and fastest
                // midway between them. How it gets from one to the other decides the look:
                //   scooped: a sharp ridge with a rounded hollow between ridges (the first version);
                //   flat:    the faces are flat, as on a pyramid with k sides, so from above the peak
                //            is a triangle or a square and the ridges are its edges;
                //   folded:  a sharp V gully midway between ridges, like folded paper.
                // facet and fold are how much of flat and folded go in; the rest is scooped.
                // What Sammi made of them (12 Nov 2026): flat is the one he wants ("pyramidal"), with
                // few sides. Folded with 4 to 6 ridges and finer ribs on the faces was "more star like
                // than pyramidal", "way too many sides", and the ribs were taken out again. Flat peaks
                // did look smooth in 0.4.2, but that was peaks being squashed (see PeakStrength) and
                // five-sided ones, whose edges are too shallow to see.
                // The ridges are not spread evenly round the summit: the angle is stretched and
                // squeezed a little, differently for every peak, so no two pyramids are alike and
                // none is perfectly regular. 0.3 + 2 * 0.15 < 1 keeps the angle running one way.
                double bent = th + 0.3 * Math.sin(th + hash(ix, iz, seed + 9) * 2 * Math.PI)
                        + 0.15 * Math.sin(2 * th + hash(ix, iz, seed + 10) * 2 * Math.PI);
                double turn = k * (bent - phi) / (2 * Math.PI);
                double u = turn - Math.floor(turn);                         // 0 on a ridge, 0.5 midway between two
                double scooped = 1 - ad + 2 * ad * Math.pow(Math.abs(Math.sin(k * (bent - phi) / 2)), 0.8);
                double across = (u - 0.5) * 2 * Math.PI / k;                // angle from the middle of the face
                double flat = (1 - ad) * Math.cos(across) / Math.cos(Math.PI / k);
                double folded = 1 - ad * (4 * Math.abs(u - 0.5) - 1);
                double g = (1 - facet - fold) * scooped + facet * flat + fold * folded;
                double fall = (slopeTop * r0 * (1 - Math.exp(-r / r0)) + slopeFar * r) * g;
                // gullies straight down the faces: noise that varies round the peak, hardly along the radius
                double gn = 1 - 2 * Math.abs(fbm(GULLY_CYCLES * Math.cos(th) + ix * 7.1, GULLY_CYCLES * Math.sin(th) + iz * 3.7 + r / 260.0, seed + 8, 2));
                fall *= 1 + gully * (gn - 0.6) * Math.min(1, r / 40.0);
                if (h - fall > best) best = h - fall;
            }
        }
        return best / 128.0;
    }

    @Override
    public Interval range() { return Interval.of(0, (float) (height / 128.0 * 1.01)); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
