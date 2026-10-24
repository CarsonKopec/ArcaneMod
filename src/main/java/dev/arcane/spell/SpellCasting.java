package dev.arcane.spell;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellAttributes;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.item.StaffCastingProfile;
import dev.arcane.api.SpellTags;
import dev.arcane.network.StaffCastPayload;
import dev.arcane.registry.ModComponents;
import dev.arcane.registry.ModSpells;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** The one way into a spell: checks mana and cooldown, casts, then charges for it. */
public final class SpellCasting {
    private SpellCasting() {
    }

    /** Every spell, in wheel order: Arcane Spells' own first, then each other mod's together, in registration order. */
    public static List<Spell> spells() {
        return SpellRegistries.SPELLS.stream()
                .sorted(Comparator.comparing((Spell spell) -> !spell.id().getNamespace().equals(ArcaneSpells.ID))
                        .thenComparing(spell -> spell.id().getNamespace()))
                .toList();
    }

    /** The spells {@code staff} holds, in wheel order; none if it isn't a staff. */
    public static List<Spell> spells(ItemStack staff) {
        if (!(staff.getItem() instanceof ArcaneStaffItem item)) return List.of();
        return spells().stream().filter(spell -> spell.is(item.spells())).toList();
    }

    /** Whether {@code staff} holds {@code spell}. */
    public static boolean holds(ItemStack staff, Spell spell) {
        return staff.getItem() instanceof ArcaneStaffItem item && spell.is(item.spells());
    }

    /**
     * The spell {@code staff} casts: the one picked on it, or if the staff doesn't hold that (a datapack changed its
     * tag), its first spell.
     */
    public static Spell selected(ItemStack staff) {
        Spell picked = staff.getOrDefault(ModComponents.SELECTED_SPELL, ModSpells.ARCANE_BOLT);
        if (holds(staff, picked)) return picked;
        List<Spell> held = spells(staff);
        return held.isEmpty() ? picked : held.getFirst();
    }

    /** The staff (or another mod's staff) in the player's main hand, else their offhand, else {@link ItemStack#EMPTY}. */
    public static ItemStack heldStaff(Player player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof ArcaneStaffItem) return stack;
        }
        return ItemStack.EMPTY;
    }

    /**
     * The staff {@code player} casts {@code spell} with: the one they're channelling, if it holds the spell, else the first
     * hand's, main hand first, that holds it; {@link ItemStack#EMPTY} if neither does (an API cast).
     */
    public static ItemStack castingStaff(Player player, Spell spell) {
        if (player.isUsingItem() && holds(player.getUseItem(), spell)) return player.getUseItem();
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (holds(stack, spell)) return stack;
        }
        return ItemStack.EMPTY;
    }

    /** What {@code spell}'s damage is multiplied by when {@code player} casts it now, after other mods' adjustments. */
    public static float spellPower(Player player, Spell spell) {
        float power = (float) player.getAttributeValue(SpellAttributes.SPELL_POWER);
        return Math.max(0, SpellEvents.MODIFY_SPELL_POWER.invoker().modify(player, spell, power));
    }

    /** The spell's cost after other mods' adjustments. Creative players cast for free; cooldowns still apply. */
    public static float manaCost(Player player, Spell spell) {
        if (player.getAbilities().instabuild) return 0;
        return Math.max(0, SpellEvents.MODIFY_MANA_COST.invoker().modify(player, spell, spell.manaCost()));
    }

    /** The cooldown {@code spell} starts when {@code player} casts it, in ticks, after other mods' adjustments. */
    public static int cooldown(Player player, Spell spell) {
        return Math.max(0, Math.round(SpellEvents.MODIFY_COOLDOWN.invoker().modify(player, spell, spell.cooldownTicks())));
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
        return cast(level, caster, spell, power, null);
    }

    /** Staff use supplies its actual hand; API casts may omit it. */
    public static boolean cast(ServerLevel level, ServerPlayer caster, Spell spell, float power, InteractionHand hand) {
        if (blocker(caster, spell) != null) return false;
        // Priced before casting, so the spell can't change what it costs.
        float cost = manaCost(caster, spell);
        int cooldown = cooldown(caster, spell);
        SpellContext context = new SpellContext(level, caster, spell.charged() ? power : 1, spellPower(caster, spell));
        if (!spell.cast(context)) return false;
        CasterState.set(caster, CasterState.of(caster).afterCast(spell, cost, cooldown, caster.level().getGameTime()));
        SpellEvents.AFTER_CAST.invoker().afterCast(spell, context, cost);
        animateCast(caster, spell, hand);
        return true;
    }

    private static void animateCast(ServerPlayer player, Spell spell, InteractionHand sourceHand) {
        // Prefer the hand actually channeling, then the main-hand-first selection used by the wheel.
        InteractionHand preferred = sourceHand != null ? sourceHand
                : player.isUsingItem() ? player.getUsedItemHand() : InteractionHand.MAIN_HAND;
        for (InteractionHand hand : new InteractionHand[]{preferred,
                preferred == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND}) {
            ItemStack stack = player.getItemInHand(hand);
            if (!holds(stack, spell)) continue;
            ArcaneStaffItem.triggerCastAnimation(player, stack);
            boolean astral = ((ArcaneStaffItem) stack.getItem()).spells().equals(SpellTags.ASTRAL_STAFF);
            StaffCastPayload payload = new StaffCastPayload(player.getId(), hand, StaffCastingProfile.of(spell, astral));
            ServerPlayNetworking.send(player, payload);
            for (ServerPlayer observer : PlayerLookup.tracking(player)) ServerPlayNetworking.send(observer, payload);
            return;
        }
    }

    /**
     * Sets the spell on the staff the player holds, if it holds that spell. Ignored mid-charge, so a release can't fire
     * a different spell.
     */
    public static void select(ServerPlayer player, Spell spell) {
        ItemStack staff = heldStaff(player);
        if (holds(staff, spell) && !player.isUsingItem()) staff.set(ModComponents.SELECTED_SPELL, spell);
    }
}
