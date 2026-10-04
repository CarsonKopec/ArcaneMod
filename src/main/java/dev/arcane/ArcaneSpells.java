package dev.arcane;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModParticles;
import dev.arcane.spell.SpellBolts;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;

/** Common entrypoint: registers content, networking and the server-side bolt simulation. */
public final class ArcaneSpells implements ModInitializer {
    public static final String ID = "arcane_spells";

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override public void onInitialize() {
        ModItems.register();
        ModParticles.register();
        PayloadTypeRegistry.playS2C().register(SpellEffectPayload.TYPE, SpellEffectPayload.CODEC);
        SpellBolts.register();
    }
}
