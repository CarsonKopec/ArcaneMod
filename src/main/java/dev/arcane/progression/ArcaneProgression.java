package dev.arcane.progression;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.event.SpellEvents;
import net.minecraft.world.entity.player.Player;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;

/** Registration and shared access to each player's authoritative CCA component. */
public final class ArcaneProgression implements EntityComponentInitializer {
    public static final ComponentKey<PlayerProgressionComponent> PLAYER = ComponentRegistry.getOrCreate(
            ArcaneSpells.id("progression"), PlayerProgressionComponent.class);

    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(PLAYER, PlayerProgressionComponent::new);
    }

    public static PlayerProgressionComponent get(Player player) {
        return PLAYER.get(player);
    }

    public static void register() {
        SpellEvents.AFTER_CAST.register((spell, context, spent) -> get(context.caster()).recordCast(spell, spent));
        SpellEvents.MODIFY_COOLDOWN.register((player, spell, ticks) -> ticks * (1 - 0.05f * get(player).rank(ArcaneTalent.FOCUS)));
        // Practice in a school makes its spells cheaper. Both sides: the HUD shows what a spell costs.
        SpellEvents.MODIFY_MANA_COST.register((player, spell, cost) ->
                cost * (1 - get(player).affinityDiscount(SpellSchool.of(spell)) / 100f));
    }
}
