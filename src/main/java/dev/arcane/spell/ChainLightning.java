package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.effect.Effects;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lightning that strikes the creature under the caster's aim, then leaps to the nearest unstruck one, weakening each
 * time. Frozen targets shatter, and wet ones conduct it: see {@link SpellReactions}.
 */
public final class ChainLightning extends Spell {
    public ChainLightning() {
        super(new Properties().manaCost(SpellTuning.CHAIN_MANA).cooldown(SpellTuning.CHAIN_COOLDOWN_TICKS).color(0xFF7EE8FF));
    }

    @Override public boolean cast(SpellContext context) {
        ServerLevel level = context.level();
        ServerPlayer caster = context.caster();
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        // A miss reports the end of the ray, so this is where the caster aimed either way.
        Vec3 reach = level.clip(new ClipContext(eye, eye.add(look.scale(SpellTuning.CHAIN_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
        // Roughly the tip of the staff.
        Vec3 from = eye.add(look.scale(0.8)).add(0, -0.3, 0);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.CHAIN_CAST, SoundSource.PLAYERS, 0.8f, 1.0f);

        LivingEntity target = aimed(level, caster, eye, reach);
        if (target == null) {
            // Nothing in reach: the arc still crackles out to the aimed point.
            Effects.send(level, ModSpellEffects.CHAIN, from, reach.subtract(from), 0.5f, 0, caster.getId());
            return true;
        }
        Set<LivingEntity> struck = new HashSet<>();
        float damage = SpellTuning.CHAIN_DAMAGE * context.spellPower();
        int jump = 0;
        LivingEntity last = target;
        for (; target != null && jump < SpellTuning.CHAIN_MAX_TARGETS; jump++) {
            last = target;
            Vec3 at = target.getBoundingBox().getCenter();
            // The arc's brightness falls with each leap, whatever the caster's spell power.
            float strength = (float) Math.pow(SpellTuning.CHAIN_DAMAGE_FALLOFF, jump);
            Effects.send(level, ModSpellEffects.CHAIN, from, at.subtract(from), strength, jump, caster.getId());
            // Each leap weakens from the last one's own damage, so a reaction doesn't carry on down the chain.
            boolean wet = SpellReactions.wet(target);
            float dealt = SpellReactions.conduct(level, target, SpellReactions.shatter(level, target, damage));
            target.hurtServer(level, level.damageSources().indirectMagic(caster, caster), dealt);
            level.playSound(null, at.x, at.y, at.z, ModSounds.CHAIN_HIT, SoundSource.PLAYERS, 0.6f, 1.0f + jump * 0.08f);
            struck.add(target);
            from = at;
            damage *= SpellTuning.CHAIN_DAMAGE_FALLOFF;
            double range = SpellTuning.CHAIN_JUMP_RANGE * (wet ? SpellTuning.CONDUCTION_MULTIPLIER : 1);
            target = nextJump(level, caster, at, struck, range);
        }
        if (jump < SpellTuning.CHAIN_MAX_TARGETS) overload(level, caster, last, SpellTuning.CHAIN_MAX_TARGETS - jump, context.spellPower());
        return true;
    }

    /**
     * Overload: the chain ran out of targets with {@code leaps} leaps unspent, and they all discharge at once around
     * the last creature it struck, hurting it and everything near it.
     */
    private static void overload(ServerLevel level, ServerPlayer caster, LivingEntity last, int leaps, float spellPower) {
        Vec3 at = last.getBoundingBox().getCenter();
        double radius = SpellTuning.CHAIN_OVERLOAD_RADIUS;
        float damage = SpellTuning.CHAIN_OVERLOAD_DAMAGE * leaps * spellPower;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e.isAlive() && SpellTargets.canHarm(caster, e) && e.getBoundingBox().getCenter().distanceTo(at) <= radius)) {
            // The last target was struck this very tick: without this, its moment of immunity would shrug the overload off.
            victim.invulnerableTime = 0;
            victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
        }
        level.playSound(null, at.x, at.y, at.z, ModSounds.CHAIN_OVERLOAD, SoundSource.PLAYERS, 0.9f, 1.0f);
        Effects.send(level, ModSpellEffects.CHAIN_OVERLOAD, at, Vec3.ZERO, leaps / (float) SpellTuning.CHAIN_MAX_TARGETS, 0, caster.getId());
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

    /** The closest unstruck target within {@code range} that the lightning can reach without passing through a wall. */
    private static LivingEntity nextJump(ServerLevel level, ServerPlayer caster, Vec3 from, Set<LivingEntity> struck, double range) {
        LivingEntity nearest = null;
        double nearestDistance = range * range;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, from).inflate(range),
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
