package dev.arcane.spell;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.SpellEffects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.Vec3;

/** Teleports the caster along their aim, stopping short of whatever they're looking at. */
final class Blink {
    private static final double STEP = 0.25;

    private Blink() {
    }

    static boolean cast(ServerLevel level, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        // A miss reports the end of the ray, so this is where the caster aimed either way.
        Vec3 aim = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(SpellTuning.BLINK_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
        Vec3 destination = landingSpot(level, caster, eye, aim);
        if (destination == null) {
            caster.displayClientMessage(Component.translatable("message.arcane_spells.blink_blocked"), true);
            return false;
        }
        Vec3 origin = caster.position();
        caster.stopRiding();
        caster.teleportTo(destination.x, destination.y, destination.z);
        caster.resetFallDistance();
        level.playSound(null, origin.x, origin.y, origin.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.5f);
        level.playSound(null, destination.x, destination.y, destination.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.6f, 1.8f);
        SpellEffects.send(level, SpellEffectPayload.Kind.BLINK, origin, destination.subtract(origin), 1, 0, caster.getId());
        return true;
    }

    /**
     * Feet position for the farthest point along the aim where the caster fits, or null if that is barely a step away.
     * Each point first tries the caster standing at their usual eye height, so blinking straight ahead over flat
     * ground lands on it, then with their body centred on the ray, which fits through gaps and over ledges.
     */
    private static Vec3 landingSpot(ServerLevel level, ServerPlayer caster, Vec3 eye, Vec3 aim) {
        Vec3 path = aim.subtract(eye);
        double length = path.length();
        if (length < SpellTuning.BLINK_MIN_DISTANCE) return null;
        Vec3 direction = path.scale(1 / length);
        double[] drops = {caster.getEyeHeight(), caster.getBbHeight() / 2};
        for (double distance = length; distance >= SpellTuning.BLINK_MIN_DISTANCE; distance -= STEP) {
            Vec3 point = eye.add(direction.scale(distance));
            for (double drop : drops) {
                Vec3 feet = point.subtract(0, drop, 0);
                if (level.noCollision(caster, caster.getBoundingBox().move(feet.subtract(caster.position())))) return feet;
            }
        }
        return null;
    }
}
