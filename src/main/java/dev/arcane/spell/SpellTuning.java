package dev.arcane.spell;

/** Every gameplay number for the staff in one place, so balancing never means hunting through logic. */
public final class SpellTuning {
    // Mana

    /** Base value of the max mana attribute; gear, effects and other mods may change a player's. */
    public static final float BASE_MAX_MANA = 100;
    /** Base value of the mana regeneration attribute, per second: an empty pool refills in 20 s. */
    public static final float BASE_MANA_REGEN = 5;

    // Arcane Bolt

    public static final float BOLT_MANA = 25;
    public static final int BOLT_COOLDOWN_TICKS = 12;
    /** Charging this long (1.2 s) gives full power. */
    public static final int FULL_CHARGE_TICKS = 24;
    /** Releases shorter than this count as a cancelled tap. */
    public static final int MIN_CHARGE_TICKS = 4;
    /** While charging, the server sends a charge effect every this many ticks. */
    public static final int CHARGE_EFFECT_INTERVAL = 2;
    /** 8 s; at full power a bolt covers 400 blocks. */
    public static final int BOLT_LIFETIME_TICKS = 160;
    public static final int MAX_ACTIVE_BOLTS = 256;

    // Chain Lightning

    public static final float CHAIN_MANA = 30;
    public static final int CHAIN_COOLDOWN_TICKS = 50;
    /** How far the first strike reaches. */
    public static final double CHAIN_RANGE = 24;
    /** Targets' hitboxes count as this much bigger for the first strike, so small or moving mobs are hittable. */
    public static final double CHAIN_AIM_ASSIST = 0.5;
    /** The farthest the lightning leaps from one target to the next. */
    public static final double CHAIN_JUMP_RANGE = 7;
    public static final int CHAIN_MAX_TARGETS = 5;
    public static final float CHAIN_DAMAGE = 8;
    /** Each leap deals this fraction of the previous one's damage. */
    public static final float CHAIN_DAMAGE_FALLOFF = 0.8f;

    // Blink

    public static final float BLINK_MANA = 20;
    public static final int BLINK_COOLDOWN_TICKS = 60;
    public static final double BLINK_RANGE = 16;
    /** Destinations closer than this fail instead of wasting the cast on a shuffle. */
    public static final double BLINK_MIN_DISTANCE = 2;

    // Arcane Ward

    public static final float WARD_MANA = 40;
    /** Counted from the cast, so it covers the 8 s the ward is up. */
    public static final int WARD_COOLDOWN_TICKS = 300;
    public static final int WARD_DURATION_TICKS = 160;
    /** Damage the ward absorbs before it shatters. The hit that breaks it is still fully absorbed. */
    public static final float WARD_STRENGTH = 16;
    /** Strength spent each time the ward turns a projectile back. */
    public static final float WARD_DEFLECT_COST = 2;
    /** Radius of the rune shell, from the centre of the caster's body. */
    public static final double WARD_RADIUS = 2.5;

    // Frost Nova

    public static final float NOVA_MANA = 35;
    public static final int NOVA_COOLDOWN_TICKS = 160;
    public static final double NOVA_RADIUS = 6;
    public static final float NOVA_DAMAGE = 4;
    /** Blazes, magma cubes and other heat-loving mobs take this many times the damage, as with powder snow. */
    public static final float NOVA_HEAT_MULTIPLIER = 2.5f;
    /** Ticks targets stay fully frozen (shaking, frosted, taking vanilla freeze damage) before they start to thaw. */
    public static final int NOVA_FREEZE_TICKS = 100;
    public static final int NOVA_SLOW_TICKS = 100;
    /** Slowness III. */
    public static final int NOVA_SLOW_AMPLIFIER = 2;
    public static final double NOVA_KNOCKBACK = 0.5;

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
