package dev.arcane.api.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * One cast of a spell, on the server.
 *
 * @param power how fully a charged spell was charged, 0 to 1; always 1 for spells that aren't charged
 */
public record SpellContext(ServerLevel level, ServerPlayer caster, float power) {
}
