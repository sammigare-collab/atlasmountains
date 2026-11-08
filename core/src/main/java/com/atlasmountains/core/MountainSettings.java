package com.atlasmountains.core;

/**
 * Every size, angle, spacing and strength used by the generator, in one place.
 * Defaults come from the geometry table in the brief. Units are blocks unless stated.
 * Tuning is done by changing these values, never by rewriting generator code.
 */
public final class MountainSettings {

    // ---- Region grid -------------------------------------------------------------
    /** Side of one region in blocks. */
    public int regionSizeBlocks = 2048;
    /** Blocks per heightmap sample. */
    public int sampleSpacing = 4;
    /** Extra samples generated on every side so neighbouring regions agree. */
    public int overlapSamples = 64;

    // ---- Vertical layout ---------------------------------------------------------
    /** Flat valley floor height (y). Lakes sit a few blocks below this after valley fill. */
    public double valleyFloorHeight = 64;   // one above vanilla sea level, so lakes share it
    /** Mean summit height (y). Relief = summitHeight - valleyFloorHeight, target 450 to 600. */
    public double summitHeight = 272;   // relief 208, about 25% above vanilla peaks (original design was 520; every length scaled by 0.4)   // relief 416 (was 520; "same design, slightly smaller": every length x0.8)
    /** Fraction of the relief, measured down from the mean summit, that is rock face at the face angle. */
    public double rockFraction = 0.5;

    // ---- Range layout (step 2) ---------------------------------------------------
    /** Mean centre-to-centre spacing of range spines. */
    public double rangeSpacing = 760;
    /** Random offset applied to each spine, +/- this, so spacing varies. */
    public double rangeSpacingJitter = 60;
    /** Fraction of spine pairs that are pulled together back to back (parallel layout). */
    public double pairTightChance = 0.7;
    /** How far each spine of a tight pair moves towards the other, blocks. With rangeSpacing 760 and pull 150 the gap inside the pair is 460 and between pairs 1060. */
    public double pairPull = 150;
    /** Fraction of the remaining (wide) gaps that are flat valleys with lakes; the rest are filled by vanilla terrain. */
    public double valleyChance = 0.4;
    /** Amplitude of the gentle curve of each spine. */
    public double spineCurveAmplitude = 72;
    /** Wavelength of that curve along the spine. */
    public double spineCurveWavelength = 1280;

    // ---- Crest profile along the spine (step 3) ----------------------------------
    /** Mean peak spacing along the crest (target 150 to 300). */
    public double peakSpacing = 88;
    /** Peak position jitter as a fraction of peakSpacing (target 0.2 to 0.3). */
    public double peakSpacingJitter = 0.25;
    /** Summit height variation as a fraction of the mean (target about 0.15 total spread). */
    public double peakHeightVariation = 0.075;
    /**
     * Slope at which the ridge falls away from a summit along the crest (tan). 0.6 is 31 degrees.
     * The true slope of a facet is sqrt(faceSlope^2 + crestFallSlope^2): 1.0 and 0.6 give 49 degrees.
     */
    public double crestFallSlope = 0.60;
    /** Rounding of the saddle between two summits, blocks. 0 is a sharp V. Summits are never rounded. */
    public double saddleRounding = 12;
    /** Summits closer together than this keep a sharp V saddle. */
    public double saddleRoundingMinGap = 64;
    /** Summits further apart than this get the full saddleRounding. */
    public double saddleRoundingMaxGap = 112;

    // ---- Cross profile (step 3) --------------------------------------------------
    /** Face slope on rock, as tan. 1.0 is 45 degrees. Also the thermal critical angle on rock. */
    public double faceSlope = 1.0;
    /** Flank slope just below the rock line, as tan. 0.7 is 35 degrees. */
    public double flankTopSlope = 0.70;
    /** Concavity exponent of the grass flank. 1 is straight, higher is more concave. */
    public double flankConcavity = 1.5;

    // ---- Detail noise (step 4) ---------------------------------------------------
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
    /** Lake surface sits this many blocks below the valley floor (this becomes sea level). */
    public double lakeSurfaceBelowFloor = 1;
    /** Fraction of the floor width taken by the lake where there is one (target 0.3 to 0.6). */
    public double lakeWidthFraction = 0.5;
    /** Wavelength along the valley of the on/off pattern of lakes, blocks. */
    public double lakeWavelength = 720;
    /** Roughly the fraction of valley length that has a lake (0 none, 1 continuous). */
    public double lakeCoverage = 0.55;

