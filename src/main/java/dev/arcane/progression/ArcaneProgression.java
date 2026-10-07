package dev.arcane.progression;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.spell.SpellTuning;
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
        SpellEvents.AFTER_CAST.register((spell, context, spent) -> {
            PlayerProgressionComponent progression = get(context.caster());
            progression.recordCast(spell, spent);
            if (progression.rank(ArcaneTalent.ECHO) > 0 && spent > 0) {
                ArcaneSpellsApi.addMana(context.caster(), spent * SpellTuning.ECHO_REFUND);
            }
        });
        // Cost and cooldown listeners run on both sides: the HUD and wheel show both.
        SpellEvents.MODIFY_COOLDOWN.register((player, spell, ticks) -> {
            PlayerProgressionComponent progression = get(player);
            float cut = 0.05f * progression.rank(ArcaneTalent.FOCUS);
            if (progression.rank(ArcaneTalent.TEMPO) > 0 && SpellSchool.of(spell) == SpellSchool.ASTRAL) {
                cut += SpellTuning.TEMPO_ASTRAL_COOLDOWN_CUT;
            }
            return ticks * (1 - cut);
        });
        // Practice in a school makes its spells cheaper, and Wellspring makes every spell cheaper.
        SpellEvents.MODIFY_MANA_COST.register((player, spell, cost) -> {
            PlayerProgressionComponent progression = get(player);
            float cut = progression.affinityDiscount(SpellSchool.of(spell)) / 100f;
            if (progression.rank(ArcaneTalent.WELLSPRING) > 0) cut += SpellTuning.WELLSPRING_COST_CUT;
            return cost * (1 - cut);
        });
    }
}
