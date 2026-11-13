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
public record HornsFunction(double cell, List<Integer> sides, double sideJitter,
                            double slope, double slopeVar, double edgeMin, double warp,
                            double height, double heightVar, double minHeight,
                            List<Integer> shoulders, double shoulderSize, double ribDepth, double ribSpacing,
                            double skirtFrom, double skirtSlope) implements ColumnFunction {

    // A codec holds 16 settings at most, so two that have never been changed are fixed here.
    /** How far a summit may sit from the middle of its cell, as a fraction of the cell. */
    private static final double JITTER = 0.7;
    /** The size of the wander that warp adds, in blocks. */
    private static final double WARP_LENGTH = 120.0;

    // ---- the faces: buttresses (look C+ of the 0.4.10 sampler, chosen by Sammi on 13 Nov 2026) ----
    // Each face is made of flat panels rib_spacing wide, each set up to PANEL_STEP blocks in or
    // out, with a sharp V gully up to rib_depth deep between each pair, so a face is still made of
    // big flat sides. The other looks he compared in the game and passed over: A big spurs (the
    // ground zigzagging between random levels 22 deep, 44 apart), A+ the same with the steeper
    // top, C buttresses with the old shallower top, and (0.4.9) B very big spurs.
    /** How far a buttress panel is set in or out, in blocks. */
    private static final double PANEL_STEP = 8;
    /** The half-width of the gully between two buttresses, as a fraction of the spacing. */
    private static final double GULLY_HALF = 0.24;

    /** The peak field most recently built from the data pack; the debug command asks it about peaks. */
    static volatile HornsFunction current;

    public HornsFunction {
        current = this;
    }

    /** One line per peak whose summit is within radius blocks of (x, z): style, summit x, summit z, height. */
    java.util.List<double[]> peaksNear(double x, double z, double radius) {
        long seed = AtlasMountainsMod.seed();
        java.util.List<double[]> found = new java.util.ArrayList<>();
        long c0x = (long) Math.floor((x - radius) / cell), c1x = (long) Math.floor((x + radius) / cell);
        long c0z = (long) Math.floor((z - radius) / cell), c1z = (long) Math.floor((z + radius) / cell);
        for (long ix = c0x; ix <= c1x; ix++) {
            for (long iz = c0z; iz <= c1z; iz++) {
                double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * JITTER) * cell;
                double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * JITTER) * cell;
                if (Math.hypot(sx - x, sz - z) > radius) continue;
                double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
                h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                if (h < minHeight) continue;
                found.add(new double[]{-1, sx, sz, h,
                        128 + 128 * PeakStrength.landAt(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz)) + h});
            }
        }
        return found;
    }

    /** What the function returns where no peak reaches: far below any land. */
    static final double NO_PEAK = -8.0;

    public static final MapCodec<HornsFunction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("cell").forGetter(HornsFunction::cell),
            Codec.INT.listOf().fieldOf("sides").forGetter(HornsFunction::sides),
            Codec.DOUBLE.fieldOf("side_jitter").forGetter(HornsFunction::sideJitter),
            Codec.DOUBLE.fieldOf("slope").forGetter(HornsFunction::slope),
            Codec.DOUBLE.fieldOf("slope_var").forGetter(HornsFunction::slopeVar),
            Codec.DOUBLE.fieldOf("edge_min").forGetter(HornsFunction::edgeMin),
            Codec.DOUBLE.fieldOf("warp").forGetter(HornsFunction::warp),
            Codec.DOUBLE.fieldOf("height").forGetter(HornsFunction::height),
            Codec.DOUBLE.fieldOf("height_var").forGetter(HornsFunction::heightVar),
            Codec.DOUBLE.fieldOf("min_height").forGetter(HornsFunction::minHeight),
            Codec.INT.listOf().optionalFieldOf("shoulders", List.of(0, 0)).forGetter(HornsFunction::shoulders),
            Codec.DOUBLE.optionalFieldOf("shoulder_size", 0.7).forGetter(HornsFunction::shoulderSize),
            Codec.DOUBLE.optionalFieldOf("rib_depth", 0.0).forGetter(HornsFunction::ribDepth),
            Codec.DOUBLE.optionalFieldOf("rib_spacing", 30.0).forGetter(HornsFunction::ribSpacing),
            Codec.DOUBLE.optionalFieldOf("skirt_from", 0.0).forGetter(HornsFunction::skirtFrom),
            Codec.DOUBLE.optionalFieldOf("skirt_slope", 1.0).forGetter(HornsFunction::skirtSlope)
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

    // ---- the pointed top and the distinct shoulder peaks (0.4.7) ---------------------------
    // These are constants because the codec is full (16 settings).
    /** The top TOP_PART of a peak's height is at TOP_STEEP times the faces' steepness (58 degrees) ... */
    static final double TOP_PART = 0.30, TOP_STEEP = 1.7;
    /** ... and everything from there down to the skirt at MID_STEEP times (49 degrees). */
    static final double MID_STEEP = 1.2;

    /**
     * How high above its base level a pyramid h blocks tall stands where its faces have dropped by
     * "fall" (fall is the drop of the steepest plane here, so the shape stays a set of flat planes).
     * Three bands, top down, each a straight-sided step of the same planes:
     * <ol>
     *   <li>the horn top: the top TOP_PART of the height, TOP_STEEP times as steep as the planes;</li>
     *   <li>the face, MID_STEEP times as steep, down to skirt_from of the height;</li>
     *   <li>the skirt, at skirt_slope times the planes' steepness, until it meets the ground. Without
     *       it a peak stopped at a line part-way down and sat on a collar of vanilla hills ("not
     *       connected to the rest of the terrain", Sammi, 12 Nov 2026).</li>
     * </ol>
     * This is the "steeper horn top" of the 0.4.10 sampler, which Sammi chose (look C+): slender
     * tops, steep at the summit and flaring out to the base, as in his reference pictures. 0.4.7 to
     * 0.4.9 had the top 18% at 1.5 times and the face at 1.
     */
    private double standing(double h, double fall) {
        double topFall = TOP_PART * h / TOP_STEEP;                 // fall at which the horn top ends
        if (fall <= topFall) return h - fall * TOP_STEEP;
        double faceDrop = (1 - skirtFrom - TOP_PART) * h;          // height the face band covers
        double faceEnd = topFall + faceDrop / MID_STEEP;
        if (fall <= faceEnd) return (1 - TOP_PART) * h - (fall - topFall) * MID_STEEP;
        return skirtFrom * h - (fall - faceEnd) * skirtSlope;
    }

    /**
     * How far from its summit a pyramid h blocks tall is looked for. Beyond this it is left out, so
     * this is also where a skirt running down over lower land ends. The numbers are those of the
     * shallower profile of 0.4.7 to 0.4.9 (top 18% at 1.5 times, face at 1, ribs up to 30 deep),
     * kept so that skirts end exactly where they did in the sampler Sammi chose from; it is a
     * little further out than the horn profile needs.
     */
    private double reach(double h) {
        double fallAtFoot = 0.18 * h / 1.5 + (1 - skirtFrom - 0.18) * h + (skirtFrom > 0 ? skirtFrom * h / skirtSlope : 0);
        return fallAtFoot / (slope * (1 - slopeVar) * edgeMin) + 30.0;
    }

    /** How many faces the pyramid drawn with this seed has. */
    private int faceCount(long ix, long iz, long seed) {
        int sMin = sides.get(0), sMax = sides.get(1);
        return (int) Math.floor(sMin + hash(ix, iz, seed + 6) * (sMax - sMin + 1));
    }

    /** The direction face f of the pyramid drawn with this seed looks out to. */
    private double faceAngle(long ix, long iz, long seed, int f, int k) {
        double turnAll = hash(ix, iz, seed + 7) * 2 * Math.PI, sector = 2 * Math.PI / k;
        return turnAll + (f + (hash(ix, iz, seed + 20 + f) - 0.5) * sideJitter) * sector;
    }

    /** Ribs and gullies come in to full depth over this many blocks out from the summit, so they start right under the point (50 up to 0.4.9, which left the top third of a small peak a bare pyramid). */
    private static final double RIB_FADE = 15.0;
    /** A rib or gully is never more than this share of the drop of the face it is on. */
    private static final double RIB_SHARE = 0.5;

    /**
     * How far one pyramid has dropped at (dx, dz) from its own summit; ribOut[0] is set to the
     * part of that which is the ribs and gullies (blocks; above zero in a gully, below on a rib).
     * <p>
     * Ribs: straight ridges and gullies running down each face, side by side. Across the face the
     * ground follows a profile set every rib_spacing blocks; down the face it is dead straight.
     * The profile is buttresses: flat panels, each set a little in or out, with a sharp V gully
     * between each pair, so the face is still made of big flat sides.
     */
    private double pyramid(double dx, double dz, long ix, long iz, long seed, double[] ribOut) {
        double faceSlope = slope;
        int k = faceCount(ix, iz, seed);
        double depth = ribDepth, spacing = ribSpacing;
        double fall = edgeMin * faceSlope * Math.hypot(dx, dz);              // no edge shallower than this
        double ribOfFall = 0;
        for (int f = 0; f < k; f++) {
            double a = faceAngle(ix, iz, seed, f, k);
            double s = faceSlope * (1 + slopeVar * (2 * hash(ix, iz, seed + 30 + f) - 1));
            double out = dx * Math.cos(a) + dz * Math.sin(a);                // how far out along this face's own direction
            double down = out * s, rib = 0;
            if (depth > 0 && out > 0) {
                double across = (-dx * Math.sin(a) + dz * Math.cos(a)) / spacing + 50 * hash(ix, iz, seed + 40 + f);
                double knot = Math.floor(across), t = across - knot;
                long salt = seed + 60 + ix * 31 + iz * 17;
                double here = hash((long) knot, f, salt), next = hash((long) knot + 1, f, salt);
                // "here" is this panel's own level; the gullies sit on the lines between panels
                double before = hash((long) knot - 1, f, salt);
                boolean nearStart = t < 0.5;
                double toLine = nearStart ? t : 1 - t;                        // distance to the nearer line, in panels
                double other = nearStart ? before : next;
                double level = (here * 2 - 1) * PANEL_STEP, cut = 0;
                if (toLine < GULLY_HALF) {
                    double g = 1 - toLine / GULLY_HALF;                       // 1 on the line, 0 at the gully's edge
                    level += g * (other - here) * PANEL_STEP;                 // meet the neighbour half-way on the line
                    double lineDepth = depth * (0.55 + 0.45 * hash((long) knot + (nearStart ? 0 : 1), f, salt + 5));
                    cut = lineDepth * g;
                }
                rib = level + cut;
                rib *= Math.min(1, out / RIB_FADE);
                // Never deeper (or prouder) than RIB_SHARE of what the face itself has dropped here.
                // Without this, full-depth gullies 15 blocks from the summit cut one test peak's
                // top into a needle (64 columns across, 12 blocks below the point; now see CHANGELOG).
                double most = RIB_SHARE * down;
                rib = Math.max(-most, Math.min(most, rib));
                down += rib;
            }
            if (down > fall) { fall = down; ribOfFall = rib; }
        }
        ribOut[0] = ribOfFall;
        return fall;
    }

    @Override
    public double at(int blockX, int blockZ) {
        return column(blockX, blockZ)[0];
    }

    /** The last column asked for, per thread: terrain, surface rules and biomes ask about the same column in a row. */
    private static final ThreadLocal<double[]> LAST = ThreadLocal.withInitial(() -> new double[]{Double.NaN, Double.NaN, NO_PEAK, 0, 0});
    private static final ThreadLocal<double[]> RIB = ThreadLocal.withInitial(() -> new double[1]);

    /**
     * The peaks at one column: {level the peaks reach here (offset units, NO_PEAK if none), base
     * level of the piece that forms the surface here (offset units), that piece's height (blocks),
     * how far the ribs and gullies have moved the ground here (blocks; above zero in a gully)}.
     * A "piece" is either a peak's main pyramid or one of its shoulders; the surface rules and the
     * snow use the piece's own base and height, so every shoulder shades and takes snow as a peak
     * of its own. The returned array is reused by the calling thread: read it, don't keep it.
     */
    double[] column(int blockX, int blockZ) {
        double[] last = LAST.get();
        double world = AtlasMountainsMod.seed(), self = System.identityHashCode(this);
        double[] out = RESULT.get();
        if (last[0] == blockX && last[1] == blockZ && last[2] == world && last[3] == self) return out;
        evaluate(blockX, blockZ, out);
        last[0] = blockX; last[1] = blockZ; last[2] = world; last[3] = self;
        return out;
    }

    private static final ThreadLocal<double[]> RESULT = ThreadLocal.withInitial(() -> new double[4]);

    private void evaluate(int blockX, int blockZ, double[] out) {
        long seed = AtlasMountainsMod.seed();
        double x = blockX, z = blockZ;
        double wx = x + warp * fbm(x / WARP_LENGTH, z / WARP_LENGTH, seed + 1, 3);
        double wz = z + warp * fbm(x / WARP_LENGTH, z / WARP_LENGTH, seed + 2, 3);
        long cx = (long) Math.floor(wx / cell), cz = (long) Math.floor(wz / cell);
        int shLow = shoulders.get(0), shHigh = shoulders.get(1);
        // Beyond this distance nothing of even the tallest peak, or of a shoulder standing off from
        // it, is left. The loop looks three cells each way, which covers at least 3.15 cells (724
        // blocks at cell 230) in every direction.
        double maxReach = reach(height) + (shHigh > 0 ? STEP_FAR * height : 0);
        double best = NO_PEAK, bestBase = 0, bestH = 0, bestRib = 0;
        double[] ribOut = RIB.get();
        // The river walls (see wallSlope). The land's level and the distance to the nearest river at
        // this column are only looked up if some piece of a peak actually stands here.
        RiverWallFunction walls = RiverWallFunction.current;
        double landHere = Double.NaN, riverBlocks = 0;
        for (int dj = -3; dj <= 3; dj++) {
            for (int di = -3; di <= 3; di++) {
                long ix = cx + di, iz = cz + dj;
                double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * JITTER) * cell;
                double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * JITTER) * cell;
                double dx = wx - sx, dz = wz - sz;
                if (Math.hypot(dx, dz) > maxReach) continue;
                // The peak's height is its own draw times how strongly mountain country holds at its
                // summit. The slopes are never scaled, so a weaker peak is lower and narrower with the
                // same angles; one that would come out shorter than min_height is left out altogether.
                double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
                h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                if (h < minHeight) continue;
                double base = PeakStrength.landAt(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                // A peak is the highest of several pyramids joined together, all standing on the
                // same level: the summit pyramid and its shoulders. Since 0.4.7 each shoulder stands
                // out in front of the middle of one of the main pyramid's faces (one shoulder per
                // face at most), 0.55 to 0.85 of the peak's height from its summit. That is where the
                // main pyramid falls away fastest, so every shoulder shows a summit of its own with
                // a notch behind it (median about 26 blocks in a check of 240 shoulders; Sammi: "add
                // more distinct peaks"). In 0.4.6 they stood closer in and in any direction, and in
                // that check only 28% showed their own summit, none with a real notch.
                // Each piece is first held under its own river wall, then the highest piece wins.
                //
                // The pieces after the first are the steps on the peak's edges (0.4.10). An edge
                // (the line where two faces meet) no longer runs dead straight from summit to
                // foot: part-way down it a sub-summit stands up out of it, so the edge drops into
                // a notch, rises to a smaller point and carries on down, as on the right-hand side
                // of the 0.4.6 peak Sammi picked out and the ridges of his reference pictures. A
                // step is a small copy of the peak itself, turned the same way, with its summit on
                // the edge and a little higher than the edge is there. A peak has one or two: on
                // two different edges, or both on one edge, one below the other. Never more, so
                // an edge does not become a row of peaks (ridge spam, rejected). In 0.4.7 to 0.4.9
                // these pieces stood in front of the middle of a face instead, where they read as
                // small pyramids stuck on.
                int faces = faceCount(ix, iz, seed);
                int steps = (int) Math.floor(shLow + hash(ix, iz, seed + 8) * (shHigh - shLow + 1));
                for (int piece = 0; piece <= steps; piece++) {
                    double px = sx, pz = sz, ph = h;
                    if (piece > 0) {
                        int j = piece - 1;
                        long stepSalt = seed + 200 + 100L * j;
                        int firstEdge = (int) Math.floor(hash(ix, iz, seed + 9) * faces);
                        boolean sameEdge = hash(ix, iz, seed + 12) < 0.5;
                        int edge = j == 0 || sameEdge ? firstEdge : (firstEdge + 1 + (int) Math.floor(hash(ix, iz, seed + 13) * (faces - 1))) % faces;
                        double along = h * (STEP_NEAR + STEP_SPREAD * hash(ix, iz, seed + 201));
                        if (j > 0 && sameEdge) along += h * (STEP_GAP + STEP_SPREAD * hash(ix, iz, stepSalt + 1));
                        else if (j > 0) along = h * (STEP_NEAR + STEP_SPREAD * hash(ix, iz, stepSalt + 1));
                        // where the edge between this face and the next runs, and how fast it drops
                        double a1 = faceAngle(ix, iz, seed, edge, faces), a2 = faceAngle(ix, iz, seed, (edge + 1) % faces, faces);
                        double between = a2 - a1;
                        while (between <= 0) between += 2 * Math.PI;
                        double s1 = slope * (1 + slopeVar * (2 * hash(ix, iz, seed + 30 + edge) - 1));
                        double s2 = slope * (1 + slopeVar * (2 * hash(ix, iz, seed + 30 + (edge + 1) % faces) - 1));
                        double off = Math.atan2(s1 - s2 * Math.cos(between), s2 * Math.sin(between));
                        double drop = Math.max(edgeMin * slope, s1 * Math.cos(off));
                        ph = standing(h, along * drop) + h * (STEP_RISE + STEP_RISE * hash(ix, iz, stepSalt + 3));
                        if (ph < 25) continue;
                        px = sx + along * Math.cos(a1 + off); pz = sz + along * Math.sin(a1 + off);
                    }
                    long salt = seed;
                    double top = base + standing(ph, pyramid(wx - px, wz - pz, ix, iz, salt, ribOut)) / 128.0;
                    if (top <= best) continue;
                    double rib = ribOut[0];
                    if (walls != null) {
                        if (Double.isNaN(landHere)) {
                            landHere = PeakStrength.land(blockX, blockZ);
                            riverBlocks = RiverDistance.at(blockX, blockZ, walls.channel());
                        }
                        // The wall carries the face's own ribs and gullies, so it is not one bare
                        // slab (Sammi's screenshot of 12 Nov 2026, a dead-flat plane hundreds of
                        // blocks wide). They fade out towards the river, which stays clear.
                        if (top > landHere) {
                            double wallRib = rib * Math.min(1, riverBlocks / WALL_RIB_FADE);
                            top = Math.min(top, landHere + (riverBlocks * wallSlope(walls, ix, iz, piece, px, pz, ph, base) - wallRib) / 128.0);
                        }
                        if (top <= best) continue;
                    }
                    best = top; bestBase = base; bestH = ph; bestRib = rib;
                }
            }
        }
        out[0] = best; out[1] = bestBase; out[2] = bestH; out[3] = bestRib;
    }

    /** Each piece's wall steepness, worked out once: see wallSlope. */
    private static final java.util.concurrent.ConcurrentHashMap<Long, Float> WALL_SLOPES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Forgets the remembered wall slopes: called when a world starts or stops. */
    static void forget() { WALL_SLOPES.clear(); }

    /** A step stands on its edge between STEP_NEAR and STEP_NEAR + STEP_SPREAD of the peak's height out from the summit; a second one on the same edge at least STEP_GAP further out. */
    static final double STEP_NEAR = 0.30, STEP_SPREAD = 0.15, STEP_GAP = 0.25, STEP_FAR = STEP_NEAR + STEP_GAP + 2 * STEP_SPREAD;
    /** A step's summit stands this share of the peak's height (once to twice it) above the edge it sits on. */
    static final double STEP_RISE = 0.07;

    /** The ribs on a river wall come in to full depth over this many blocks from the river. */
    static final double WALL_RIB_FADE = 40.0;

    // ---- the snow line (0.4.9) ----------------------------------------------------------------
    /** Left to itself, a peak's snow starts this share of its height below its summit ... */
    static final double SNOW_SHARE = 0.25;
    /** ... but each peak's line is blended with its neighbours' over about this many cells. */
    static final double SNOW_BLEND_CELLS = 0.6;

    /**
     * The height (y) of the snow line at a column, before climate. NaN where there are no peaks about.
     * <p>
     * Up to 0.4.8 every peak and every shoulder had a cap on the top 28% of its own height, so a
     * small shoulder half-way down a mountain wore a white hat while the main face above it was
     * bare ("white tents", Sammi's screenshots of 12 Nov 2026). Real snow starts at about one
     * height across a mountainside. So the line is now one smooth surface over the whole range:
     * each main peak wants snow on the top SNOW_SHARE of its height, and at any column the line is
     * the average of the nearby peaks' wishes, the nearest counting most (and taller peaks more
     * than small ones). Round a summit it is mostly that peak's own line, so every main peak still
     * has a cap; shoulders have no say, so they are white only if they reach up into their
     * mountain's snow; and between neighbouring peaks the line runs smoothly from one to the other.
     * <p>
     * A first version used one flat line for a whole range, set from its average summit. Summits
     * in one range differ by 60 blocks or more (the land under them does), so that left most of a
     * row of peaks Sammi had liked with no snow and buried the tallest one.
     */
    double snowLine(int blockX, int blockZ) {
        return snowLine(blockX, blockZ, null);
    }

    /** The peaks round one cell, per thread: {summit x, summit z, height, summit y} each. Columns arrive a cell at a time. */
    private static final class Near {
        long cx = Long.MIN_VALUE, cz, seed;
        int owner, count;
        final double[] peaks = new double[25 * 4];
    }

    private static final ThreadLocal<Near> NEAR = ThreadLocal.withInitial(Near::new);

    /** As snowLine; also puts the height of the peaks round here (blended the same way) in heightOut[0]. */
    double snowLine(int blockX, int blockZ, double[] heightOut) {
        long seed = AtlasMountainsMod.seed();
        long cx = (long) Math.floor(blockX / cell), cz = (long) Math.floor(blockZ / cell);
        Near near = NEAR.get();
        if (near.cx != cx || near.cz != cz || near.seed != seed || near.owner != System.identityHashCode(this)) {
            // Peaks more than two cells off count for under a five-hundredth of one close by.
            int n = 0;
            for (long iz = cz - 2; iz <= cz + 2; iz++)
                for (long ix = cx - 2; ix <= cx + 2; ix++) {
                    double sx = (ix + 0.5 + (hash(ix, iz, seed + 3) - 0.5) * JITTER) * cell;
                    double sz = (iz + 0.5 + (hash(ix, iz, seed + 4) - 0.5) * JITTER) * cell;
                    double h = height * (1 - heightVar * Math.pow(hash(ix, iz, seed + 5), 1.5));
                    h *= PeakStrength.at(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz));
                    if (h < minHeight) continue;
                    near.peaks[n++] = sx; near.peaks[n++] = sz; near.peaks[n++] = h;
                    near.peaks[n++] = 128 + 128 * PeakStrength.landAt(ix, iz, (int) Math.floor(sx), (int) Math.floor(sz)) + h;
                }
            near.count = n; near.cx = cx; near.cz = cz; near.seed = seed; near.owner = System.identityHashCode(this);
        }
        double spread = SNOW_BLEND_CELLS * cell, sum = 0, weight = 0, heights = 0;
        for (int k = 0; k < near.count; k += 4) {
            double dx = blockX - near.peaks[k], dz = blockZ - near.peaks[k + 1], h = near.peaks[k + 2], tall = h / height;
            double w = tall * tall * Math.exp(-(dx * dx + dz * dz) / (2 * spread * spread));
            sum += w * (near.peaks[k + 3] - SNOW_SHARE * h); weight += w; heights += w * h;
        }
        if (weight < 1e-12) return Double.NaN;
        if (heightOut != null) heightOut[0] = heights / weight;
        return sum / weight;
    }

    /**
     * How steeply the river wall of one piece of a peak rises: blocks up per block of distance
     * from the nearest river channel.
     * <p>
     * Beside a river no piece may stand higher above the land than its distance from the channel
     * times this, so the river runs in a straight-sided V. The wall is normally at the angle of the
     * peaks' own faces ({@code slope}, 44 degrees). But a summit that stands close to a river
     * cannot be reached by a wall that gentle, and the first build of 0.4.8 cut the tops off 7 of 9
     * summits in a test square on Sammi's seed. So a piece whose summit is close to a river gets a
     * steeper wall, just steep enough to clear its own summit with WALL_MARGIN to spare, up to
     * {@code max_slope}. The wall is still one flat plane per piece. A summit closer to a river
     * than even that allows still loses its top.
     */
    private double wallSlope(RiverWallFunction walls, long ix, long iz, int piece, double px, double pz, double ph, double base) {
        long key = (ix * 0x9E3779B97F4A7C15L) ^ (iz * 0xC2B2AE3D27D4EB4FL) ^ (piece * 0x165667B19E3779F9L);
        Float known = WALL_SLOPES.get(key);
        if (known != null) return known;
        int sxBlock = (int) Math.floor(px), szBlock = (int) Math.floor(pz);
        double river = RiverDistance.at(sxBlock, szBlock, walls.channel());
        double summitAboveLand = 128.0 * (base - PeakStrength.land(sxBlock, szBlock)) + ph;
        double needed = river >= RiverDistance.REACH ? 0 : WALL_MARGIN * summitAboveLand / Math.max(1.0, river);
        float value = (float) Math.max(walls.slope(), Math.min(walls.maxSlope(), needed));
        if (WALL_SLOPES.size() > 400_000) WALL_SLOPES.clear();
        WALL_SLOPES.put(key, value);
        return value;
    }

    /** A piece's wall is made steep enough to clear its own summit by this factor. */
    private static final double WALL_MARGIN = 1.15;

    @Override
    public Interval range() { return Interval.of((float) NO_PEAK, (float) (4.0 + height / 128.0)); }

    @Override
    public MapCodec<? extends DensityFunction> codec() { return CODEC; }
}
