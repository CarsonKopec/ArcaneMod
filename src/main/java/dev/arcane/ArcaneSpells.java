package dev.arcane;

import dev.arcane.network.SelectSpellPayload;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.LearnTalentPayload;
import dev.arcane.network.StaffCastPayload;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.registry.ModAttachments;
import dev.arcane.command.ArcaneCommand;
import dev.arcane.registry.ModComponents;
import dev.arcane.registry.ModEffects;
import dev.arcane.registry.ModEnchantments;
import dev.arcane.registry.ModGameRules;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModPotions;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.registry.ModSpells;
import dev.arcane.registry.ModTriggers;
import dev.arcane.spell.ArcaneBolt;
import dev.arcane.spell.ArcaneWard;
import dev.arcane.spell.AstralGate;
import dev.arcane.spell.Blink;
import dev.arcane.spell.CelestialJudgment;
import dev.arcane.spell.ChronoField;
import dev.arcane.spell.ManaLimits;
import dev.arcane.spell.MeteorSwarm;
import dev.arcane.spell.Planetfall;
import dev.arcane.spell.Singularity;
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
        ModGameRules.register();
        ModSounds.register();
        ModSpellEffects.register();
        ModSpells.register();
        ModItems.register();
        ModParticles.register();
        ModEffects.register();
        ModPotions.register();
        ModEnchantments.register();
        ModTriggers.register();
        ArcaneCommand.register();
        PayloadTypeRegistry.playS2C().register(SpellEffectPayload.TYPE, SpellEffectPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(StaffCastPayload.TYPE, StaffCastPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(SelectSpellPayload.TYPE, SelectSpellPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(LearnTalentPayload.TYPE, LearnTalentPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(SelectSpellPayload.TYPE,
                (payload, context) -> SpellCasting.select(context.player(), payload.spell()));
        ServerPlayNetworking.registerGlobalReceiver(LearnTalentPayload.TYPE,
                (payload, context) -> ArcaneProgression.get(context.player()).learnTalent(payload.talent()));
        ArcaneProgression.register();
        ArcaneBolt.register();
        ArcaneWard.register();
        Blink.register();
        CelestialJudgment.register();
        MeteorSwarm.register();
        Singularity.register();
        ChronoField.register();
        AstralGate.register();
        Planetfall.register();
        ManaLimits.register();
    }
}
