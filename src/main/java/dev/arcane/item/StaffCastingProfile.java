package dev.arcane.item;

import dev.arcane.api.spell.Spell;
import dev.arcane.registry.ModSpells;

/** Which set of casting poses a staff cast uses: Planetfall has its own, the other Astral spells share one. */
public enum StaffCastingProfile {
    ARCANE, ASTRAL, PLANETFALL;

    /** {@code spell} may be null when only the staff is known. */
    public static StaffCastingProfile of(Spell spell, boolean astral) {
        if (spell == ModSpells.PLANETFALL) return PLANETFALL;
        return astral ? ASTRAL : ARCANE;
    }
}
