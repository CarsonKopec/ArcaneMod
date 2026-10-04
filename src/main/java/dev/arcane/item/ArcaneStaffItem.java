package dev.arcane.item;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;

/**
 * Casts the staff's selected spell: charged spells are held and fire on release, the rest fire on click. All spell
 * logic runs on the server; the client only predicts whether a cast is allowed.
 *
 * <p>Each staff holds the spells in its tag (see {@link dev.arcane.api.SpellTags}). Other mods may use or extend it for
 * staffs of their own: any instance opens the spell wheel and shows the HUD.
 */
public class ArcaneStaffItem extends Item {
    private static final int USE_DURATION = 72000;
    private static final int FIZZLE_INTERVAL_TICKS = 10;
    /** Client only: when the last refused cast fizzled. */
    private static long lastFizzle;

    private final TagKey<Spell> spells;
    private final int glowColor;

    /**
     * @param spells    the spells this staff can cast
     * @param glowColor RGB of the light the staff gives off in its holder's hand
     */
    public ArcaneStaffItem(Properties properties, TagKey<Spell> spells, int glowColor) {
        super(properties);
        this.spells = spells;
        this.glowColor = glowColor;
    }

    public TagKey<Spell> spells() {
        return spells;
    }

    public int glowColor() {
        return glowColor;
    }

    @Override public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION;
    }

    @Override public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        Spell spell = SpellCasting.selected(player.getItemInHand(hand));
        Component blocker = SpellCasting.blocker(player, spell);
        if (blocker != null) {
            if (level.isClientSide()) fizzle(level, player);
            else player.displayClientMessage(blocker, true);
            return InteractionResult.FAIL;
        }
        if (spell.charged()) {
            player.startUsingItem(hand);
            if (level instanceof ServerLevel server && player instanceof ServerPlayer caster) {
                spell.onChargeStart(new SpellContext(server, caster, 0));
            }
            return InteractionResult.CONSUME;
        }
        if (level instanceof ServerLevel server && player instanceof ServerPlayer caster) {
            SpellCasting.cast(server, caster, spell, 1);
        }
        return InteractionResult.SUCCESS;
    }

    @Override public void onUseTick(Level level, LivingEntity user, ItemStack stack, int remaining) {
        int charged = getUseDuration(stack, user) - remaining;
        if (level instanceof ServerLevel server && user instanceof ServerPlayer caster) {
            Spell spell = SpellCasting.selected(stack);
            spell.onChargeTick(new SpellContext(server, caster, power(spell, charged)), charged);
        }
    }

    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        int charged = getUseDuration(stack, user) - remaining;
        if (charged < SpellTuning.MIN_CHARGE_TICKS || !(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
            return false;
        }
        Spell spell = SpellCasting.selected(stack);
        return SpellCasting.cast(server, player, spell, power(spell, charged));
    }

    private static float power(Spell spell, int chargedTicks) {
        return Math.min(chargedTicks / (float) spell.fullChargeTicks(), 1f);
    }

    /**
     * A refused cast fizzles for the caster alone: passing the player to playSound on the client plays it locally as
     * a prediction. Holding right-click retries every few ticks, so it sounds at most twice a second.
     */
    private static void fizzle(Level level, Player player) {
        long now = level.getGameTime();
        if (Math.abs(now - lastFizzle) < FIZZLE_INTERVAL_TICKS) return;
        lastFizzle = now;
        level.playSound(player, player.getX(), player.getY(), player.getZ(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.5f, 1.0f);
    }
}
