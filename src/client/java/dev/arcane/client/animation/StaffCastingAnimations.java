package dev.arcane.client.animation;

import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.Animation;
import com.zigythebird.playeranimcore.animation.AnimationData;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.MirrorModifier;
import com.zigythebird.playeranimcore.bones.PlayerAnimBone;
import com.zigythebird.playeranimcore.enums.PlayState;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellTags;
import dev.arcane.api.spell.Spell;
import dev.arcane.client.config.ArcaneClientSettings;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.item.StaffCastingProfile;
import dev.arcane.spell.SpellCasting;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Staff poses occupy one PAL layer on each player. Vanilla item-use synchronization drives the charge loops, while
 * successful releases arrive through the spell's server event. Controllers belong to their player entities, so there
 * is no player map to retain old worlds. Only arms and torso yaw are authored; looking, crouching and walking remain
 * vanilla. GeckoLib owns first-person staff motion.
 */
public final class StaffCastingAnimations {
    public static final Identifier LAYER = ArcaneSpells.id("staff_casting");
    private static final Identifier ARCANE_CHANNEL = ArcaneSpells.id("staff.arcane.channel");
    private static final Identifier ASTRAL_CHANNEL = ArcaneSpells.id("staff.astral.channel");
    private static final Identifier ARCANE_RELEASE = ArcaneSpells.id("staff.arcane.release");
    private static final Identifier ASTRAL_RELEASE = ArcaneSpells.id("staff.astral.release");
    private static final Identifier PLANETFALL_CHANNEL = ArcaneSpells.id("staff.planetfall.channel");
    private static final Identifier PLANETFALL_HOLD = ArcaneSpells.id("staff.planetfall.hold");
    private static final Identifier PLANETFALL_RELEASE = ArcaneSpells.id("staff.planetfall.release");
    private static boolean registered;

    private StaffCastingAnimations() { }

