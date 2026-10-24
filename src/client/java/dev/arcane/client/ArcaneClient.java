package dev.arcane.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.client.animation.StaffCastingAnimations;
import dev.arcane.client.render.StaffRenderers;
import dev.arcane.client.render.TrinketRenderers;
import dev.arcane.client.gui.ArcaneScreens;
import dev.arcane.client.fx.AstralFx;
import dev.arcane.client.fx.CorruptionFx;
import dev.arcane.client.fx.ScreenEffects;
import dev.arcane.client.fx.SpellLighting;
import dev.arcane.client.fx.SpellParticles;
import dev.arcane.client.gui.SpellHud;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.client.particle.ArcaneSpark;
import dev.arcane.client.particle.PlanetfallCloud;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.StaffCastPayload;
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
        registerSpark(ModParticles.GOLD, 0xFFD27A);
        ParticleFactoryRegistry.getInstance().register(ModParticles.PLANETFALL_DUST,
                sprites -> new PlanetfallCloud.Provider(sprites, PlanetfallCloud.Kind.DUST));
        ParticleFactoryRegistry.getInstance().register(ModParticles.PLANETFALL_SMOKE,
                sprites -> new PlanetfallCloud.Provider(sprites, PlanetfallCloud.Kind.SMOKE));
        ParticleFactoryRegistry.getInstance().register(ModParticles.PLANETFALL_FIRE,
                sprites -> new PlanetfallCloud.Provider(sprites, PlanetfallCloud.Kind.FIRE));

        spellWheel = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.arcane_spells.spell_wheel",
                InputConstants.Type.KEYSYM, InputConstants.KEY_R, KeyMapping.Category.register(ArcaneSpells.id("spells"))));
        SpellHud.register();
        ArcaneScreens.register();
        StaffCastingAnimations.register();
        StaffRenderers.register();
        TrinketRenderers.register();

        // Lights first, as they were before the particles.
        SpellLighting.register();
        SpellParticles.register();
        AstralFx.register();
        CorruptionFx.register();
        ScreenEffects.register();
        ClientPlayNetworking.registerGlobalReceiver(SpellEffectPayload.TYPE, (payload, context) -> {
            if (context.client().level != null) SpellEffectRenderers.render(payload.effect(), context.client().level);
        });
        ClientPlayNetworking.registerGlobalReceiver(StaffCastPayload.TYPE, (payload, context) ->
                StaffCastingAnimations.playRelease(payload.entityId(), payload.hand(), payload.profile()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level == null) {
                clearEffects();
                return;
            }
            openSpellWheel(client);
            if (client.isPaused()) return;
            SpellLighting.tick(client);
            SpellParticles.tick(client.level);
            AstralFx.tick(client);
            CorruptionFx.tick();
            ScreenEffects.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEffects());
    }

    /**
     * Opens the wheel on a fresh press, with a staff holding spells in hand and not mid-charge (a pick then would be
     * ignored).
     */
    private static void openSpellWheel(Minecraft client) {
        while (spellWheel.consumeClick()) {
            LocalPlayer player = client.player;
            if (player == null || client.screen != null || player.isUsingItem()) continue;
            ItemStack staff = SpellCasting.heldStaff(player);
            if (!SpellCasting.spells(staff).isEmpty()) client.setScreen(new SpellWheelScreen(spellWheel, staff));
        }
    }

    private static void registerSpark(SimpleParticleType type, int color) {
        ParticleFactoryRegistry.getInstance().register(type, sprites -> new ArcaneSpark.Provider(sprites, color));
    }

    private static void clearEffects() {
        SpellParticles.clear();
        SpellLighting.clear();
        AstralFx.clear();
        CorruptionFx.clear();
        ScreenEffects.clear();
    }
}