    // ---- Thermal erosion (step 5) ------------------------------------------------
    /** Critical angle on rock, degrees. Default equals the built facet slope so the faces are kept, not cut. */
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

    // ---- Hydraulic erosion (step 6, Beyer droplets) ------------------------------
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

    // ---- Layout mode -------------------------------------------------------------
    /**
     * true: mountain chains ({@link ChainLayout}; the mixed "Atlas Mountains" preset).
     * false: parallel spines over the whole world ({@link RangeLayout}; the "everywhere" preset and the diagnostics).
     */
    public boolean chainLayout = false;

    // ---- Mountain chains (chainLayout = true) ------------------------------------
    /** Cell size of the chain lattice, blocks. One chain per cell at most; sets how far apart chains are. */
    public double chainCellSize = 4500;
    /** Probability that a cell has a chain (before the erosion gate). */
    public double chainChance = 0.95;
    /**
     * Biome-layer gate: a chain is placed with full chainChance where the world's erosion noise at
     * its origin is below this minus half chainErosionBand, with no chance above this plus half the
     * band. Vanilla: erosion below about -0.375 is its mountain range, -0.375..0.05 hills, above that flat.
     */
    public double chainErosionBelow = 2.0;      // gate off: the mountain footprint mask decides where chains show
    public double chainErosionBand = 0.4;
    /** Chain length, blocks, uniform between these two. */
    public double chainLengthMin = 2500;
    public double chainLengthMax = 5500;
    /** A chain is at least this many times longer than its zone is wide, so multi-range chains are long massifs rather than blobs. */
    public double chainMinAspect = 3.5;
    /** Lateral curve of the chain centreline: amplitude and wavelength, blocks. */
    public double chainCurveAmplitude = 700;
    public double chainCurveWavelength = 3500;
    /** Spines (ranges) per chain: bounds, and the relative weights of 1, 2, 3 and 4 spines. */
    public int chainRangesMin = 1;
    public int chainRangesMax = 4;
    public double[] chainRangesWeights = {30, 38, 24, 8};
    /** Width of the inner-mask fade outside the outermost spine, blocks (see ChainLayout.inner). */
    public double innerFade = 120;
    /**
     * Gap between neighbouring spines in a chain, blocks. A tight gap (less than two range half-widths,
     * about 650) makes the ranges meet back to back in a col; a wide gap leaves a flat valley with lakes.
     */
    public double spineGapTightMin = 360;
    public double spineGapTightMax = 520;
    public double spineGapWideMin = 760;
    public double spineGapWideMax = 900;
    /** Fraction of gaps that are wide (the flat valleys). */
    public double spineGapWideChance = 0.35;
    /** Probability that a chain forks once; a child forks again with 0.6 of this, up to chainMaxBranchDepth. */
    public double chainBranchChance = 0.7;
    public int chainMaxBranchDepth = 2;
    /** Angle between a child chain and its parent, degrees, uniform between these two. */
    public double chainBranchAngleMin = 35;
    public double chainBranchAngleMax = 65;
    /** Child length as a fraction of the parent's, uniform between these two. */
    public double chainBranchLengthMin = 0.45;
    public double chainBranchLengthMax = 0.8;
    /** Each chain's summits are scaled by a factor uniform between these two (whole chains differ in height). */
    public double chainHeightScaleMin = 0.85;
    public double chainHeightScaleMax = 1.2;
    /** Outer spines of a chain are this fraction lower than the middle one (foothills building up to the main range). */
    public double foothillDrop = 0.3;
    /** One summit per chain, on the middle spine, is up to this fraction taller than its neighbours, over +/- dominantSummitLength blocks. */
    public double dominantSummitBoost = 0.3;
    public double dominantSummitLength = 150;
    /** Summits within junctionBoostLength (blocks, along the spine) of a fork are up to this fraction taller. */
    public double junctionBoost = 0.18;
    public double junctionBoostLength = 450;
    /** Zone capsule extends this far beyond the outer spine's valley edge before the mask starts to fade. */
    public double chainZoneMargin = 0;
    /** Width of the mask fade at the chain sides and ends, blocks (relief tapers to vanilla over it). */
    public double chainEdgeWidth = 500;

