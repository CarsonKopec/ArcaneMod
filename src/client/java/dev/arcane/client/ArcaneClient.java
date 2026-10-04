package dev.arcane.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.client.fx.SpellLighting;
import dev.arcane.client.fx.SpellParticles;
import dev.arcane.client.gui.SpellHud;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.client.particle.ArcaneSpark;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModParticles;
import dev.arcane.spell.SpellCasting;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.item.ItemStack;

/** Client entrypoint: spell wheel and HUD, and turning server effect messages into particles, lights and runes. */
public final class ArcaneClient implements ClientModInitializer {
    private static KeyMapping spellWheel;

    @Override public void onInitializeClient() {
        registerSpark(ModParticles.VIOLET, 0xA34DFF);
        registerSpark(ModParticles.PINK, 0xF3B5FF);
        registerSpark(ModParticles.CYAN, 0x88F5FF);

        spellWheel = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcane_spells.spell_wheel",
                InputConstants.Type.KEYSYM, InputConstants.KEY_R, KeyMapping.Category.register(ArcaneSpells.id("spells"))));
        SpellHud.register();

        ClientPlayNetworking.registerGlobalReceiver(SpellEffectPayload.TYPE, (effect, context) -> {
            if (context.client().level == null) return;
            SpellLighting.play(effect);
            SpellParticles.play(effect);
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                clearEffects();
                return;
            }
            openSpellWheel(client);
            if (client.isPaused()) return;
            SpellLighting.tick(client);
            SpellParticles.tick(client.level);
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEffects());
    }

    /** Opens the wheel on a fresh press, with the staff in hand and not mid-charge (a pick then would be ignored). */
    private static void openSpellWheel(Minecraft client) {
        while (spellWheel.consumeClick()) {
            LocalPlayer player = client.player;
            if (player == null || client.screen != null || player.isUsingItem()) continue;
            ItemStack staff = SpellCasting.heldStaff(player);
            if (!staff.isEmpty()) client.setScreen(new SpellWheelScreen(spellWheel, SpellCasting.selected(staff)));
        }
    }

    private static void registerSpark(SimpleParticleType type, int color) {
        ParticleFactoryRegistry.getInstance().register(type, sprites -> new ArcaneSpark.Provider(sprites, color));
    }

    private static void clearEffects() {
        SpellParticles.clear();
        SpellLighting.clear();
    }
}
