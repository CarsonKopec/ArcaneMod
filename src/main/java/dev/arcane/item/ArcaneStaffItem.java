package dev.arcane.item;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.SpellEffects;
import dev.arcane.spell.Spell;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Casts the staff's selected spell: charged spells are held and fire on release, the rest fire on click. All spell
 * logic runs on the server; the client only predicts whether a cast is allowed.
 */
public final class ArcaneStaffItem extends Item {
    private static final int USE_DURATION = 72000;

    public ArcaneStaffItem(Properties properties) {
        super(properties);
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
            if (!level.isClientSide()) player.displayClientMessage(blocker, true);
            return InteractionResult.FAIL;
        }
        if (spell.charged()) {
            player.startUsingItem(hand);
            if (!level.isClientSide()) {
                level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.6f, 1.2f);
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
        if (level instanceof ServerLevel server && charged % SpellTuning.CHARGE_EFFECT_INTERVAL == 0) {
            Vec3 direction = user.getLookAngle();
            Vec3 center = user.getEyePosition().add(direction.scale(1.4));
            SpellEffects.send(server, SpellEffectPayload.Kind.CHARGE, center, direction, SpellTuning.power(charged), charged, user.getId());
        }
    }

    @Override public boolean releaseUsing(ItemStack stack, Level level, LivingEntity user, int remaining) {
        int charged = getUseDuration(stack, user) - remaining;
        if (charged < SpellTuning.MIN_CHARGE_TICKS || !(level instanceof ServerLevel server) || !(user instanceof ServerPlayer player)) {
            return false;
        }
        return SpellCasting.cast(server, player, SpellCasting.selected(stack), SpellTuning.power(charged));
    }
}
