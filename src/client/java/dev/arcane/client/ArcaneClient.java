package dev.arcane.client;

import dev.arcane.client.fx.SpellLighting;
import dev.arcane.client.fx.SpellParticles;
import dev.arcane.client.particle.ArcaneSpark;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModParticles;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.core.particles.SimpleParticleType;

/** Client entrypoint: turns server effect messages into particles, Nebulon lights and rune effects. */
public final class ArcaneClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        registerSpark(ModParticles.VIOLET, 0xA34DFF);
        registerSpark(ModParticles.PINK, 0xF3B5FF);
        registerSpark(ModParticles.CYAN, 0x88F5FF);

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
            if (client.isPaused()) return;
            SpellLighting.tick(client);
            SpellParticles.tick();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clearEffects());
    }

    private static void registerSpark(SimpleParticleType type, int color) {
        ParticleFactoryRegistry.getInstance().register(type, sprites -> new ArcaneSpark.Provider(sprites, color));
    }

    private static void clearEffects() {
        SpellParticles.clear();
        SpellLighting.clear();
    }
}
