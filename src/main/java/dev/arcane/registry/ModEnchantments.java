package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * The staffs' enchantments. Each is defined in data ({@code data/arcane_spells/enchantment}): its rarity, levels and
 * which items take it (the {@code arcane_spells:enchantable/staff} item tag); what it does is here, and applies to the
 * spells its staff casts. They come from the enchanting table, books on an anvil, loot and librarians, as vanilla's do.
 */
public final class ModEnchantments {
    /** More spell power, per level. */
    public static final ResourceKey<Enchantment> POTENCY = key("potency");
    /** Spells cost less mana, per level. */
    public static final ResourceKey<Enchantment> THRIFT = key("thrift");
    /** Shorter new cooldowns, per level. */
    public static final ResourceKey<Enchantment> CELERITY = key("celerity");

    private ModEnchantments() {
    }

    /** The level of {@code enchantment} on {@code stack}, 0 if it hasn't got it. */
    public static int level(ItemStack stack, ResourceKey<Enchantment> enchantment) {
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : stack.getEnchantments().entrySet()) {
            if (entry.getKey().is(enchantment)) return entry.getIntValue();
        }
        return 0;
    }

    public static void register() {
        // Both sides: the HUD, wheel and grimoire show costs, cooldowns and power.
        SpellEvents.MODIFY_SPELL_POWER.register((caster, spell, power) ->
                power + SpellTuning.POTENCY_SPELL_POWER * level(SpellCasting.castingStaff(caster, spell), POTENCY));
        SpellEvents.MODIFY_MANA_COST.register((caster, spell, cost) ->
                cost * (1 - SpellTuning.THRIFT_COST_CUT * level(SpellCasting.castingStaff(caster, spell), THRIFT)));
        SpellEvents.MODIFY_COOLDOWN.register((caster, spell, ticks) ->
                ticks * (1 - SpellTuning.CELERITY_COOLDOWN_CUT * level(SpellCasting.castingStaff(caster, spell), CELERITY)));
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ArcaneSpells.id(name));
    }
}
