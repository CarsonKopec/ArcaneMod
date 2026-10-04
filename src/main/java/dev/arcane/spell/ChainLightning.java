package dev.arcane.spell;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.SpellEffects;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Lightning that strikes the creature under the caster's aim, then leaps to the nearest unstruck one, weakening each time. */
final class ChainLightning {
    private ChainLightning() {
    }

    static boolean cast(ServerLevel level, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        // A miss reports the end of the ray, so this is where the caster aimed either way.
        Vec3 reach = level.clip(new ClipContext(eye, eye.add(look.scale(SpellTuning.CHAIN_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
        // Roughly the tip of the staff.
        Vec3 from = eye.add(look.scale(0.8)).add(0, -0.3, 0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.5f, 1.9f);

        LivingEntity target = aimed(level, caster, eye, reach);
        if (target == null) {
            // Nothing in reach: the arc still crackles out to the aimed point.
            SpellEffects.send(level, SpellEffectPayload.Kind.CHAIN, from, reach.subtract(from), 0.5f, 0, caster.getId());
            return true;
        }
        Set<LivingEntity> struck = new HashSet<>();
        float damage = SpellTuning.CHAIN_DAMAGE;
        for (int jump = 0; target != null && jump < SpellTuning.CHAIN_MAX_TARGETS; jump++) {
            Vec3 at = target.getBoundingBox().getCenter();
            SpellEffects.send(level, SpellEffectPayload.Kind.CHAIN, from, at.subtract(from), damage / SpellTuning.CHAIN_DAMAGE, jump, caster.getId());
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.PLAYERS, 0.35f, 1.6f + jump * 0.12f);
            struck.add(target);
            from = at;
            damage *= SpellTuning.CHAIN_DAMAGE_FALLOFF;
            target = nextJump(level, caster, at, struck);
        }
        return true;
    }

    /** The nearest living entity whose (slightly enlarged) hitbox the aim ray passes through. */
    private static LivingEntity aimed(ServerLevel level, ServerPlayer caster, Vec3 eye, Vec3 reach) {
        LivingEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(eye, reach).inflate(1),
                e -> SpellTargets.canTarget(caster, e))) {
            Optional<Vec3> hit = entity.getBoundingBox().inflate(SpellTuning.CHAIN_AIM_ASSIST).clip(eye, reach);
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearestDistance) {
                nearest = entity;
                nearestDistance = eye.distanceToSqr(hit.get());
            }
        }
        return nearest;
    }

    /** The closest unstruck target within leaping range that the lightning can reach without passing through a wall. */
    private static LivingEntity nextJump(ServerLevel level, ServerPlayer caster, Vec3 from, Set<LivingEntity> struck) {
        LivingEntity nearest = null;
        double nearestDistance = SpellTuning.CHAIN_JUMP_RANGE * SpellTuning.CHAIN_JUMP_RANGE;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(SpellTuning.CHAIN_JUMP_RANGE),
                e -> !struck.contains(e) && SpellTargets.canTarget(caster, e))) {
            Vec3 center = entity.getBoundingBox().getCenter();
            double distance = from.distanceToSqr(center);
            if (distance < nearestDistance && SpellTargets.clearPath(level, from, center, caster)) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }
}
