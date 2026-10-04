package dev.arcane.spell;

import dev.arcane.api.event.SpellEvents;
import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModComponents;
import dev.arcane.registry.ModSpells;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The one way into a spell: checks mana and cooldown, casts, then charges for it. */
public final class SpellCasting {
    private SpellCasting() {
    }

    public static Spell selected(ItemStack staff) {
        return staff.getOrDefault(ModComponents.SELECTED_SPELL, ModSpells.ARCANE_BOLT);
    }

    /** The staff (or another mod's staff) in the player's main hand, else their offhand, else {@link ItemStack#EMPTY}. */
    public static ItemStack heldStaff(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ArcaneStaffItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** The spell's cost after other mods' adjustments. Creative players cast for free; cooldowns still apply. */
    public static float manaCost(Player player, Spell spell) {
        if (player.getAbilities().instabuild) return 0;
        return Math.max(0, SpellEvents.MODIFY_MANA_COST.invoker().modify(player, spell, spell.manaCost()));
    }

    /**
     * Why {@code player} can't cast {@code spell} right now, or null if they can. Reads only synced state, so the
     * client can predict the answer and skip the charge animation for a cast the server would refuse.
     */
    public static Component blocker(Player player, Spell spell) {
        Component refused = SpellEvents.ALLOW_CAST.invoker().check(player, spell);
        if (refused != null) return refused;
        CasterState state = CasterState.of(player);
        long now = player.level().getGameTime();
        int cooldown = state.cooldownLeft(spell, now);
        if (cooldown > 0) {
            String seconds = String.format(Locale.ROOT, "%.1f", cooldown / 20f);
            return Component.translatable("message.arcane_spells.cooldown", spell.displayName(), seconds);
        }
        if (state.mana(now) < manaCost(player, spell)) {
            return Component.translatable("message.arcane_spells.no_mana", spell.displayName());
        }
        return null;
    }

    /** Casts {@code spell}; on success spends its mana and starts its cooldown. {@code power} only matters for charged spells. */
    public static boolean cast(ServerLevel level, ServerPlayer caster, Spell spell, float power) {
        if (blocker(caster, spell) != null) return false;
        // Priced before casting, so the spell can't change what it costs.
        float cost = manaCost(caster, spell);
        SpellContext context = new SpellContext(level, caster, spell.charged() ? power : 1);
        if (!spell.cast(context)) return false;
        caster.setAttached(ModAttachments.CASTER, CasterState.of(caster).afterCast(spell, cost, level.getGameTime()));
        SpellEvents.AFTER_CAST.invoker().afterCast(spell, context, cost);
        return true;
    }

    /** Sets the spell on the staff the player holds. Ignored mid-charge, so a release can't fire a different spell. */
    public static void select(ServerPlayer player, Spell spell) {
        ItemStack staff = heldStaff(player);
        if (!staff.isEmpty() && !player.isUsingItem()) staff.set(ModComponents.SELECTED_SPELL, spell);
    }
}
