package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.SpellAttributes;
import dev.arcane.spell.SpellTuning;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.InstantenousMobEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.jspecify.annotations.Nullable;

/** The mod's status effects, brewed into its potions ({@link ModPotions}). Icons are {@code textures/mob_effect/<name>.png}. */
public final class ModEffects {
    /** Instantly restores {@link SpellTuning#INSTANT_MANA} mana per level; only players have mana. */
    public static final Holder<MobEffect> INSTANT_MANA = register("instant_mana", new InstantMana());
    /** More mana per second while it lasts. */
    public static final Holder<MobEffect> ARCANE_FLOW = register("arcane_flow", new Effect(MobEffectCategory.BENEFICIAL, 0x9B5CFF)
            .addAttributeModifier(SpellAttributes.MANA_REGEN, ArcaneSpells.id("effect.arcane_flow"), SpellTuning.ARCANE_FLOW_MANA_REGEN,
                    AttributeModifier.Operation.ADD_VALUE));
    /** Corruption drains away fast, and no Astral spell backlashes: see {@link dev.arcane.progression.Corruption}. */
    public static final Holder<MobEffect> CLARITY = register("clarity", new Effect(MobEffectCategory.BENEFICIAL, 0xBDF3FF));

    private ModEffects() {
    }

    /** Loading this class registers the effects. */
    public static void register() {
    }

    private static Holder<MobEffect> register(String name, MobEffect effect) {
        return Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT, ArcaneSpells.id(name), effect);
    }

    /** An effect whose work is done by its attribute modifiers, or by code that checks for it. */
    private static final class Effect extends MobEffect {
        Effect(MobEffectCategory category, int color) {
            super(category, color);
        }
    }

    private static final class InstantMana extends InstantenousMobEffect {
        InstantMana() {
            super(MobEffectCategory.BENEFICIAL, 0x6E7BFF);
        }

        /** Applied by {@code /effect}, or by a potion effect lasting longer than an instant. */
        @Override public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
            restore(entity, amplifier, 1);
            return true;
        }

        /** Drunk, or splashed: a splash restores less the farther from its centre. */
        @Override public void applyInstantenousEffect(ServerLevel level, @Nullable Entity source, @Nullable Entity indirectSource,
                LivingEntity entity, int amplifier, double proximity) {
            restore(entity, amplifier, proximity);
        }

        private static void restore(LivingEntity entity, int amplifier, double proximity) {
            if (entity instanceof ServerPlayer player) {
                ArcaneSpellsApi.addMana(player, (float) (SpellTuning.INSTANT_MANA * (amplifier + 1) * proximity));
            }
        }
    }
}
