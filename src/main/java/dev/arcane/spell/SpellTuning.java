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

    // Celestial Judgment (Astral Staff)

    /** Nearly a full base pool: one cast at a time, unless gear raises max mana. */
    public static final float JUDGMENT_MANA = 90;
    /** One minute. */
    public static final int JUDGMENT_COOLDOWN_TICKS = 1200;
    /** The channel (2 s) must be held to the end; releasing early casts nothing and costs nothing. */
    public static final int JUDGMENT_CHARGE_TICKS = 40;
    /** How far away the caster can mark the ground. */
    public static final double JUDGMENT_RANGE = 96;
    /** The mark must be at least this far away horizontally, so the caster is never inside the crater. */
    public static final double JUDGMENT_MIN_DISTANCE = 18;
    /** From the mark to the beam: the sky darkens and the circles form (2.5 s). */
    public static final int JUDGMENT_GATHER_TICKS = 50;
    /** How long the beam burns before it detonates (1.5 s). */
    public static final int JUDGMENT_BEAM_TICKS = 30;
    /** The crater is carved outwards over this many ticks after the detonation, spreading the work. */
    public static final int JUDGMENT_CARVE_TICKS = 8;
    /** Height of the sky circle above the mark. */
    public static final double JUDGMENT_SKY_HEIGHT = 64;
    /** The beam's radius, and the shaft it bores. */
    public static final double JUDGMENT_BEAM_RADIUS = 3.5;
    /** How far below the mark the shaft goes. */
    public static final int JUDGMENT_SHAFT_DEPTH = 24;
    /** Damage to anything in the beam, every {@link #JUDGMENT_BEAM_HIT_INTERVAL} ticks; it also sets them alight. */
    public static final float JUDGMENT_BEAM_DAMAGE = 12;
    public static final int JUDGMENT_BEAM_HIT_INTERVAL = 10;
    /** The crater's horizontal radius, give or take 15% to roughen the rim. */
    public static final double JUDGMENT_CRATER_RADIUS = 12;
    /** How far the crater reaches below the mark; above it, it reaches as far as it does sideways. */
    public static final double JUDGMENT_CRATER_DEPTH = 9;
    /** Entities this close to the mark are caught by the detonation. */
    public static final double JUDGMENT_BLAST_RADIUS = 16;
    /** Detonation damage at the centre, falling to nothing at the edge. */
    public static final float JUDGMENT_BLAST_DAMAGE = 40;
    public static final double JUDGMENT_BLAST_KNOCKBACK = 2.4;
    /** Strikes in progress at once, server-wide; more fizzle. */
    public static final int MAX_ACTIVE_JUDGMENTS = 8;

    // Meteor Swarm (Astral Staff)

    public static final float METEOR_MANA = 75;
    /** 45 s. */
    public static final int METEOR_COOLDOWN_TICKS = 900;
    /** 1.5 s. */
    public static final int METEOR_CHARGE_TICKS = 30;
    public static final double METEOR_RANGE = 96;
    public static final double METEOR_MIN_DISTANCE = 16;
    /** Meteors land anywhere within this radius of the mark. */
    public static final double METEOR_AREA_RADIUS = 14;
    public static final int METEOR_COUNT = 18;
    /** One meteor every this many ticks: 18 over 3.6 s. */
    public static final int METEOR_INTERVAL_TICKS = 4;
    /** Height of the burning sigil the meteors fall from, above the mark. */
    public static final double METEOR_SKY_HEIGHT = 55;
    /** Blocks per tick. */
    public static final double METEOR_SPEED = 3.0;
    /** A meteor that hits nothing burns out after this long. */
    public static final int METEOR_LIFETIME_TICKS = 60;
    public static final double METEOR_BLAST_RADIUS = 4.5;
    /** At the centre of each impact, falling to nothing at its edge; it also sets things alight. */
    public static final float METEOR_DAMAGE = 14;
    public static final int METEOR_FIRE_SECONDS = 4;
    /** Vanilla explosion strength for terrain; TNT is 4. */
    public static final float METEOR_TERRAIN_STRENGTH = 3.5f;
    public static final int MAX_ACTIVE_SWARMS = 8;

    // Singularity (Astral Staff)

    public static final float SINGULARITY_MANA = 70;
    /** 40 s. */
    public static final int SINGULARITY_COOLDOWN_TICKS = 800;
    public static final int SINGULARITY_CHARGE_TICKS = 30;
    public static final double SINGULARITY_RANGE = 64;
    public static final double SINGULARITY_MIN_DISTANCE = 12;
    /** How high above the mark the hole hangs. */
    public static final double SINGULARITY_LIFT = 3;
    /** 6 s, then it collapses. */
    public static final int SINGULARITY_DURATION_TICKS = 120;
    /** Creatures, items and projectiles this close are dragged in. */
    public static final double SINGULARITY_PULL_RADIUS = 14;
    /** Speed added towards the hole each tick at the edge of its pull, tripling at its heart. */
    public static final double SINGULARITY_PULL = 0.07;
    /** Anything this close to the heart is crushed every {@link #SINGULARITY_CRUSH_INTERVAL} ticks. */
    public static final double SINGULARITY_CRUSH_RADIUS = 2.5;
    public static final float SINGULARITY_CRUSH_DAMAGE = 4;
    public static final int SINGULARITY_CRUSH_INTERVAL = 10;
    /** The hole eats the ground within this radius, nearest first. */
    public static final double SINGULARITY_CONSUME_RADIUS = 5;
    public static final int SINGULARITY_CONSUME_PER_TICK = 3;
    /** The collapse hurls everything this close outwards. */
    public static final double SINGULARITY_COLLAPSE_RADIUS = 9;
    public static final float SINGULARITY_COLLAPSE_DAMAGE = 16;
    public static final double SINGULARITY_COLLAPSE_KNOCKBACK = 2.0;
    public static final int MAX_ACTIVE_SINGULARITIES = 4;

    // Chrono Field (Astral Staff)

    public static final float CHRONO_MANA = 60;
    /** 50 s. */
    public static final int CHRONO_COOLDOWN_TICKS = 1000;
    /** 1 s. */
    public static final int CHRONO_CHARGE_TICKS = 20;
    /** The dome's radius, around where the caster stood. */
    public static final double CHRONO_RADIUS = 10;
    /** 8 s. */
    public static final int CHRONO_DURATION_TICKS = 160;
    /** Damage dealt to frozen creatures is held back, then lands this much harder when time resumes. */
    public static final float CHRONO_DAMAGE_MULTIPLIER = 1.5f;
    public static final int MAX_ACTIVE_CHRONO_FIELDS = 4;

    // Astral Gate (Astral Staff)

    public static final float GATE_MANA = 50;
    /** 30 s. */
    public static final int GATE_COOLDOWN_TICKS = 600;
    public static final int GATE_CHARGE_TICKS = 20;
    public static final double GATE_RANGE = 160;
    /** Nearer than this, Blink does the job. */
    public static final double GATE_MIN_DISTANCE = 8;
    /** From the gates opening to the caster stepping through. */
    public static final int GATE_CROSS_TICKS = 10;
    /** How long the gates stay open, for others to follow. */
    public static final int GATE_OPEN_TICKS = 100;
    /** Anything standing this close to the first gate's centre is carried through. */
    public static final double GATE_RADIUS = 1.5;
    public static final int MAX_ACTIVE_GATES = 8;

    /** Blocks at least this blast resistant survive spells that vaporise terrain, as obsidian (1200) does. */
    public static final float VAPORISE_RESISTANCE_LIMIT = 1200;

    /** Players farther than this are not sent effects; also the client's light and rune render distance. */
    public static final double EFFECT_RANGE = 448;

    private SpellTuning() {
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
