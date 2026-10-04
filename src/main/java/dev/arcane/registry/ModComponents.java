package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.spell.Spell;
import net.fabricmc.fabric.api.item.v1.ComponentTooltipAppenderRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class ModComponents {
    /** The spell a staff casts. Changing it skips the re-equip animation, so picking a spell doesn't bob the staff. */
    public static final DataComponentType<Spell> SELECTED_SPELL = DataComponentType.<Spell>builder()
            .persistent(Spell.CODEC).networkSynchronized(Spell.STREAM_CODEC).ignoreSwapAnimation().build();

    private ModComponents() {
    }

    public static void register() {
        Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, ArcaneSpells.id("selected_spell"), SELECTED_SPELL);
        // The spell writes its own tooltip lines, directly under the item name.
        ComponentTooltipAppenderRegistry.addFirst(SELECTED_SPELL);
    }
}
