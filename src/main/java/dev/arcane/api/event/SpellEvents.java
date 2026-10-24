package dev.arcane.api.event;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** Hooks into casting, for any spell, from any mod. */
public final class SpellEvents {
    /**
     * Whether a player may cast a spell right now, checked before cooldown and mana. Runs on both sides: on the
     * client to predict (so a refused cast fizzles at once), on the server to decide. Base the answer on state both
     * sides know, or the client may start a charge the server then refuses.
     */
    public static final Event<AllowCast> ALLOW_CAST = EventFactory.createArrayBacked(AllowCast.class, listeners -> (caster, spell) -> {
        for (AllowCast listener : listeners) {
            Component reason = listener.check(caster, spell);
            if (reason != null) return reason;
        }
        return null;
    });

    /**
     * Adjusts what a spell costs a player. Runs on both sides, like {@link #ALLOW_CAST}, since the HUD shows whether a
     * spell is affordable. Listeners run in turn, each given the previous one's result. Creative players still pay
     * nothing.
     */
    public static final Event<ModifyManaCost> MODIFY_MANA_COST = EventFactory.createArrayBacked(ModifyManaCost.class,
            listeners -> (caster, spell, cost) -> {
                for (ModifyManaCost listener : listeners) cost = listener.modify(caster, spell, cost);
                return cost;
            });

    /**
     * Adjusts a spell's cooldown for a player, in ticks. Runs on both sides, like {@link #ALLOW_CAST}, since the wheel
     * shows it. Listeners run in turn, each given the previous one's result; the last is rounded to whole ticks, and 0
     * or less means no cooldown. It's applied when the spell is cast: a cooldown already running keeps its length. To
     * change one that's running, use {@link dev.arcane.api.ArcaneSpellsApi#setCooldown}.
     */
    public static final Event<ModifyCooldown> MODIFY_COOLDOWN = EventFactory.createArrayBacked(ModifyCooldown.class,
            listeners -> (caster, spell, ticks) -> {
                for (ModifyCooldown listener : listeners) ticks = listener.modify(caster, spell, ticks);
                return ticks;
            });

    /**
     * Adjusts what a spell's damage is multiplied by when a player casts it, starting from their
     * {@linkplain dev.arcane.api.SpellAttributes#SPELL_POWER spell power}. Runs on both sides, like {@link #ALLOW_CAST},
     * since the spell book shows it. Listeners run in turn, each given the previous one's result; below 0 counts as 0.
     * The result is fixed when the spell is cast, and spells read it as {@link SpellContext#spellPower()}.
     */
    public static final Event<ModifySpellPower> MODIFY_SPELL_POWER = EventFactory.createArrayBacked(ModifySpellPower.class,
            listeners -> (caster, spell, power) -> {
                for (ModifySpellPower listener : listeners) power = listener.modify(caster, spell, power);
                return power;
            });

    /** Server only: a spell was cast, and its mana and cooldown have been charged. */
    public static final Event<AfterCast> AFTER_CAST = EventFactory.createArrayBacked(AfterCast.class, listeners -> (spell, context, manaSpent) -> {
        for (AfterCast listener : listeners) listener.afterCast(spell, context, manaSpent);
    });

    private SpellEvents() {
    }

    @FunctionalInterface
    public interface AllowCast {
        /** Returns why {@code caster} can't cast {@code spell}, shown above their hotbar, or null to allow it. */
        Component check(Player caster, Spell spell);
    }

    @FunctionalInterface
    public interface ModifyManaCost {
        float modify(Player caster, Spell spell, float cost);
    }

    @FunctionalInterface
    public interface ModifyCooldown {
        float modify(Player caster, Spell spell, float ticks);
    }

    @FunctionalInterface
    public interface ModifySpellPower {
        float modify(Player caster, Spell spell, float power);
    }

    @FunctionalInterface
    public interface AfterCast {
        void afterCast(Spell spell, SpellContext context, float manaSpent);
    }
}
