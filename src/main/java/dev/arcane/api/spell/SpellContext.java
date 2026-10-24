package dev.arcane.api.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One cast of a spell, on the server.
 *
 * @param power      how fully a charged spell was charged, 0 to 1; always 1 for spells that aren't charged
 * @param spellPower what the spell's damage is multiplied by: the caster's {@linkplain dev.arcane.api.SpellAttributes#SPELL_POWER
 *                   spell power} for this spell when it was cast, after
 *                   {@link dev.arcane.api.event.SpellEvents#MODIFY_SPELL_POWER}. 1 is the spell's own damage.
 */
public record SpellContext(ServerLevel level, ServerPlayer caster, float power, float spellPower) {
    /** A cast at the spell's own damage. */
    public SpellContext(ServerLevel level, ServerPlayer caster, float power) {
        this(level, caster, power, 1);
    }
}
