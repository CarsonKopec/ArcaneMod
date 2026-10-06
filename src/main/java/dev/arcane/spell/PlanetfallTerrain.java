package dev.arcane.spell;

/** Seeded crater geometry shared by the server's excavation and the client's impact effects. */
public final class PlanetfallTerrain {
    private static final int FRACTURES = 9;
    private static final double TAU = Math.PI * 2;
    private final int seed;
    private final double phase;

    public PlanetfallTerrain(int seed) {
        this.seed = seed;
        this.phase = noise(seed, 17) * TAU;
    }

    /** Shared air-pressure front: violent contact at the centre, then a rapidly expanding blast. */
    public static double pressureRadius(float elapsed) {
        double progress = Math.max(0, Math.min(1, elapsed / SpellTuning.PLANETFALL_PRESSURE_TICKS));
        return 16 + (SpellTuning.PLANETFALL_IMPACT_RADIUS - 16) * (1 - (1 - progress) * (1 - progress));
    }

    /** The ground ruptures behind the faster air shock, opening channels all the way to the outer scar field. */
    public static double ruptureRadius(float elapsed) {
        return SpellTuning.PLANETFALL_CRATER_RADIUS * 1.6
                * Math.sqrt(Math.max(0, Math.min(1, elapsed / SpellTuning.PLANETFALL_RUPTURE_TICKS)));
    }

    /** The broken, asymmetric lip of the main bowl, measured from the impact. */
    public double rimRadius(double angle) {
        return SpellTuning.PLANETFALL_CRATER_RADIUS * (1 + 0.095 * Math.sin(angle * 3 + phase)
                + 0.045 * Math.sin(angle * 7 - phase * 1.7) + 0.02 * Math.sin(angle * 13 + phase * 2.3));
    }

    /** Depth below the mark, with broad collapsed terraces and narrow fractures through them. */
    public double floorDepth(double dx, double dz) {
        double distance = Math.hypot(dx, dz);
        double relative = distance / rimRadius(Math.atan2(dz, dx));
        if (relative >= 1) return 0;
        double smooth = SpellTuning.PLANETFALL_CRATER_DEPTH * Math.pow(1 - relative * relative, 0.72);
        double terraced = Math.floor(smooth / 4.0) * 4.0;
        double spall = Math.exp(-Math.pow((relative - 0.19) / 0.06, 2))
                * Math.max(0, Math.sin(Math.atan2(dz, dx) * 6 + phase) - 0.35) * 4;
        double depth = smooth * 0.5 + terraced * 0.5 - spall;
        return Math.min(SpellTuning.PLANETFALL_CRATER_DEPTH,
                depth + fractureStrength(dx, dz) * 3.0 * Math.min(1, relative * 2));
    }

    public int fractureCount() {
        return FRACTURES;
    }

    public double fractureAngle(int index) {
        return phase + index * TAU / FRACTURES + (noise(seed, 101 + index) - 0.5) * 0.24;
    }

    /** A slight bend keeps the fractures from resembling straight spokes drawn with a ruler. */
    public double fractureAngle(int index, double distance) {
        return fractureAngle(index) + Math.sin(distance * 0.085 + phase + index) * 0.045;
    }

    public double fractureReach(int index) {
        return SpellTuning.PLANETFALL_CRATER_RADIUS * (1.32 + noise(seed, 211 + index) * 0.25);
    }

    /** 0 outside a fissure; 1 at its hot centre. Fissures taper to a point beyond the crater lip. */
    public double fractureStrength(double dx, double dz) {
        double distance = Math.hypot(dx, dz);
        if (distance < SpellTuning.PLANETFALL_CRATER_RADIUS * 0.32) return 0;
        double angle = Math.atan2(dz, dx);
        double strongest = 0;
        for (int i = 0; i < FRACTURES; i++) {
            double reach = fractureReach(i);
            if (distance >= reach) continue;
            double delta = angle - fractureAngle(i, distance);
            if (Math.cos(delta) < 0) continue;
            double taper = Math.min(1, (reach - distance) / 14);
            double width = (2.4 + noise(seed, 307 + i) * 1.8) * taper;
            double across = Math.abs(Math.sin(delta)) * distance;
            double start = Math.min(1, (distance - SpellTuning.PLANETFALL_CRATER_RADIUS * 0.32) / 7);
            if (across < width) strongest = Math.max(strongest, (1 - across / width) * taper * start);
            double branchStart = SpellTuning.PLANETFALL_CRATER_RADIUS * (0.77 + noise(seed, 401 + i) * 0.1);
            double branchEnd = reach - 4;
            if (distance <= branchStart || distance >= branchEnd) continue;
            double branchTaper = Math.min(1, (branchEnd - distance) / 10);
            double branchWidth = (1.4 + noise(seed, 503 + i)) * branchTaper;
            for (int side = -1; side <= 1; side += 2) {
                double branch = branchAngle(i, side, distance);
                double crossBranch = Math.abs(Math.sin(angle - branch)) * distance;
                if (Math.cos(angle - branch) > 0 && crossBranch < branchWidth)
                    strongest = Math.max(strongest, (1 - crossBranch / branchWidth) * branchTaper * 0.8);
            }
        }
        return strongest;
    }

    /** Forks peel away from the primary scar as they cross the collapsed rim. {@code side} is -1 or +1. */
    public double branchAngle(int index, int side, double distance) {
        double split = SpellTuning.PLANETFALL_CRATER_RADIUS * (0.77 + noise(seed, 401 + index) * 0.1);
        return fractureAngle(index, distance) + side * Math.max(0, distance - split) * 0.018;
    }

    /** Broad coherent lobes leave high rubble banks with lower, breached sectors between them. */
    public double ejectaWeight(double angle) {
        return Math.max(0.3, 0.85 + 0.32 * Math.sin(angle * 3 + phase) + 0.2 * Math.sin(angle * 7 - phase));
    }

    /** Repeatable local variation without consuming the world's random stream. */
    public double variation(int x, int z) {
        return noise(seed ^ x * 0x1f123bb5, z * 0x5f356495);
    }

    private static double noise(int seed, int salt) {
        int hash = seed ^ salt * 0x9e3779b9;
        hash ^= hash >>> 16;
        hash *= 0x7feb352d;
        hash ^= hash >>> 15;
        hash *= 0x846ca68b;
        hash ^= hash >>> 16;
        return (hash & 0x7fffffff) / (double) 0x7fffffff;
    }
}
