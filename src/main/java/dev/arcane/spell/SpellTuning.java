package dev.arcane.spell;

/** Every gameplay number for the staff in one place, so balancing never means hunting through logic. */
public final class SpellTuning {
    // Mana

    /** Base value of the max mana attribute; gear, effects and other mods may change a player's. */
    public static final float BASE_MAX_MANA = 100;
    /** Base value of the mana regeneration attribute, per second: an empty pool refills in 20 s. */
    public static final float BASE_MANA_REGEN = 5;

    // Trinkets, worn in Trinkets' slots

    /** Max mana an Arcane Ring adds. Two can be worn, one on each hand. */
    public static final float ARCANE_RING_MAX_MANA = 20;
    /** Mana per second the Astral Amulet adds. */
    public static final float ASTRAL_AMULET_MANA_REGEN = 1;
    /** How much shorter the Chrono Charm makes each new cooldown, as a fraction. */
    public static final float CHRONO_CHARM_COOLDOWN_CUT = 0.15f;

    // Progression

    /** Affinity XP, one per point of mana spent on a school's spells, for each 1% off that school's mana costs. */
    public static final int AFFINITY_XP_PER_PERCENT = 500;
    /** The most affinity takes off a school's mana costs, in percent. */
    public static final int AFFINITY_MAX_PERCENT = 10;
    /** Wellspring, Reservoir's capstone: how much less every spell costs. */
    public static final float WELLSPRING_COST_CUT = 0.05f;
    /** Echo, Recovery's capstone: the share of every spell's mana that comes back. */
    public static final float ECHO_REFUND = 0.1f;
    /** Tempo, Focus's capstone: how much shorter Astral spells' new cooldowns are. */
    public static final float TEMPO_ASTRAL_COOLDOWN_CUT = 0.1f;

    // Corruption: the Astral Staff's price, and its power

    /** The most corruption a caster can carry. */
    public static final int CORRUPTION_MAX = 100;
    /** Corruption each point of mana spent on an Astral spell builds (at least 1 a cast): Celestial Judgment builds 23. */
    public static final float CORRUPTION_PER_MANA = 0.25f;
    /** Spell power each point of corruption adds to Astral spells: +50% at the most. */
    public static final float CORRUPTION_POWER_PER_POINT = 0.005f;
    /** From this much corruption on, an Astral cast can backlash. */
    public static final int BACKLASH_THRESHOLD = 50;
    /** The chance of a backlash for each point of corruption over the threshold: 50% at the most. */
    public static final float BACKLASH_CHANCE_PER_POINT = 0.01f;
    /** A backlash hurts its caster this much, plus {@link #BACKLASH_DAMAGE_PER_POINT} for each point of corruption. */
    public static final float BACKLASH_BASE_DAMAGE = 2;
    /** 7 damage at the threshold, 12 at the most. */
    public static final float BACKLASH_DAMAGE_PER_POINT = 0.1f;
    /** How long a backlash darkens its caster's sight. */
    public static final int BACKLASH_DARKNESS_TICKS = 80;
    /** Corruption a backlash burns off. */
    public static final int BACKLASH_VENT = 30;
    /** Corruption fades by a point every this many ticks (2 s) while the caster isn't channelling or using an item. */
    public static final int CORRUPTION_DECAY_TICKS = 40;
    /** Under Clarity it fades by a point every this many ticks, whatever the caster is doing, and nothing backlashes. */
    public static final int CLARITY_DECAY_TICKS = 4;

    // Potions and their effects

    /** Instant Mana restores this much mana, doubled at level II. */
    public static final float INSTANT_MANA = 40;
    /** Arcane Flow's extra mana per second, per level. */
    public static final float ARCANE_FLOW_MANA_REGEN = 2.5f;
    /** Potion durations in ticks: 3 and 8 minutes for Arcane Flow, 90 s and 4 minutes for Clarity, as vanilla's. */
    public static final int ARCANE_FLOW_TICKS = 3600;
    public static final int LONG_ARCANE_FLOW_TICKS = 9600;
    public static final int STRONG_ARCANE_FLOW_TICKS = 1800;
    public static final int CLARITY_TICKS = 1800;
    public static final int LONG_CLARITY_TICKS = 4800;

    // Staff enchantments

    /** Potency: spell power each level adds to the spells its staff casts. */
    public static final float POTENCY_SPELL_POWER = 0.1f;
    /** Thrift: how much less mana each level makes its staff's spells cost. */
    public static final float THRIFT_COST_CUT = 0.08f;
    /** Celerity: how much shorter each level makes its staff's new cooldowns. */
    public static final float CELERITY_COOLDOWN_CUT = 0.08f;

    // Spell reactions

