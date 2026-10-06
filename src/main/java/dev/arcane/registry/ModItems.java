package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellAttributes;
import dev.arcane.api.SpellTags;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.item.ArcaneTrinketItem;
import dev.arcane.spell.SpellTuning;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

public final class ModItems {
    public static final ResourceKey<Item> ARCANE_STAFF_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("arcane_staff"));
    public static final Item ARCANE_STAFF = new ArcaneStaffItem(new Item.Properties().setId(ARCANE_STAFF_KEY).stacksTo(1)
            .component(ModComponents.SELECTED_SPELL, ModSpells.ARCANE_BOLT), SpellTags.ARCANE_STAFF, 0xAD55FF);

    /** The advanced spells' staff: an endgame upgrade of the Arcane Staff. Survives fire and lava, like netherite. */
    public static final ResourceKey<Item> ASTRAL_STAFF_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("astral_staff"));
    public static final Item ASTRAL_STAFF = new ArcaneStaffItem(new Item.Properties().setId(ASTRAL_STAFF_KEY).stacksTo(1)
            .rarity(Rarity.EPIC).fireResistant().component(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            .component(ModComponents.SELECTED_SPELL, ModSpells.CELESTIAL_JUDGMENT), SpellTags.ASTRAL_STAFF, 0xFFE3A0);

    // Trinkets, worn in Trinkets' slots: which slots each fits is in data/trinkets/tags/item.

    /** Worn on either hand: more max mana. */
    public static final ResourceKey<Item> ARCANE_RING_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("arcane_ring"));
    public static final Item ARCANE_RING = new ArcaneTrinketItem(new Item.Properties().setId(ARCANE_RING_KEY).stacksTo(1)
            .rarity(Rarity.UNCOMMON), SpellAttributes.MAX_MANA, SpellTuning.ARCANE_RING_MAX_MANA);

    /** Worn at the neck: faster mana regeneration. */
    public static final ResourceKey<Item> ASTRAL_AMULET_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("astral_amulet"));
    public static final Item ASTRAL_AMULET = new ArcaneTrinketItem(new Item.Properties().setId(ASTRAL_AMULET_KEY).stacksTo(1)
            .rarity(Rarity.RARE), SpellAttributes.MANA_REGEN, SpellTuning.ASTRAL_AMULET_MANA_REGEN);

    /** Worn at the belt: shorter cooldowns. */
    public static final ResourceKey<Item> CHRONO_CHARM_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("chrono_charm"));
    public static final Item CHRONO_CHARM = new ArcaneTrinketItem(new Item.Properties().setId(CHRONO_CHARM_KEY).stacksTo(1)
            .rarity(Rarity.UNCOMMON));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ARCANE_STAFF_KEY, ARCANE_STAFF);
        Registry.register(BuiltInRegistries.ITEM, ASTRAL_STAFF_KEY, ASTRAL_STAFF);
        Registry.register(BuiltInRegistries.ITEM, ARCANE_RING_KEY, ARCANE_RING);
        Registry.register(BuiltInRegistries.ITEM, ASTRAL_AMULET_KEY, ASTRAL_AMULET);
        Registry.register(BuiltInRegistries.ITEM, CHRONO_CHARM_KEY, CHRONO_CHARM);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(ARCANE_STAFF);
            entries.accept(ASTRAL_STAFF);
            entries.accept(ARCANE_RING);
            entries.accept(ASTRAL_AMULET);
            entries.accept(CHRONO_CHARM);
        });
        // The Chrono Charm shortens every new cooldown. Both sides: the wheel and HUD show cooldowns too.
        SpellEvents.MODIFY_COOLDOWN.register((caster, spell, ticks) -> ArcaneTrinketItem.worn(caster, CHRONO_CHARM)
                ? ticks * (1 - SpellTuning.CHRONO_CHARM_COOLDOWN_CUT) : ticks);
    }
}
