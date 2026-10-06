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

    public static final float JUDGMENT_MANA = 90;
    /** 45 s. */
    public static final int JUDGMENT_COOLDOWN_TICKS = 900;
    /** The channel (2 s) must be held to the end; releasing early casts nothing and costs nothing. */
    public static final int JUDGMENT_CHARGE_TICKS = 40;
    /** How far away the caster can mark the ground. */
    public static final double JUDGMENT_RANGE = 128;
    /** The mark must be at least this far away horizontally, so the caster is never inside the crater. */
    public static final double JUDGMENT_MIN_DISTANCE = 30;
    /** From the mark to the beam: the sky darkens and the circles form (2.5 s). */
    public static final int JUDGMENT_GATHER_TICKS = 50;
    /** How long the beam burns before it detonates (1.5 s); it bores its shaft down to bedrock in that time. */
    public static final int JUDGMENT_BEAM_TICKS = 30;
    /** The crater is carved outwards over this many ticks after the detonation, spreading the work. */
    public static final int JUDGMENT_CARVE_TICKS = 30;
    /** Height of the sky circle above the mark. */
    public static final double JUDGMENT_SKY_HEIGHT = 80;
    /** The beam's radius, and the shaft it bores from the surface to bedrock. */
    public static final double JUDGMENT_BEAM_RADIUS = 6;
    /** Damage to anything in the beam, every {@link #JUDGMENT_BEAM_HIT_INTERVAL} ticks; it also sets them alight. */
    public static final float JUDGMENT_BEAM_DAMAGE = 30;
    public static final int JUDGMENT_BEAM_HIT_INTERVAL = 10;
    public static final int JUDGMENT_BEAM_FIRE_SECONDS = 10;
    /** The crater's horizontal radius, give or take 15% to roughen the rim. */
    public static final double JUDGMENT_CRATER_RADIUS = 24;
    /** How far the crater reaches below the mark; above it, it reaches as far as it does sideways. */
    public static final double JUDGMENT_CRATER_DEPTH = 16;
    /** Entities this close to the mark are caught by the detonation. */
    public static final double JUDGMENT_BLAST_RADIUS = 32;
    /** Detonation damage at the centre, falling to nothing at the edge. */
    public static final float JUDGMENT_BLAST_DAMAGE = 100;
    public static final double JUDGMENT_BLAST_KNOCKBACK = 4.0;
    /** Strikes in progress at once, server-wide; more fizzle. */
    public static final int MAX_ACTIVE_JUDGMENTS = 8;

    // Meteor Swarm (Astral Staff)

    public static final float METEOR_MANA = 75;
    /** 35 s. */
    public static final int METEOR_COOLDOWN_TICKS = 700;
    /** 1.5 s. */
    public static final int METEOR_CHARGE_TICKS = 30;
    public static final double METEOR_RANGE = 128;
    public static final double METEOR_MIN_DISTANCE = 24;
    /** Meteors land anywhere within this radius of the mark. */
    public static final double METEOR_AREA_RADIUS = 24;
    public static final int METEOR_COUNT = 40;
    /** One meteor every this many ticks: 40 over 4 s, then the Dread Star. */
    public static final int METEOR_INTERVAL_TICKS = 2;
    /** Height of the burning sigil the meteors fall from, above the mark. */
    public static final double METEOR_SKY_HEIGHT = 70;
    /** Blocks per tick. */
    public static final double METEOR_SPEED = 3.5;
    /** A meteor that hits nothing burns out after this long. */
    public static final int METEOR_LIFETIME_TICKS = 80;
    public static final double METEOR_BLAST_RADIUS = 6;
    /** At the centre of each impact, falling to nothing at its edge; it also sets things alight. */
    public static final float METEOR_DAMAGE = 30;
    public static final int METEOR_FIRE_SECONDS = 8;
    /** Vanilla explosion strength for terrain: a charged creeper's. TNT is 4. */
    public static final float METEOR_TERRAIN_STRENGTH = 6;
    /** The swarm ends with the Dread Star: one colossal, slower meteor straight onto the mark. */
    public static final double DREAD_STAR_SPEED = 2.2;
    public static final double DREAD_STAR_BLAST_RADIUS = 16;
    public static final float DREAD_STAR_DAMAGE = 80;
    public static final float DREAD_STAR_TERRAIN_STRENGTH = 12;
    /** How much larger the Dread Star looks than a meteor. */
    public static final float DREAD_STAR_SIZE = 4;
    public static final int MAX_ACTIVE_SWARMS = 8;

    // Singularity (Astral Staff)

    public static final float SINGULARITY_MANA = 70;
    /** 35 s. */
    public static final int SINGULARITY_COOLDOWN_TICKS = 700;
    public static final int SINGULARITY_CHARGE_TICKS = 30;
    public static final double SINGULARITY_RANGE = 96;
    public static final double SINGULARITY_MIN_DISTANCE = 24;
    /** How high above the mark the hole hangs. */
    public static final double SINGULARITY_LIFT = 6;
    /** 10 s, then it collapses. */
    public static final int SINGULARITY_DURATION_TICKS = 200;
    /** Creatures, items and projectiles this close are dragged in. */
    public static final double SINGULARITY_PULL_RADIUS = 32;
    /** Speed added towards the hole each tick at the edge of its pull, tripling at its heart. */
    public static final double SINGULARITY_PULL = 0.12;
    /** Anything this close to the heart is crushed every {@link #SINGULARITY_CRUSH_INTERVAL} ticks. */
    public static final double SINGULARITY_CRUSH_RADIUS = 4;
    public static final float SINGULARITY_CRUSH_DAMAGE = 10;
    public static final int SINGULARITY_CRUSH_INTERVAL = 10;
    /** The hole devours everything within this radius, nearest first. */
    public static final double SINGULARITY_CONSUME_RADIUS = 12;
    public static final int SINGULARITY_CONSUME_PER_TICK = 30;
    /** Of the blocks it eats each tick, this many are torn loose and fly into it, rather than simply vanishing. */
    public static final int SINGULARITY_DEBRIS_PER_TICK = 3;
    /** The collapse hurls everything this close outwards. */
    public static final double SINGULARITY_COLLAPSE_RADIUS = 24;
    public static final float SINGULARITY_COLLAPSE_DAMAGE = 60;
    public static final double SINGULARITY_COLLAPSE_KNOCKBACK = 4.0;
    /** Vanilla explosion strength of the collapse, for terrain. TNT is 4. */
    public static final float SINGULARITY_COLLAPSE_TERRAIN_STRENGTH = 8;
    public static final int MAX_ACTIVE_SINGULARITIES = 4;

    // Chrono Field (Astral Staff)

    public static final float CHRONO_MANA = 60;
    /** 40 s. */
    public static final int CHRONO_COOLDOWN_TICKS = 800;
    /** 1 s. */
    public static final int CHRONO_CHARGE_TICKS = 20;
    /** The dome's radius, around where the caster stood. */
    public static final double CHRONO_RADIUS = 32;
    /** 15 s. */
    public static final int CHRONO_DURATION_TICKS = 300;
    /** Damage dealt to frozen creatures is held back, then lands this much harder when time resumes. */
    public static final float CHRONO_DAMAGE_MULTIPLIER = 3;
    /** While inside their own field, the caster moves and strikes with these: Speed II, Strength III, Haste II. */
    public static final int CHRONO_SPEED_AMPLIFIER = 1;
    public static final int CHRONO_STRENGTH_AMPLIFIER = 2;
    public static final int CHRONO_HASTE_AMPLIFIER = 1;
    public static final int MAX_ACTIVE_CHRONO_FIELDS = 4;

    // Astral Gate (Astral Staff)

    public static final float GATE_MANA = 50;
    /** 15 s. */
    public static final int GATE_COOLDOWN_TICKS = 300;
    public static final int GATE_CHARGE_TICKS = 20;
    public static final double GATE_RANGE = 384;
    /** Nearer than this, Blink does the job. */
    public static final double GATE_MIN_DISTANCE = 8;
    /** Looking at least this far up (degrees) when released opens a gate home instead: to the caster's respawn point. */
    public static final float GATE_HOME_PITCH = 60;
    /** From the gates opening to the caster stepping through. */
    public static final int GATE_CROSS_TICKS = 10;
    /** How long the gates stay open, both ways, for anyone. */
    public static final int GATE_OPEN_TICKS = 400;
    /** Anything standing this close to a gate's centre is carried through. */
    public static final double GATE_RADIUS = 2;
    public static final int MAX_ACTIVE_GATES = 8;

    // Planetfall (Astral Staff): the ultimate

    /** A whole base pool. */
    public static final float PLANETFALL_MANA = 100;
    /** 2 minutes. */
    public static final int PLANETFALL_COOLDOWN_TICKS = 2400;
    /** 3 s: the longest channel of any spell. */
    public static final int PLANETFALL_CHARGE_TICKS = 60;
    public static final double PLANETFALL_RANGE = 160;
    /** Well outside the crater and the falling planet. */
    public static final double PLANETFALL_MIN_DISTANCE = 72;
    /** The rift opens this high above the mark, and {@link #PLANETFALL_RIFT_LEAD} blocks beyond it, away from the caster. */
    public static final double PLANETFALL_RIFT_HEIGHT = 150;
    public static final double PLANETFALL_RIFT_LEAD = 45;
    /** 3 s: the sky tears open and gravity fails beneath it. */
    public static final int PLANETFALL_RIFT_TICKS = 60;
    /** 4 s: the planet falls out of the rift, slowly at first. */
    public static final int PLANETFALL_FALL_TICKS = 80;
    public static final double PLANETFALL_PLANET_RADIUS = 20;
    /** At impact the planet's centre is this fraction of its radius above the mark: most of it is buried. */
    public static final double PLANETFALL_SINK = 0.35;
    /** Anything the planet itself touches on the way down. */
    public static final float PLANETFALL_CRUSH_DAMAGE = 1000;
    /** The impact: up to this much damage at the mark, falling to none at the edge of its radius. */
    public static final double PLANETFALL_IMPACT_RADIUS = 72;
    public static final float PLANETFALL_IMPACT_DAMAGE = 400;
    public static final double PLANETFALL_IMPACT_KNOCKBACK = 5.0;
    public static final double PLANETFALL_CRATER_RADIUS = 44;
    public static final double PLANETFALL_CRATER_DEPTH = 28;
    /** The air-pressure front reaches the edge in 1.4 seconds; entities are struck once as it passes. */
    public static final int PLANETFALL_PRESSURE_TICKS = 28;
    /** The slower earth rupture reaches the tips of the fracture field in 3.2 seconds. */
    public static final int PLANETFALL_RUPTURE_TICKS = 64;
    /** Nominal settling time; exceptionally dense terrain finishes under the same fixed work budget. */
    public static final int PLANETFALL_CARVE_TICKS = 96;
    /** Within this radius of the mark, gravity fails while the planet comes: creatures float and ground tears loose. */
    public static final double PLANETFALL_LIFT_RADIUS = 48;
    /** At most this many blocks torn loose to float, a few each tick. */
    public static final int PLANETFALL_LIFTED_BLOCKS = 160;
    public static final int PLANETFALL_LIFT_PER_TICK = 2;
    /** The rim thrown up around the crater: its width and nominal height before asymmetric collapse. */
    public static final double PLANETFALL_RIM_WIDTH = 10;
    public static final double PLANETFALL_RIM_HEIGHT = 8;
    /**
     * From this far through its fall, burning shards break off the planet's leading face, one every
     * {@link #PLANETFALL_SHED_INTERVAL_TICKS}, each streaking down to crater the ground somewhere in the lift radius.
     */
    public static final float PLANETFALL_SHED_FROM = 0.4f;
    public static final int PLANETFALL_SHED_INTERVAL_TICKS = 3;
    /** Shards land no nearer the mark than this, and no further than {@link #PLANETFALL_LIFT_RADIUS}. */
    public static final double PLANETFALL_SHARD_MIN_DISTANCE = 24;
    public static final double PLANETFALL_SHARD_SPEED = 5;
    public static final float PLANETFALL_SHARD_DAMAGE = 24;
    public static final double PLANETFALL_SHARD_BLAST_RADIUS = 6;
    /** A charged creeper's crater; TNT is 4. */
    public static final float PLANETFALL_SHARD_TERRAIN_STRENGTH = 5;
    /** The planet's core, left glowing at the bottom of the crater. */
    public static final double PLANETFALL_CORE_RADIUS = 4.5;
    public static final int MAX_ACTIVE_PLANETFALLS = 2;

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
