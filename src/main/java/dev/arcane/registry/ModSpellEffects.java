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
     * Celestial Judgment is being channelled by entity {@code sourceId}, aimed at {@code position}: {@code power} is the
     * channel so far, 0–1, and {@code age} its ticks. {@code direction} is up if the mark is valid, zero if not.
     */
    public static final SpellEffectType JUDGMENT_CHANNEL = register("judgment_channel");
    /**
     * A judgment strike {@code sourceId} began at {@code position}: the sky darkens and the circles form. The sky circle
     * is at {@code position + direction}, and the beam falls in {@code age} ticks.
     */
    public static final SpellEffectType JUDGMENT_MARK = register("judgment_mark");
    /** Strike {@code sourceId}'s beam fell from {@code position + direction} to {@code position}, for {@code age} ticks. */
    public static final SpellEffectType JUDGMENT_BEAM = register("judgment_beam");
    /** Strike {@code sourceId} detonated at {@code position}. */
    public static final SpellEffectType JUDGMENT_BLAST = register("judgment_blast");

    private ModSpellEffects() {
    }

    /** Loading this class registers the effects. */
    public static void register() {
    }

    private static SpellEffectType register(String name) {
        return Registry.register(SpellRegistries.SPELL_EFFECTS, ArcaneSpells.id(name), new SpellEffectType());
    }
}
