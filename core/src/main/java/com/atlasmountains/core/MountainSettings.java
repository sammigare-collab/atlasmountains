package com.atlasmountains.core;

/**
 * Every size, angle, spacing and strength used by the generator, in one place. Units are
 * blocks unless stated. Tuning is done by changing these values, never by rewriting generator
 * code. The values were tuned in game; several heights are repeated in
 * tools/build_datapack.py and SettingsSyncTest checks the two agree.
 */
public final class MountainSettings {

    // ---- Region grid -------------------------------------------------------------
    /** Side of one region in blocks. */
    public int regionSizeBlocks = 2048;

    // ---- Field layout (see FieldLayout) --------------------------------------------
    /** Voronoi cell size: ridges enclose cells about this big, so neighbouring ridges sit about this far apart. */
    public double fieldCellSize = 420;
    /** Site jitter as a fraction of the cell (0 = square grid, 1 = anywhere in the cell). */
    public double fieldJitter = 0.9;
    /**
     * Domain warp of the diagram, so the ridges curve: amplitude of the main octave in blocks
     * (a second octave adds a quarter of this), and its wavelength.
     */
    public double fieldWarpAmplitude = 220;
    public double fieldWarpWavelength = 700;
    /**
     * Constant shift added to the warp, blocks. It has no visual purpose; it keeps every seed's
     * ridges where they were before the warp formula was corrected (Nov 2026). Setting it to 0
     * moves the whole ridge network diagonally by about 190 blocks.
     */
    public double fieldWarpShift = -137.5;
    /**
     * Field layout mask: a cell is in the mountains when vanilla's footprint is on at its site, so
     * the mountain edge follows whole cells and the last ridge stands at full height; the mask
     * then runs one flank beyond the last ridge and fades over this many blocks into vanilla.
     */
    public double fieldEdgeFade = 120;
    /** How far below the valley floor our surface sinks where the mask is 0 (see RegionCache.maskedHeight). */
    public double maskSink = 24;
    /**
     * Typical summit relief multiplier in the field layout (1.0 = the design summit). The relief is
     * fieldHeightBoost * (1 + fieldHeightVariation * n), n in -1..1 over fieldHeightWavelength
     * blocks: with 0.88 and 0.5 that is 0.44..1.32, mostly near 0.88. These are the values tuned
     * in game, restated after a range bug in the old formula was corrected (Nov 2026).
     */
    public double fieldHeightBoost = 0.88;
    /** Relief varies by this fraction either side of fieldHeightBoost. */
    public double fieldHeightVariation = 0.5;
    public double fieldHeightWavelength = 1800;

    // ---- Heightmap sampling ----------------------------------------------------------
    /** Blocks per heightmap sample. */
    public int sampleSpacing = 4;
    /** Extra samples generated on every side so neighbouring regions agree. */
    public int overlapSamples = 64;

    // ---- Vertical layout ---------------------------------------------------------
    /** Flat valley floor height (y). Lakes sit a few blocks below this after valley fill. */
    public double valleyFloorHeight = 64;   // one above vanilla sea level, so lakes share it
    /** Design summit height (y), before the field layout's height scale. Relief = summitHeight - valleyFloorHeight. */
    public double summitHeight = 232;
    /** Fraction of the relief, measured down from the mean summit, that is rock face at the face angle. */
    public double rockFraction = 0.5;

    // ---- Crest profile along a ridge ----------------------------------
    /** Mean peak spacing along the crest. */
    public double peakSpacing = 72;
    /** Peak position jitter as a fraction of peakSpacing. */
    public double peakSpacingJitter = 0.25;
    /** Summit-to-summit height variation along a crest, as a fraction either side of the mean. */
    public double peakHeightVariation = 0.075;
    /**
     * Slope at which the ridge falls away from a summit along the crest (tan). 0.6 is 31 degrees.
     * The true slope of a facet is sqrt(faceSlope^2 + crestFallSlope^2): 1.15 and 0.6 give 52 degrees.
     */
    public double crestFallSlope = 0.60;
    /** Rounding of the saddle between two summits, blocks. 0 is a sharp V. Summits are never rounded. */
    public double saddleRounding = 12;
    /** Summits closer together than this keep a sharp V saddle. */
    public double saddleRoundingMinGap = 64;
    /** Summits further apart than this get the full saddleRounding. */
    public double saddleRoundingMaxGap = 112;