    /** Arcane Bolt and Chain Lightning hit a frozen target this much harder, shattering the ice. */
    public static final float SHATTER_MULTIPLIER = 1.5f;
    /** Chain Lightning hits a wet target this much harder, and leaps from it this much farther. */
    public static final float CONDUCTION_MULTIPLIER = 1.5f;

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
    /** Shardburst: a fully charged bolt bursts into this many shards, each seeking a different creature near the blast. */
    public static final int BOLT_SHARDS = 4;
    /** How far from the blast a shard seeks. */
    public static final double BOLT_SHARD_RANGE = 8;
    public static final float BOLT_SHARD_DAMAGE = 4;

    // Chain Lightning

    public static final float CHAIN_MANA = 25;
    public static final int CHAIN_COOLDOWN_TICKS = 50;
    /** How far the first strike reaches. */
    public static final double CHAIN_RANGE = 24;
    /** Targets' hitboxes count as this much bigger for the first strike, so small or moving mobs are hittable. */
    public static final double CHAIN_AIM_ASSIST = 0.5;
    /** The farthest the lightning leaps from one target to the next. */
    public static final double CHAIN_JUMP_RANGE = 7;
    public static final int CHAIN_MAX_TARGETS = 5;
    public static final float CHAIN_DAMAGE = 10;
    /** Each leap deals this fraction of the previous one's damage. */
    public static final float CHAIN_DAMAGE_FALLOFF = 0.8f;
    /** Overload: each leap left over when the chain runs out of targets deals this much around its last target. */
    public static final float CHAIN_OVERLOAD_DAMAGE = 3;
    public static final double CHAIN_OVERLOAD_RADIUS = 3;

    // Blink

    public static final float BLINK_MANA = 20;
    public static final int BLINK_COOLDOWN_TICKS = 60;
    public static final double BLINK_RANGE = 16;
    /** Destinations closer than this fail instead of wasting the cast on a shuffle. */
    public static final double BLINK_MIN_DISTANCE = 2;
    /** Afterimage: this long after a blink, the caster's afterimage bursts where they stood. */
    public static final int BLINK_AFTERIMAGE_TICKS = 20;
    public static final double BLINK_AFTERIMAGE_RADIUS = 3;
    public static final float BLINK_AFTERIMAGE_DAMAGE = 6;
    public static final double BLINK_AFTERIMAGE_KNOCKBACK = 0.8;

    // Arcane Ward

    public static final float WARD_MANA = 40;
    /** Counted from the cast, so it covers the 8 s the ward is up. */
    public static final int WARD_COOLDOWN_TICKS = 300;
    public static final int WARD_DURATION_TICKS = 160;
    /** Damage the ward absorbs before it shatters. The hit that breaks it is still fully absorbed. */
    public static final float WARD_STRENGTH = 20;
    /** Strength spent each time the ward turns a projectile back. */
    public static final float WARD_DEFLECT_COST = 2;
    /** Radius of the rune shell, from the centre of the caster's body. */
    public static final double WARD_RADIUS = 2.5;
    /** Rebound: a ward that is broken bursts outwards, hurting and hurling what's within this reach of its owner. */
    public static final double WARD_REBOUND_RADIUS = 4;
    public static final float WARD_REBOUND_DAMAGE = 6;
    public static final double WARD_REBOUND_KNOCKBACK = 1.4;

    // Frost Nova

