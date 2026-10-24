package dev.arcane.spell;

import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSpellEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** One server-simulated projectile. Collision is swept per tick; the client only ever sees effect messages. */
final class SpellBolt {
    private final ServerLevel level;
    private final ServerPlayer owner;
    /** Whether the owner could build when they cast it. */
    private final boolean mayBuild;
    private final Vec3 direction;
    private final float power;
    /** What its damage is multiplied by. */
    private final float spellPower;
    private final int sourceId;
    private Vec3 position;
    private int age;

    SpellBolt(ServerLevel level, ServerPlayer owner, Vec3 position, Vec3 direction, float power, float spellPower, int sourceId) {
        this.level = level;
        this.owner = owner;
        this.mayBuild = SpellTerrain.mayBreak(level, owner);
        this.position = position;
        this.direction = direction;
        this.power = power;
        this.spellPower = spellPower;
        this.sourceId = sourceId;
    }

    /** Advances the bolt one tick. Returns true once it has detonated or expired. */
    boolean tick() {
        if (++age > SpellTuning.BOLT_LIFETIME_TICKS || owner.isRemoved() || owner.level() != level || !isChunkLoaded(position)) {
            return true;
        }
        Vec3 end = position.add(direction.scale(SpellTuning.boltSpeed(power)));
        if (!isChunkLoaded(end)) return true;

        BlockHitResult blockHit = level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 nearest = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        double nearestDistance = position.distanceToSqr(nearest);
        LivingEntity target = null;
        // Sweep the entire movement segment so fast bolts cannot tunnel through targets.
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(position, end).inflate(0.4),
                e -> e.isPickable() && SpellTargets.canTarget(owner, e))) {
            AABB box = entity.getBoundingBox().inflate(0.25);
            Vec3 hit = box.contains(position) ? position : box.clip(position, end).orElse(null);
            if (hit != null && position.distanceToSqr(hit) < nearestDistance) {
                nearest = hit;
                nearestDistance = position.distanceToSqr(hit);
                target = entity;
            }
        }

        SpellEffects.send(level, ModSpellEffects.BOLT, position, nearest.subtract(position), power, age, sourceId);
        position = nearest;
        if (target == null && blockHit.getType() == HitResult.Type.MISS) return false;
        detonate(target);
        return true;
    }

    /** {@code directTarget} is the entity the bolt struck, or null when it hit a block. */
    private void detonate(LivingEntity directTarget) {
        // Move the blast just outside the struck surface so the wall does not hide every ray.
        Vec3 blast = position.subtract(direction.scale(0.3));
        double radius = SpellTuning.blastRadius(power);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(blast, blast).inflate(radius),
                e -> SpellTargets.canHarm(owner, e))) {
            boolean direct = victim == directTarget;
            double distance = victim.getBoundingBox().getCenter().distanceTo(blast);
            if (!direct && (distance > radius || !SpellTargets.clearPath(level, blast, victim.getEyePosition(), owner))) continue;

            float falloff = (float) Math.max(0, 1 - distance / radius);
            float damage = spellPower * (direct ? SpellTuning.directHitDamage(power) : SpellTuning.splashDamage(power, falloff));
            damage = SpellReactions.shatter(level, victim, damage);
            if (victim.hurtServer(level, level.damageSources().indirectMagic(owner, owner), damage)) {
                Vec3 push = victim.position().subtract(blast).normalize().scale(falloff * (0.6 + power));
                victim.push(push.x, 0.25 + falloff * 0.45, push.z);
            }
        }
        // Use vanilla block resistance, drops, and TNT chain reactions. Do this after
        // spell damage so cover is evaluated before the terrain is destroyed.
        level.explode(owner, level.damageSources().indirectMagic(owner, owner), SpellTerrain.explosion(level, owner, mayBuild),
                position, SpellTuning.terrainBlastStrength(power), false, Level.ExplosionInteraction.BLOCK);
        SpellEffects.send(level, ModSpellEffects.IMPACT, position, direction, power, age, sourceId);
    }

    /** Column-only check: unlike {@link Level#isLoaded}, positions above or below the build limit still count. */
    private boolean isChunkLoaded(Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        return level.hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()));
    }
}