    // ---- Cross profile --------------------------------------------------
    /** Face slope on rock, as tan. 1.0 is 45 degrees, 1.15 is 49 degrees. */
    public double faceSlope = 1.15;
    /** Flank slope just below the rock line, as tan. 0.7 is 35 degrees. */
    public double flankTopSlope = 0.90;
    /** Concavity exponent of the grass flank. 1 is straight, higher is more concave. */
    public double flankConcavity = 1.3;

    // ---- Detail noise ---------------------------------------------------
    /** Peak-to-trough amplitude of the ridged detail noise at the summits, blocks. Seasoning, not shape. */
    public double detailAmplitude = 5.5;
    /** Wavelength of the largest detail octave, blocks. */
    public double detailWavelength = 44;
    /** Octaves of ridged noise. */
    public int detailOctaves = 3;
    /** Detail amplitude is scaled by ((h - floor) / relief) ^ this, so flanks stay smooth. */
    public double detailHeightPower = 2.0;

    // ---- Valley floor and lakes --------------------------------------------------
    /** Lake bed depth below the valley floor at the deepest point, blocks. */
    public double lakeDepth = 5;
    /** Fraction of the floor width taken by the lake where there is one . */
    public double lakeWidthFraction = 0.5;
    /** Wavelength along the valley of the on/off pattern of lakes, blocks. */
    public double lakeWavelength = 720;
    /** Roughly the fraction of valley length that has a lake (0 none, 1 continuous). */
    public double lakeCoverage = 0.55;

    // ---- Thermal erosion ------------------------------------------------
    /**
     * Critical angle on rock, degrees. Computed for a face slope of 1.0 (49 degrees), so it is
     * below the facet slope actually built with faceSlope 1.15 (52 degrees) and thermal erosion
     * does trim the faces slightly. That is the tuned look; it is not tied to faceSlope.
     */
    public double thermalRockAngle = Math.toDegrees(Math.atan(Math.sqrt(1.0 * 1.0 + 0.60 * 0.60)));
    /** Critical angle below the rock line, degrees (gully walls on the grass flank). */
    public double thermalFlankAngle = 36;
    /** Height band over which the critical angle blends, blocks, centred on the rock line. */
    public double thermalBlendBand = 30;
    /** Fraction of the excess moved per neighbour per pass. */
    public double thermalRate = 0.25;
    /** Passes before hydraulic erosion. */
    public int thermalPassesBefore = 40;
    /** Passes after hydraulic erosion, to restore the face angle around gullies. */
    public int thermalPassesAfter = 40;

    // ---- Hydraulic erosion (Beyer droplets) ------------------------------
    public int dropletsPerRegion = 50_000;
    /** Droplets erode only above (rock line - this); below it they only deposit, so the grass flanks stay smooth. */
    public double hydraulicErodeBelowRockLine = 8;
    /** Width of the blend band under that height, blocks. */
    public double hydraulicErodeBand = 16;
    public int dropletMaxLifetime = 120;
    public double dropletInertia = 0.1;
    public double dropletCapacity = 2.5;
    public double dropletMinSlopeCapacity = 0.01;
    public double dropletDeposit = 0.3;
    public double dropletErode = 0.3;
    public double dropletEvaporate = 0.015;
    public double dropletGravity = 0.5;
    /** Speed cap so droplets on long faces do not run away. */
    public double dropletMaxSpeed = 3;
    /** Most height a droplet may remove from its footprint in one step, blocks. */
    public double dropletMaxErodePerStep = 0.3;
    public double dropletInitialSpeed = 1;
    public double dropletInitialWater = 1;
    /** Erosion brush radius in samples. */
    public int dropletRadius = 1;

