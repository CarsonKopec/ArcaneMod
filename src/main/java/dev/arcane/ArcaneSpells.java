package dev.arcane;

import dev.arcane.network.SelectSpellPayload;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModComponents;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.ArcaneBolt;
import dev.arcane.spell.ArcaneWard;
import dev.arcane.spell.SpellCasting;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint: registers content, networking and the server-side spell simulation. */
public final class ArcaneSpells implements ModInitializer {
    public static final String ID = "arcane_spells";
    public static final Logger LOGGER = LoggerFactory.getLogger(ID);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(ID, path);
    }

    @Override public void onInitialize() {
        // Components first: the staff's default spell is one.
        ModComponents.register();
        ModAttachments.register();
        ModSounds.register();
        ModSpellEffects.register();
        ModSpells.register();
        ModItems.register();
        ModParticles.register();
        PayloadTypeRegistry.playS2C().register(SpellEffectPayload.TYPE, SpellEffectPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectSpellPayload.TYPE, SelectSpellPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SelectSpellPayload.TYPE,
                (payload, context) -> SpellCasting.select(context.player(), payload.spell()));
        ArcaneBolt.register();
        ArcaneWard.register();
    }
}
