package com.atlasmountains.core;

/**
 * Height across a ridge. From the local crest height the ground falls at the
 * face angle (straight, rock) until it reaches the rock line height, which is the same
 * elevation everywhere. Below the rock line it follows a concave grass flank that meets
 * the flat valley floor tangentially.
 */
public final class CrossProfile {

    private final MountainSettings s;

    public CrossProfile(MountainSettings settings) {
        this.s = settings;
    }

    /**
     * @param crest    crest height at this position along the ridge
     * @param distance horizontal distance from the ridge line, blocks
     * @return terrain height, never below the valley floor
     */
    public double height(double crest, double distance) {
        double rockLine = s.rockLineHeight();
        double rockRun = Math.max(0, (crest - rockLine) / s.faceSlope);
        if (distance <= rockRun) {
            return crest - s.faceSlope * distance;
        }
        // The flank always ends at the same distance from the ridge line (valleyEdge), so the
        // crease under each saddle fades out across the grass flank and the valley edge
        // is a straight line. Flank length therefore varies slightly with the local crest.
        double valleyEdge = s.meanRockRun() + s.flankLength();
        double flankLength = Math.max(1.0, valleyEdge - rockRun);
        double t = (distance - rockRun) / flankLength;
        if (t >= 1) return s.valleyFloorHeight;
        double remaining = Math.pow(1 - t, s.flankConcavity);
        return s.valleyFloorHeight + s.flankRelief() * remaining;
    }
}