    // ---- Mountain mask from vanilla's mountain footprint (chainLayout = false, mixed world) ------
    /** Zones are where the world's erosion is below this (vanilla's peaks sit below about -0.375), fading over erosionMaskBand. */
    public double erosionMountainBelow = -0.375;
    public double erosionMaskBand = 0.12;
    /** The erosion noise is stretched by this ratio along the range direction, so mountain provinces are elongated. */
    public double erosionStretch = 2.0;

    // ---- Mountain mask: stretched zone noise (fallback when no erosion field is supplied) ------
    /** Wavelength of the mask noise along the range direction, blocks. Zones are long this way. */
    public double maskWavelengthAlong = 6000;
    /** Wavelength across the range direction, blocks. Zones are narrow this way. */
    public double maskWavelengthAcross = 1400;
    /** Octaves of mask noise (more = less rounded zones). */
    public int maskOctaves = 2;
    /** Noise value (-1..1) above which a zone exists. Higher = fewer, smaller zones. */
    public double maskThreshold = 0.16;
    /** Width of the zone edge in noise units; the terrain blends from vanilla to ours over it. */
    public double maskEdgeWidth = 0.16;
    /** Continentalness at and below which mountains are fully faded out (coast is -0.19..-0.11; -0.15 is the shoreline). */
    public double maskInlandStart = -0.15;
    /** Continentalness at and above which mountains are fully on. */
    public double maskInlandFull = 0.05;

    // ---- Continents (mixed world; replaces vanilla continentalness) ------------------
    /** true: noise continents ({@link NoiseContinents}, Continents-mod style). false: Voronoi plates ({@link PlateContinents}). */
    public boolean noiseContinents = true;
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

    // ---- Plate continents (noiseContinents = false). The distance ramp fields at the end apply to both models. ----
    /** Lattice spacing of the Voronoi plates, blocks. A continent is a cluster of a few plates. */
    public double plateSizeBlocks = 15000;
    /** Fraction of plates that are land; adjacent land plates merge. Above about 0.55 the land joins up. The origin plate is always land. */
    public double plateLandFraction = 0.46;
    /** The coast is pulled this far inside the land plates, blocks (widens the sea between continents). */
    public double coastInset = 1000;
    /** Cell-centre jitter as a fraction of the lattice spacing (0 = square grid, 1 = anywhere in the cell). */
    public double plateJitter = 0.8;
    /** Domain-warp amplitude, blocks: how far plate edges bend away from straight lines. */
    public double plateWarpAmplitude = 4500;
    /** Wavelength of that warp, blocks. */
    public double plateWarpWavelength = 14000;
    /** Second, medium-scale warp: breaks the remaining straight edges into lobes and inlets. Amplitude, blocks. */
    public double plateWarp2Amplitude = 1500;
    /** Wavelength of the medium warp, blocks. */
    public double plateWarp2Wavelength = 4500;
    /** Each plate is stretched along a random direction by a ratio between 1 and this, so continents are elongated. */
    public double plateStretchMax = 1.5;
    /** Amplitude of the kilometre-scale coast shape noise, blocks: bays, peninsulas, offshore islets. */
    public double coastShapeAmplitude = 1300;
    /** Wavelength of the coast shape noise, blocks. */
    public double coastShapeWavelength = 6500;
    /** Amplitude of the small coastal noise added to the coast distance, blocks (bays and headlands). */
    public double coastNoiseAmplitude = 160;
    /** Wavelength of the coastal noise, blocks. */
    public double coastNoiseWavelength = 900;
    /** Half-width of the coast band, blocks: continentalness runs -0.25..-0.05 over +/- this around the shoreline. */
    public double coastHalfWidth = 100;
    /** Blocks inland at which continentalness reaches 0.05 (near inland ends, mountains fully on). */
    public double nearInlandDistance = 600;
    /** Blocks inland at which continentalness reaches 0.45 (far inland). */
    public double farInlandDistance = 2500;
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
    /** Distance from spine to the valley edge; the range's half-width. */
    public double halfWidth() { return meanRockRun() + flankLength(); }
    public int samplesPerRegion() { return regionSizeBlocks / sampleSpacing; }
    public int gridSize() { return samplesPerRegion() + 2 * overlapSamples; }
}
