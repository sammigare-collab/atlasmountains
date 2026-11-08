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
    /** Vanilla continentalness at and below which mountains are fully faded out (coast is -0.19..-0.11). */
    public double maskInlandStart = -0.15;
    /** Vanilla continentalness at and above which mountains are fully on. */
    public double maskInlandFull = 0.05;

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
