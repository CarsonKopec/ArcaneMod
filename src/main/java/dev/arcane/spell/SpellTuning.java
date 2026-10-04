package dev.arcane.spell;

/** Every gameplay number for the staff in one place, so balancing never means hunting through logic. */
public final class SpellTuning {
    /** Charging this long (1.2 s) gives full power. */
    public static final int FULL_CHARGE_TICKS = 24;
    /** Releases shorter than this count as a cancelled tap. */
    public static final int MIN_CHARGE_TICKS = 4;
    public static final int COOLDOWN_TICKS = 12;
    /** While charging, the server sends a charge effect every this many ticks. */
    public static final int CHARGE_EFFECT_INTERVAL = 2;

    /** 8 s; at full power a bolt covers 400 blocks. */
    public static final int BOLT_LIFETIME_TICKS = 160;
    public static final int MAX_ACTIVE_BOLTS = 256;

    /** Players farther than this are not sent effects; also the client's light and rune render distance. */
    public static final double EFFECT_RANGE = 448;

    private SpellTuning() {
    }

    public static float power(int chargedTicks) {
        return Math.min(chargedTicks / (float) FULL_CHARGE_TICKS, 1f);
    }

    /** Blocks per tick. */
    public static double boltSpeed(float power) {
        return 2.0 + power * 0.5;
    }

    public static double blastRadius(float power) {
        return 4 + power * 4;
    }

    public static float directHitDamage(float power) {
        return 10 + power * 14;
    }

    /** {@code falloff} is 1 at the blast center and 0 at its edge. */
    public static float splashDamage(float power, float falloff) {
        return (6 + power * 14) * falloff;
    }

    /** Vanilla explosion strength used for terrain; TNT is 4. */
    public static float terrainBlastStrength(float power) {
        return 3 + power * 3;
    }
}
