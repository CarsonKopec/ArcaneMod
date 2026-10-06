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
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.animation.object.PlayState;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Supplier;

/**
 * Casts the staff's selected spell: charged spells are held and fire on release, the rest fire on click. All spell
 * logic runs on the server; the client only predicts whether a cast is allowed.
 *
 * <p>Each staff holds the spells in its tag (see {@link dev.arcane.api.SpellTags}). Other mods may use or extend it for
 * staffs of their own: any instance opens the spell wheel and shows the HUD.
 */
public class ArcaneStaffItem extends Item implements GeoItem {
    private static final int USE_DURATION = 72000;
    private static final int FIZZLE_INTERVAL_TICKS = 10;
    /** Client only: when the last refused cast fizzled. */
    private static long lastFizzle;

    /** Render inputs supplied by the client without introducing client classes into the common item. */
    public static final DataTicket<Boolean> STAFF_ANIMATIONS = DataTicket.create("arcane_spells:staff_animations", Boolean.class);
    public static final DataTicket<Boolean> CHANNELING = DataTicket.create("arcane_spells:staff_channeling", Boolean.class);
    public static final DataTicket<Float> CHANNEL_POWER = DataTicket.create("arcane_spells:staff_channel_power", Float.class);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.staff.idle");
    private static final RawAnimation CHANNEL = RawAnimation.begin().thenLoop("animation.staff.channel");
    private static final RawAnimation RELEASE = RawAnimation.begin().thenPlay("animation.staff.release");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private Supplier<Object> clientRenderProvider;

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
        GeoItem.registerSyncedAnimatable(this);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animationCache;
    }

    @Override public boolean isPerspectiveAware() {
        return true;
    }

    /** Installed during client initialization; the server never loads or instantiates a renderer. */
    public void setClientRenderProvider(Supplier<Object> provider) {
        clientRenderProvider = provider;
    }

    @Override public Object getRenderProvider() {
        return clientRenderProvider == null ? GeoItem.super.getRenderProvider() : clientRenderProvider.get();
    }

    @Override public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<ArcaneStaffItem>("staff", 4, animation -> {
            if (!animation.getDataOrDefault(STAFF_ANIMATIONS, true)) {
                animation.controller().reset();
                return PlayState.STOP;
            }
            if (animation.controller().isTriggeredAnimation("release")) {
                animation.setControllerSpeed(1);
                if (!animation.controller().hasAnimationFinished()) return PlayState.CONTINUE;
                animation.controller().stopTriggeredAnimation();
            }
            boolean channeling = animation.getDataOrDefault(CHANNELING, false);
            animation.setControllerSpeed(channeling ? 1 + animation.getDataOrDefault(CHANNEL_POWER, 0f) * 0.75f : 1);
            return animation.setAndContinue(channeling ? CHANNEL : IDLE);
        }).triggerableAnim("release", RELEASE).receiveTriggeredAnimations());
    }

    /** Called once by the shared successful-cast hook, so refused/short casts never pulse. */
    public static void triggerCastAnimation(ServerPlayer player, ItemStack stack) {
        if (stack.getItem() instanceof ArcaneStaffItem staff) {
            staff.triggerAnim(player, GeoItem.getOrAssignId(stack, player.level()), "staff", "release");
        }
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
            SpellCasting.cast(server, caster, spell, 1, hand);
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
        return SpellCasting.cast(server, player, spell, power(spell, charged), player.getUsedItemHand());
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