    public static final float NOVA_MANA = 35;
    public static final int NOVA_COOLDOWN_TICKS = 160;
    public static final double NOVA_RADIUS = 6;
    public static final float NOVA_DAMAGE = 6;
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
    /** Smite: undead take this many times the beam's and the blast's damage. */
    public static final float JUDGMENT_SMITE_MULTIPLIER = 1.5f;
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
    public static final double METEOR_BLAST_RADIUS = 10;
    /** At the centre of each impact, falling to nothing at its edge; it also sets things alight. */
    public static final float METEOR_DAMAGE = 30;
    public static final int METEOR_FIRE_SECONDS = 8;
    /** Seeking: one meteor in this many aims at a creature in the area, if there is one. */
    public static final int METEOR_SEEKING_EVERY = 2;
    /** Each impact blasts out a crater this wide (from its centre) and this deep, whatever the ground is made of. */
    public static final double METEOR_CRATER_RADIUS = 5;
    public static final double METEOR_CRATER_DEPTH = 3.5;
    /** Vanilla explosion strength for terrain beyond the crater, where weaker blocks break further: twice TNT's. */
    public static final float METEOR_TERRAIN_STRENGTH = 8;
    /** The swarm ends with the Dread Star: one colossal, slower meteor straight onto the mark. */
    public static final double DREAD_STAR_SPEED = 2.2;
    public static final double DREAD_STAR_BLAST_RADIUS = 32;
    public static final float DREAD_STAR_DAMAGE = 80;
    /** The Dread Star's crater, its floor and walls left seared: carved over {@link #DREAD_STAR_CARVE_TICKS} ticks. */
    public static final double DREAD_STAR_CRATER_RADIUS = 16;
    public static final double DREAD_STAR_CRATER_DEPTH = 9;
    public static final int DREAD_STAR_CARVE_TICKS = 12;
    public static final float DREAD_STAR_TERRAIN_STRENGTH = 16;
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
    /**
     * As it eats, it grows: its size climbs towards this many times its first, its heart with it, and so does its
     * collapse.
     */
    public static final float SINGULARITY_MAX_GROWTH = 3;
    /**
     * Mass that takes it most of the way there (63%): each block it eats counts 1, each item or experience orb it takes
     * in 1, each creature it crushes to death that creature's health. Solid ground under it is some 1100 blocks.
     */
    public static final float SINGULARITY_GROWTH_MASS = 800;
    /**
     * Anything this close to the heart is crushed every {@link #SINGULARITY_CRUSH_INTERVAL} ticks: at its first size, and
     * as many times further as it has grown.
     */
    public static final double SINGULARITY_CRUSH_RADIUS = 4;
    public static final float SINGULARITY_CRUSH_DAMAGE = 10;
    public static final int SINGULARITY_CRUSH_INTERVAL = 10;
    /** The hole devours everything within this radius, nearest first. */
    public static final double SINGULARITY_CONSUME_RADIUS = 12;
    public static final int SINGULARITY_CONSUME_PER_TICK = 30;
    /** Of the blocks it eats each tick, this many are torn loose and fly into it, rather than simply vanishing. */
    public static final int SINGULARITY_DEBRIS_PER_TICK = 3;
    /**
     * The collapse hurls everything this close outwards: at its first size, and as many times further as the hole has
     * grown, up to 72 blocks.
     */
    public static final double SINGULARITY_COLLAPSE_RADIUS = 24;
    /**
     * The collapse's damage, knockback, terrain blast and crater at the hole's first size. Grown to g times that, each is
     * (1 + g) / 2 times as much: twice, at its largest.
     */
    public static final float SINGULARITY_COLLAPSE_DAMAGE = 60;
    public static final double SINGULARITY_COLLAPSE_KNOCKBACK = 4.0;
    /** Vanilla explosion strength, for terrain beyond the crater. TNT is 4. */
    public static final float SINGULARITY_COLLAPSE_TERRAIN_STRENGTH = 8;
    /** The crater the collapse leaves in the ground below, carved over {@link #SINGULARITY_CARVE_TICKS} ticks. */
    public static final double SINGULARITY_CRATER_RADIUS = 12;
    public static final double SINGULARITY_CRATER_DEPTH = 7;
    public static final int SINGULARITY_CARVE_TICKS = 20;
    /** Items it held are thrown out of reach for this many ticks, so they scatter before anyone grabs them. */
    public static final int SINGULARITY_RELEASE_PICKUP_DELAY = 20;
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
    /** Astral Wake: whatever crosses a gate comes out with Speed II for this long. */
    public static final int GATE_WAKE_TICKS = 100;
    public static final int GATE_WAKE_AMPLIFIER = 1;
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
    /** Well outside the crater, the fissures running out from it and the falling planet. */
    public static final double PLANETFALL_MIN_DISTANCE = 100;
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
    /**
     * The impact: up to this much damage at the mark, falling to none at the edge of its radius. The client draws the
     * fireball, mushroom cloud and impact light to scale with this radius.
     */
    public static final double PLANETFALL_IMPACT_RADIUS = 100;
    public static final float PLANETFALL_IMPACT_DAMAGE = 400;
    public static final double PLANETFALL_IMPACT_KNOCKBACK = 6.0;
    public static final double PLANETFALL_CRATER_RADIUS = 62;
    public static final double PLANETFALL_CRATER_DEPTH = 39;
    /** The air-pressure front reaches the edge in about 2 seconds; entities are struck once as it passes. */
    public static final int PLANETFALL_PRESSURE_TICKS = 39;
    /** The slower earth rupture reaches the tips of the fracture field in 4.5 seconds. */
    public static final int PLANETFALL_RUPTURE_TICKS = 90;
    /** Nominal settling time; exceptionally dense terrain finishes under the same fixed work budget. */
    public static final int PLANETFALL_CARVE_TICKS = 160;
    /** Within this radius of the mark, gravity fails while the planet comes: creatures float and ground tears loose. */
    public static final double PLANETFALL_LIFT_RADIUS = 48;
    /** At most this many blocks torn loose to float, a few each tick. */
    public static final int PLANETFALL_LIFTED_BLOCKS = 160;
    public static final int PLANETFALL_LIFT_PER_TICK = 2;
    /** The rim thrown up around the crater: its width and nominal height before asymmetric collapse. */
    public static final double PLANETFALL_RIM_WIDTH = 14;
    public static final double PLANETFALL_RIM_HEIGHT = 11;
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
    /** Riches: of the core's outer blocks, these fractions are gold ore, diamond ore and ancient debris. */
    public static final double PLANETFALL_CORE_GOLD = 0.08;
    public static final double PLANETFALL_CORE_DIAMOND = 0.04;
    public static final double PLANETFALL_CORE_DEBRIS = 0.015;
    public static final int MAX_ACTIVE_PLANETFALLS = 2;



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
