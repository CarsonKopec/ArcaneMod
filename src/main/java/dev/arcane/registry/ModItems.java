package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.item.ArcaneStaffItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

public final class ModItems {
    public static final ResourceKey<Item> ARCANE_STAFF_KEY = ResourceKey.create(Registries.ITEM, ArcaneSpells.id("arcane_staff"));
    public static final Item ARCANE_STAFF = new ArcaneStaffItem(new Item.Properties().setId(ARCANE_STAFF_KEY).stacksTo(1)
            .component(ModComponents.SELECTED_SPELL, ModSpells.ARCANE_BOLT));

    private ModItems() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.ITEM, ARCANE_STAFF_KEY, ARCANE_STAFF);
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.COMBAT).register(entries -> entries.accept(ARCANE_STAFF));
    }
}
