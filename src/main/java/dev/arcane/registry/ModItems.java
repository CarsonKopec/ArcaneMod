package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellTags;
import dev.arcane.item.ArcaneStaffItem;
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

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ARCANE_STAFF_KEY, ARCANE_STAFF);
        Registry.register(BuiltInRegistries.ITEM, ASTRAL_STAFF_KEY, ASTRAL_STAFF);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> {
            entries.accept(ARCANE_STAFF);
            entries.accept(ASTRAL_STAFF);
        });
    }
}
