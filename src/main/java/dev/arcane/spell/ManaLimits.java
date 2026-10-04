package dev.arcane.spell;

import dev.arcane.api.SpellAttributes;
import dev.arcane.registry.ModAttachments;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/**
 * Keeps each player's stored mana limits in step with their max mana and regeneration attributes. Checked every
 * tick, which costs two attribute lookups per player; the state only changes, and is only re-synced, when an
 * attribute does.
 */
public final class ManaLimits {
    private ManaLimits() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> server.getPlayerList().getPlayers().forEach(ManaLimits::update));
    }

    static void update(ServerPlayer player) {
        CasterState state = CasterState.of(player);
        float maxMana = (float) player.getAttributeValue(SpellAttributes.MAX_MANA);
        float manaRegen = (float) player.getAttributeValue(SpellAttributes.MANA_REGEN);
        if (maxMana != state.maxMana() || manaRegen != state.manaRegen()) {
            player.setAttached(ModAttachments.CASTER, state.withLimits(maxMana, manaRegen, player.level().getGameTime()));
        }
    }
}
