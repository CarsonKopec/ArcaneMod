package dev.arcane.progression;

import dev.arcane.api.SpellTags;
import dev.arcane.api.spell.Spell;
import dev.arcane.registry.ModSpells;

/** Built-in affinities; spells supplied by add-ons default to Arcane. */
public enum SpellSchool {
    ARCANE, STORM, FROST, ASTRAL;

    public static SpellSchool of(Spell spell) {
        if (spell == ModSpells.CHAIN_LIGHTNING) return STORM;
        if (spell == ModSpells.FROST_NOVA) return FROST;
        if (spell.is(SpellTags.ASTRAL_STAFF)) return ASTRAL;
        return ARCANE;
    }
}
