package dev.arcane.spell;

import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModComponents;
import dev.arcane.registry.ModItems;
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
        return staff.getOrDefault(ModComponents.SELECTED_SPELL, Spell.ARCANE_BOLT);
    }

    /** The staff in the player's main hand, else their offhand, else {@link ItemStack#EMPTY}. */
    public static ItemStack heldStaff(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.ARCANE_STAFF)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** Creative players cast for free; cooldowns still apply. */
    public static float manaCost(Player player, Spell spell) {
        return player.getAbilities().instabuild ? 0 : spell.manaCost();
    }

    /**
     * Why {@code player} can't cast {@code spell} right now, or null if they can. Reads only synced state, so the
     * client can predict the answer and skip the charge animation for a cast the server would refuse.
     */
    public static Component blocker(Player player, Spell spell) {
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
        boolean cast = switch (spell) {
            case ARCANE_BOLT -> SpellBolts.cast(level, caster, power);
            case CHAIN_LIGHTNING -> ChainLightning.cast(level, caster);
            case BLINK -> Blink.cast(level, caster);
            case ARCANE_WARD -> ArcaneWards.cast(level, caster);
            case FROST_NOVA -> FrostNova.cast(level, caster);
        };
        if (cast) {
            CasterState after = CasterState.of(caster).afterCast(spell, manaCost(caster, spell), level.getGameTime());
            caster.setAttached(ModAttachments.CASTER, after);
        }
        return cast;
    }

    /** Sets the spell on the staff the player holds. Ignored mid-charge, so a release can't fire a different spell. */
    public static void select(ServerPlayer player, Spell spell) {
        ItemStack staff = heldStaff(player);
        if (!staff.isEmpty() && !player.isUsingItem()) staff.set(ModComponents.SELECTED_SPELL, spell);
    }
}