    // ---- Vanilla's mountain footprint (mixed preset) -----------------------------------------
    /**
     * A field cell is mountain when the world's erosion at its site, plus the ragged noise below,
     * is under this. Vanilla's own peaks start at about -0.375; lower = smaller provinces.
     * Restated with footprintRagged after a range bug in the old formula was corrected (Nov 2026);
     * the old pair (-0.42, 0.1) gives exactly the same provinces.
     */
    public double erosionMountainBelow = -0.32;
    /**
     * Province edges are roughened: a cell counts as mountain when erosion + footprintRagged * n
     * (n in -1..1, wavelength footprintRaggedWavelength) is below the threshold, so the edge breaks
     * into bays and outliers instead of following one smooth erosion contour.
     */
    public double footprintRagged = 0.2;
    /**
     * The footprint reads the erosion noise at this many times the biome layer's frequency, so
     * mountain provinces are smaller than the other biomes (2 = vanilla-sized provinces while the
     * biomes stay at twice vanilla).
     */
    public double footprintErosionScale = 2.5;
    public double footprintRaggedWavelength = 900;
    /**
     * The erosion noise is stretched by this ratio along a seed-chosen direction (1 = no stretch;
     * 2, elongated provinces, was tried and dropped).
     */
    public double erosionStretch = 1.0;

    // ---- Coast fade of the mountains ------------------------------------------------
    /** Continentalness at and below which mountains are fully faded out (coast is -0.19..-0.11; -0.15 is the shoreline). */
    public double maskInlandStart = -0.15;
    /** Continentalness at and above which mountains are fully on. */
    public double maskInlandFull = 0.05;

    // ---- Continents (mixed preset; NoiseContinents replaces vanilla continentalness) ----
    /** Wavelength of the largest continent noise octave, blocks. Sets continent and ocean size. */
    public double continentWavelength = 36000;
    /** Octaves of continent noise; each halves the wavelength. More = finer coastal detail. */
    public int continentOctaves = 7;
    /** Amplitude falloff per octave (0.5 = rough coasts, 0.65 = rougher, 0.4 = smooth blobs). */
    public double continentGain = 0.5;
    /** Noise value above which there is land. Higher = less land. About 0.1 gives a third land. */
    public double continentLandThreshold = 0.18;
    /** Spawn island: the noise is raised by this at the origin, fading out over continentSpawnRadius blocks. */
    public double continentSpawnBoost = 0.4;
    public double continentSpawnRadius = 12000;
    /** Land is guaranteed within this radius of the origin (a dome of at least this noise value above the threshold at the centre). */
    public double continentSpawnGuaranteedRadius = 5000;
    public double continentSpawnGuaranteedValue = 0.25;

    // ---- Continentalness ramp: signed distance to the shore -> vanilla's scale (Continents.fromDistance)
    /** Half-width of the coast band, blocks: continentalness runs -0.25..-0.05 over +/- this around the shoreline. */
    public double coastHalfWidth = 100;
    /** Blocks inland at which continentalness reaches 0.05 (near inland ends, mountains fully on). */
    public double nearInlandDistance = 250;
    /** Blocks inland at which continentalness reaches 0.45 (far inland). */
    public double farInlandDistance = 1200;
    /** Blocks offshore at which continentalness reaches -0.5 (ocean). */
    public double oceanDistance = 700;
    /** Blocks offshore at which continentalness reaches -1.0 (deep ocean). */
    public double deepOceanDistance = 3000;

    // ---- Derived helpers ---------------------------------------------------------
    public double relief() { return summitHeight - valleyFloorHeight; }
    public double rockLineHeight() { return summitHeight - rockFraction * relief(); }
    public double flankRelief() { return rockLineHeight() - valleyFloorHeight; }
    /** Horizontal length of the flank from rock line to valley edge, for the mean crest height. */
    public double flankLength() { return flankConcavity * flankRelief() / flankTopSlope; }
    /** Horizontal run of the rock face from the mean summit down to the rock line. */
    public double meanRockRun() { return (summitHeight - rockLineHeight()) / faceSlope; }
    /** Distance from a ridge line to the valley edge; a range's half-width. */
    public double halfWidth() { return meanRockRun() + flankLength(); }
    public int samplesPerRegion() { return regionSizeBlocks / sampleSpacing; }
    public int gridSize() { return samplesPerRegion() + 2 * overlapSamples; }
}
