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

    // ---- Mountain mask (mixed world) ---------------------------------------------
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
    /** Continentalness at and above which mountains are fully on (0.05 is {@link #nearInlandDistance} blocks inland). */
    public double maskInlandFull = 0.05;

    // ---- Plate continents (mixed world; replaces vanilla continentalness) -----------
    /** Lattice spacing of the Voronoi plates, blocks. A continent is a cluster of a few plates. */
    public double plateSizeBlocks = 10000;
    /**
     * Fraction of plates that are land. Adjacent land plates merge into one continent, so this
     * sets continent size: at 0.4 a continent is typically 3 to 8 plates and ringed by sea;
     * above about 0.55 the land joins up into one world-spanning mass. The origin plate is always land.
     */
    public double plateLandFraction = 0.32;
    /** The coast is pulled this far inside the land plates, blocks (widens the sea between continents). */
    public double coastInset = 1200;
    /** Cell-centre jitter as a fraction of the lattice spacing (0 = square grid, 1 = anywhere in the cell). */
    public double plateJitter = 0.8;
    /** Domain-warp amplitude, blocks: how far plate edges bend away from straight lines. */
    public double plateWarpAmplitude = 2400;
    /** Wavelength of that warp, blocks. */
    public double plateWarpWavelength = 9000;
    /** Second, medium-scale warp: breaks the remaining straight edges into lobes and inlets. Amplitude, blocks. */
    public double plateWarp2Amplitude = 1000;
    /** Wavelength of the medium warp, blocks. */
    public double plateWarp2Wavelength = 2600;
    /** Each plate is stretched along a random direction by a ratio between 1 and this, so continents are elongated. */
    public double plateStretchMax = 2.2;
    /** Amplitude of the kilometre-scale coast shape noise, blocks: bays, peninsulas, offshore islets. */
    public double coastShapeAmplitude = 900;
    /** Wavelength of the coast shape noise, blocks. */
    public double coastShapeWavelength = 4500;
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
