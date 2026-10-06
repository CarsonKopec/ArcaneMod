package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.effect.SpellEffectType;
import net.minecraft.core.Registry;

/** The built-in spells' visual events, and what each one's fields mean. */
public final class ModSpellEffects {
    /** The staff is being charged; {@code sourceId} is the caster's entity id. */
    public static final SpellEffectType CHARGE = register("charge");
    /** A bolt moved; {@code direction} spans this tick's travel. */
    public static final SpellEffectType BOLT = register("bolt");
    /** A bolt detonated; {@code direction} is its flight direction. */
    public static final SpellEffectType IMPACT = register("impact");
    /** One leap of chain lightning, from {@code position} along {@code direction}; {@code age} counts the leaps. */
    public static final SpellEffectType CHAIN = register("chain");
    /** The caster's feet moved from {@code position} by {@code direction}. */
    public static final SpellEffectType BLINK = register("blink");
    /** A ward went up around entity {@code sourceId}, at {@code position}, for {@code age} ticks. */
    public static final SpellEffectType WARD = register("ward");
    /** A ward absorbed a hit at {@code position}, facing {@code direction}; {@code power} is the strength left, 0–1. */
    public static final SpellEffectType WARD_HIT = register("ward_hit");
    /** A ward ended: {@code power} is 1 if it shattered, 0 if it faded. */
    public static final SpellEffectType WARD_END = register("ward_end");
    /** A frost nova burst from the caster's feet at {@code position}. */
    public static final SpellEffectType NOVA = register("nova");
    /**
     * An Astral Staff spell is being channelled by entity {@code sourceId}, aimed at {@code position}: {@code power} is
     * the channel so far, 0–1, and {@code age} its ticks. {@code direction.y} is the radius of the mark, negative if
     * the spell can't land there.
     */
    public static final SpellEffectType ASTRAL_CHANNEL = register("astral_channel");
    /**
     * A judgment strike {@code sourceId} began at {@code position}: the sky darkens and the circles form. The sky circle
     * is at {@code position + direction}, and the beam falls in {@code age} ticks.
     */
    public static final SpellEffectType JUDGMENT_MARK = register("judgment_mark");
    /** Strike {@code sourceId}'s beam fell from {@code position + direction} to {@code position}, for {@code age} ticks. */
    public static final SpellEffectType JUDGMENT_BEAM = register("judgment_beam");
    /** Strike {@code sourceId} detonated at {@code position}. */
    public static final SpellEffectType JUDGMENT_BLAST = register("judgment_blast");
    /**
     * Meteor swarm {@code sourceId} began over {@code position}: its sigil burns at {@code position + direction}, its
     * meteors land within {@code power} blocks of the mark, and it lasts {@code age} ticks.
     */
    public static final SpellEffectType METEOR_SWARM = register("meteor_swarm");
    /** Meteor {@code sourceId} fell from {@code position}, moving {@code direction} each tick, for at most {@code age} ticks. */
    public static final SpellEffectType METEOR = register("meteor");
    /** Meteor {@code sourceId} struck at {@code position}. */
    public static final SpellEffectType METEOR_IMPACT = register("meteor_impact");
    /** Singularity {@code sourceId} opened at {@code position}, pulling within {@code power} blocks, for {@code age} ticks. */
    public static final SpellEffectType SINGULARITY = register("singularity");
    /** Singularity {@code sourceId} collapsed at {@code position}. */
    public static final SpellEffectType SINGULARITY_COLLAPSE = register("singularity_collapse");
    /** Chrono field {@code sourceId} stopped time within {@code power} blocks of {@code position}, for {@code age} ticks. */
    public static final SpellEffectType CHRONO_FIELD = register("chrono_field");
    /** Chrono field {@code sourceId} at {@code position} ended, and time resumed. */
    public static final SpellEffectType CHRONO_END = register("chrono_end");
    /** Gates {@code sourceId} opened at {@code position} and {@code position + direction}, for {@code age} ticks. */
    public static final SpellEffectType ASTRAL_GATE = register("astral_gate");
    /**
     * Something crossed gate {@code sourceId}, from {@code position} to {@code position + direction}: {@code power} is
     * 1 for the caster, less for anything following.
     */
    public static final SpellEffectType GATE_CROSSING = register("gate_crossing");
    /**
     * Planetfall {@code sourceId} began over {@code position}: the sky tears open at {@code position + direction}, and
     * the planet starts to fall in {@code age} ticks. {@code power} seeds the planet's looks and the crater's shape, the
     * same for everyone.
     */
    public static final SpellEffectType PLANETFALL_RIFT = register("planetfall_rift");
    /**
     * Planetfall {@code sourceId}'s planet began falling out of the rift, landing in {@code age} ticks. The rest is as
     * for {@link #PLANETFALL_RIFT}, so a client that missed the rift can still draw the same planet.
     */
    public static final SpellEffectType PLANETFALL_FALL = register("planetfall_fall");
    /** Planetfall {@code sourceId}'s planet struck {@code position}; the rest is as for {@link #PLANETFALL_RIFT}. */
    public static final SpellEffectType PLANETFALL_IMPACT = register("planetfall_impact");
    /**
     * Shard {@code sourceId} broke off a falling planet at {@code position}, and strikes {@code position + direction}
     * in {@code age} ticks. {@code power} is its size, about 1.
     */
    public static final SpellEffectType PLANETFALL_SHARD = register("planetfall_shard");

    private ModSpellEffects() {
    }

    /** Loading this class registers the effects. */
    public static void register() {
    }

    private static SpellEffectType register(String name) {
        return Registry.register(SpellRegistries.SPELL_EFFECTS, ArcaneSpells.id(name), new SpellEffectType());
    }
}
