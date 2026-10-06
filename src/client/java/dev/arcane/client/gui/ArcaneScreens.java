package dev.arcane.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.client.config.ArcaneClientSettings;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;

/** Registers the book independently of the staff's hold-to-select wheel. */
public final class ArcaneScreens {
    private static KeyMapping spellBook;

    private ArcaneScreens() {
    }

    public static void register() {
        if (spellBook != null) return;
        ArcaneClientSettings.register();
        // Category is a value record; reuse the category ArcaneClient registers without registering it twice.
        spellBook = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcane_spells.spell_book",
                InputConstants.Type.KEYSYM, InputConstants.KEY_K, new KeyMapping.Category(ArcaneSpells.id("spells"))));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (spellBook.consumeClick()) {
                if (client.player != null && client.level != null && client.screen == null && !client.player.isUsingItem()) {
                    client.setScreen(new SpellBookScreen());
                }
            }
        });
    }

    static boolean matchesBookKey(KeyEvent event) {
        return spellBook != null && spellBook.matches(event);
    }
}