    public static void register() {
        if (registered) return;
        registered = true;
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1500,
                avatar -> avatar instanceof Player player ? new CastingController(player) : null);
    }

    /**
     * Called on the client thread only for a successful cast, including instant spells. The profile comes from the
     * server, so changing the selected spell cannot change a release already earned.
     */
    public static void playRelease(int entityId, InteractionHand hand, StaffCastingProfile profile) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || !ArcaneClientSettings.castingAnimations()) return;
        if (!(client.level.getEntity(entityId) instanceof Player player)) return;
        if (!(player.getItemInHand(hand).getItem() instanceof ArcaneStaffItem)) return;
        if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof CastingController controller) {
            controller.release(hand, profile);
        }
    }

    /** Current pose, including its fade out; returns null when this layer is idle. Client thread only. */
    public static Identifier currentAnimation(Player player) {
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof CastingController controller
                ? controller.playingId : null;
    }

    private static final class CastingController extends PlayerAnimationController {
        private final Player player;
        private final MirrorModifier mirror = new MirrorModifier();
        private final Map<String, PlayerAnimBone> lastPose = new HashMap<>(3);
        private final Map<String, PlayerAnimBone> transitionPose = new HashMap<>(3);
        private Animation playing;
        private Identifier playingId;
        private float weight, previousWeight;
        private float transition = 1, previousTransition = 1;
        private int releaseTicks;
        private int lastUseTicks = -1;
        private StaffCastingProfile profile = StaffCastingProfile.ARCANE;

        CastingController(Player player) {
            super(player, (controller, state, setter) -> PlayState.STOP);
            this.player = player;
            mirror.enabled = false;
            addModifierLast(mirror);
        }

        @Override public void tick(AnimationData state) {
            previousWeight = weight;
            previousTransition = transition;
            transition = Math.min(1, transition + 0.25f);
            boolean allowed = ArcaneClientSettings.castingAnimations() && canPose();
            int useTicks = player.isUsingItem() ? player.getTicksUsingItem() : -1;
            boolean startedUse = useTicks >= 0 && (lastUseTicks < 0 || useTicks < lastUseTicks);
            lastUseTicks = useTicks;
            boolean active = false;
            if (!allowed) releaseTicks = 0;
            else if (releaseTicks > 0) {
                releaseTicks--;
                active = true;
            } else if (player.isUsingItem()) {
                ItemStack stack = player.getUseItem();
                if (stack.getItem() instanceof ArcaneStaffItem staff) {
                    boolean isAstral = staff.spells().equals(SpellTags.ASTRAL_STAFF);
                    Spell spell = SpellCasting.selected(stack);
                    StaffCastingProfile castingProfile = StaffCastingProfile.of(spell, isAstral);
                    boolean ready = useTicks >= spell.fullChargeTicks();
                    Identifier id = switch (castingProfile) {
                        case PLANETFALL -> ready ? PLANETFALL_HOLD : PLANETFALL_CHANNEL;
                        case ASTRAL -> ASTRAL_CHANNEL;
                        case ARCANE -> ARCANE_CHANNEL;
                    };
                    Animation animation = PlayerAnimResources.getAnimation(id);
                    if (animation != null) {
                        boolean left = usedArm(player.getUsedItemHand()) == HumanoidArm.LEFT;
                        if (animation != playing || !id.equals(playingId) || left != mirror.enabled
                                || (startedUse && castingProfile == StaffCastingProfile.PLANETFALL)) {
                            // The gathering movement runs once. Holding right-click beyond full charge enters a
                            // breathing ready pose instead of repeatedly lowering and raising the staff.
                            start(id, animation, left, castingProfile, !id.equals(PLANETFALL_CHANNEL));
                        }
                        active = true;
                    }
                }
            }
            weight = active ? Math.min(1, weight + 0.2f) : Math.max(0, weight - 1 / 7f);
            if (weight == 0 && previousWeight == 0 && playing != null) {
                stopTriggeredAnimation();
                stop();
                playing = null;
                playingId = null;
                lastPose.clear();
                transitionPose.clear();
            }
            super.tick(state);
        }

        private boolean canPose() {
            return player.isAlive() && !player.isSpectator() && !player.isSleeping()
                    && !player.isSwimming() && !player.isFallFlying();
        }

        private HumanoidArm usedArm(InteractionHand hand) {
            HumanoidArm mainArm = player.getMainArm();
            return hand == InteractionHand.MAIN_HAND ? mainArm : mainArm.getOpposite();
        }

        private void release(InteractionHand hand, StaffCastingProfile castingProfile) {
            if (!canPose()) return;
            Identifier id = switch (castingProfile) {
                case PLANETFALL -> PLANETFALL_RELEASE;
                case ASTRAL -> ASTRAL_RELEASE;
                case ARCANE -> ARCANE_RELEASE;
            };
            Animation animation = PlayerAnimResources.getAnimation(id);
            if (animation == null) return;
            start(id, animation, usedArm(hand) == HumanoidArm.LEFT, castingProfile, false);
            releaseTicks = Math.max(1, Mth.ceil(animation.length()));
        }

        private void start(Identifier id, Animation animation, boolean left, StaffCastingProfile castingProfile, boolean loop) {
            transitionPose.clear();
            // Capture the already mirrored pose: changing hands during a rapid follow-up must not mirror the old
            // pose a second time. Snapshots contain only this layer's three bones and live on this player instance.
            if (weight > 0) lastPose.forEach((name, bone) -> transitionPose.put(name, copy(bone)));
            transition = previousTransition = transitionPose.isEmpty() ? 1 : 0;
            mirror.enabled = left;
            profile = castingProfile;
            playing = animation;
            playingId = id;
            triggerAnimation(loop ? RawAnimation.begin().thenLoop(animation) : RawAnimation.begin().thenPlayAndHold(animation));
        }

        @Override public boolean isActive() {
            return (weight > 0 || previousWeight > 0) && super.isActive();
        }

        @Override public PlayerAnimBone get3DTransform(PlayerAnimBone bone) {
            AnimationData state = getAnimationData();
            if (state == null || state.isFirstPersonPass()) return bone;
            String name = bone.getName();
            if (!name.equals("right_arm") && !name.equals("left_arm") && !name.equals("torso")) return bone;
            float partialTick = Mth.clamp(state.getPartialTick(), 0, 1);
            float amount = smooth(Mth.lerp(partialTick, previousWeight, weight));
            if (amount <= 0) return bone;
            PlayerAnimBone posed = super.get3DTransform(copy(bone));
            if (!name.equals("torso")) {
                boolean holdingArm = name.equals(mirror.enabled ? "left_arm" : "right_arm");
                float aim = Mth.clamp(player.getXRot(), -65, 65) * Mth.DEG_TO_RAD;
                float aimWeight = switch (profile) {
                    case PLANETFALL -> 0.12f;
                    case ASTRAL -> 0.18f;
                    case ARCANE -> holdingArm ? 0.7f : 0.35f;
                };
                posed.setRotX(posed.getRotX() + aim * aimWeight);
            }
            PlayerAnimBone from = transitionPose.get(name);
            if (from != null) {
                float blend = smooth(Mth.lerp(partialTick, previousTransition, transition));
                posed = copy(from).scale(1 - blend).add(posed.scale(blend));
            }
            lastPose.put(name, copy(posed));
            return bone.scale(1 - amount).add(posed.scale(amount));
        }

        private static PlayerAnimBone copy(PlayerAnimBone bone) {
            return new PlayerAnimBone(bone.getName()).copyOtherBone(bone);
        }

        private static float smooth(float value) {
            return value * value * (3 - 2 * value);
        }
    }
}
