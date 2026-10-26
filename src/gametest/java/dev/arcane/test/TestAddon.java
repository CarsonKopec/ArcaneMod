package dev.arcane.test;

import dev.arcane.api.SpellRegistries;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.leyline.LeylineRegistries;
import dev.leyline.effect.Effect;
import dev.leyline.effect.EffectType;
import dev.leyline.effect.Effects;
import net.fabricmc.api.ModInitializer;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * A stand-in for another mod, written only against {@code dev.arcane.api}: one spell, its own effect type, and a
 * listener on each cast event. {@link SpellApiGameTest} checks that all of it works.
 */
public final class TestAddon implements ModInitializer {
    public static final EffectType PING_EFFECT = Registry.register(LeylineRegistries.EFFECT_TYPES, id("ping"), new EffectType());
    public static final Spell PING = Registry.register(SpellRegistries.SPELLS, id("ping"), new Ping());
    public static final float PING_MANA = 20;
    public static final int PING_COOLDOWN_TICKS = 40;

    /** While set, {@link SpellEvents#ALLOW_CAST} refuses Ping. */
    public static volatile boolean silenced;
    /** {@link SpellEvents#MODIFY_COOLDOWN} scales every spell's cooldown by this. */
    public static volatile float cooldownScale = 1;
    public static volatile Spell lastCast;
    public static volatile float lastManaSpent;
    public static volatile SpellContext lastContext;

    @Override public void onInitialize() {
        SpellEvents.ALLOW_CAST.register((caster, spell) -> silenced && spell == PING ? Component.literal("Silenced") : null);
        // Half price, to show the cost listeners see is the one charged.
        SpellEvents.MODIFY_MANA_COST.register((caster, spell, cost) -> spell == PING ? cost / 2 : cost);
        SpellEvents.MODIFY_COOLDOWN.register((caster, spell, ticks) -> ticks * cooldownScale);
        SpellEvents.AFTER_CAST.register((spell, context, manaSpent) -> {
            lastCast = spell;
            lastManaSpent = manaSpent;
            lastContext = context;
        });
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath("arcane_spells_test", path);
    }

    /** Sends one effect from the caster's eyes along their aim, and nothing else. */
    private static final class Ping extends Spell {
        Ping() {
            super(new Properties().manaCost(PING_MANA).cooldown(PING_COOLDOWN_TICKS).color(0xFF66FF99));
        }

        @Override public boolean cast(SpellContext context) {
            var caster = context.caster();
            Effects.send(context.level(),
                    new Effect(PING_EFFECT, caster.getEyePosition(), caster.getLookAngle(), context.power(), 0, caster.getId()));
            return true;
        }
    }
}
