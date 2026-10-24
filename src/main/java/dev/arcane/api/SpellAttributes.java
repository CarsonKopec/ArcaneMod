package dev.arcane.api;

import dev.arcane.spell.SpellTuning;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Every player's mana pool, regeneration and spell power are ordinary attributes, so anything that changes attributes changes them:
 * attribute modifiers on gear, potions and effects, other mods, and {@code /attribute}. For example, a ring adding
 * 50 max mana while worn:
 *
 * <pre>{@code
 * Identifier id = Identifier.fromNamespaceAndPath("mymod", "ring_mana");
 * ItemAttributeModifiers.builder().add(SpellAttributes.MAX_MANA,
 *         new AttributeModifier(id, 50, AttributeModifier.Operation.ADD_VALUE), EquipmentSlotGroup.ANY).build()
 * }</pre>
 *
 * <p>Changes take effect within a tick. Mana is never refilled by a change: raising the maximum leaves room to
 * regenerate into, and lowering it below the current mana trims the excess.
 */
public final class SpellAttributes {
    /** The size of the mana pool: 100 for a player with no modifiers. */
    public static final Holder<Attribute> MAX_MANA = register("max_mana", SpellTuning.BASE_MAX_MANA, 0, 10_000);
    /** Mana regenerated per second: 5 for a player with no modifiers, so an empty pool refills in 20 s. */
    public static final Holder<Attribute> MANA_REGEN = register("mana_regen", SpellTuning.BASE_MANA_REGEN, 0, 1_000);
    /**
     * What every spell's damage is multiplied by: 1 for a player with no modifiers, so +0.1 makes spells hit 10% harder.
     * It's read when a spell is cast, so a strike already on its way keeps the power it was cast with.
     */
    public static final Holder<Attribute> SPELL_POWER = register("spell_power", 1, 0, 1_024);

    private SpellAttributes() {
    }

    private static Holder<Attribute> register(String name, double base, double min, double max) {
        Identifier id = Identifier.fromNamespaceAndPath("arcane_spells", name);
        return Registry.registerForHolder(BuiltInRegistries.ATTRIBUTE, id,
                new RangedAttribute(id.toLanguageKey("attribute.name"), base, min, max).setSyncable(true));
    }
}
