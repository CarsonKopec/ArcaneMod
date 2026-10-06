package dev.arcane.api;

import dev.arcane.api.effect.SpellEffect;
import dev.arcane.api.spell.Spell;
import dev.arcane.network.SpellEffects;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Everything other mods need besides the registries and events: players' mana and cooldowns, the held staff, casting
 * from code, and sending spell effects. Methods taking a {@link Player} work on either side; the client sees the
 * local player's state as last synced.
 */
public final class ArcaneSpellsApi {
    private ArcaneSpellsApi() {
    }

    /** Every spell, in wheel order: Arcane Spells' own first, then each other mod's together, in registration order. */
    public static List<Spell> spells() {
        return SpellCasting.spells();
    }

    /** The spells {@code staff} holds (those in its {@link SpellTags tag}), in wheel order; none if it isn't a staff. */
    public static List<Spell> spells(ItemStack staff) {
        return SpellCasting.spells(staff);
    }

    /** The staff in the player's main hand, else their offhand, else {@link ItemStack#EMPTY}. */
    public static ItemStack heldStaff(Player player) {
        return SpellCasting.heldStaff(player);
    }

    /** The spell {@code staff} casts. */
    public static Spell selectedSpell(ItemStack staff) {
        return SpellCasting.selected(staff);
    }

    public static float mana(Player player) {
        return CasterState.of(player).mana(player.level().getGameTime());
    }

    /** The size of {@code player}'s mana pool. To change it, modify {@link SpellAttributes#MAX_MANA}. */
    public static float maxMana(Player player) {
        return CasterState.of(player).maxMana();
    }

    /** Mana {@code player} regenerates per second. To change it, modify {@link SpellAttributes#MANA_REGEN}. */
    public static float manaRegen(Player player) {
        return CasterState.of(player).manaRegen();
    }

    /** Adds {@code amount} mana (negative drains it), kept between 0 and the maximum, and syncs it to the player. */
    public static void addMana(ServerPlayer player, float amount) {
        long now = player.level().getGameTime();
        CasterState.set(player, CasterState.of(player).withMana(amount, now));
    }

    /** Ticks until {@code player} can cast {@code spell} again; 0 if they can now. */
    public static int cooldownLeft(Player player, Spell spell) {
        return CasterState.of(player).cooldownLeft(spell, player.level().getGameTime());
    }

    /**
     * The cooldown, in ticks, that {@code spell} starts when {@code player} casts it, after
     * {@link dev.arcane.api.event.SpellEvents#MODIFY_COOLDOWN}.
     */
    public static int cooldown(Player player, Spell spell) {
        return SpellCasting.cooldown(player, spell);
    }

    /**
     * Sets how many ticks until {@code player} can cast {@code spell} again, and syncs it to the player: 0 makes it
     * ready now, and less than {@link #cooldownLeft} shortens a running cooldown, which the HUD shows as progress.
     */
    public static void setCooldown(ServerPlayer player, Spell spell, int ticks) {
        long now = player.level().getGameTime();
        CasterState.set(player, CasterState.of(player).withCooldown(spell, ticks, now));
    }

    /** What {@code spell} costs {@code player}, after {@link dev.arcane.api.event.SpellEvents#MODIFY_MANA_COST}. */
    public static float manaCost(Player player, Spell spell) {
        return SpellCasting.manaCost(player, spell);
    }

    /** Why {@code player} can't cast {@code spell} right now, or null if they can. */
    public static Component castBlocker(Player player, Spell spell) {
        return SpellCasting.blocker(player, spell);
    }

    /**
     * Casts {@code spell} as {@code caster} would from a staff, from a scroll or another mod's item: refused, returning
     * false, unless they could cast it now; on success spends its mana and starts its cooldown.
     *
     * @param power 0 to 1; only charged spells use it
     */
    public static boolean cast(ServerPlayer caster, Spell spell, float power) {
        return SpellCasting.cast(caster.level(), caster, spell, power);
    }

    /** Sends {@code effect} to every player near its position who has that chunk loaded. */
    public static void sendEffect(ServerLevel level, SpellEffect effect) {
        SpellEffects.send(level, effect);
    }
}
