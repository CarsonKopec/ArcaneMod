package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.effect.Effects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Arcane Ward: a shell around the caster that absorbs hits from outside until its strength runs out, and turns
 * incoming projectiles back on whoever fired them. A ward that is broken, rather than left to fade, bursts outwards
 * as it goes. Owns every active ward.
 */
public final class ArcaneWard extends Spell {
    private static final Map<UUID, Ward> ACTIVE = new HashMap<>();

    public ArcaneWard() {
        super(new Properties().manaCost(SpellTuning.WARD_MANA).cooldown(SpellTuning.WARD_COOLDOWN_TICKS).color(0xFFFFD86B));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.values().removeIf(Ward::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ArcaneWard::allowDamage);
    }

    /** Strength left in {@code player}'s ward, or 0 when they have none. */
    public static float strength(Player player) {
        Ward ward = ACTIVE.get(player.getUUID());
        return ward == null || ward.owner != player ? 0 : Math.max(0, ward.strength);
    }

    @Override public boolean cast(SpellContext context) {
        ServerLevel level = context.level();
        ServerPlayer caster = context.caster();
        // Recasting replaces the old ward with a fresh one.
        ACTIVE.put(caster.getUUID(), new Ward(level, caster, context.spellPower()));
        Vec3 center = caster.getBoundingBox().getCenter();
        level.playSound(null, center.x, center.y, center.z, ModSounds.WARD_RAISE, SoundSource.PLAYERS, 0.8f, 1.0f);
        Effects.send(level, ModSpellEffects.WARD, center, new Vec3(0, 1, 0), 1, SpellTuning.WARD_DURATION_TICKS, caster.getId());
        return true;
    }

    private static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        Ward ward = ACTIVE.get(entity.getUUID());
        if (ward == null || ward.owner != entity || ward.strength <= 0 || !blocks(source)) return true;
        ward.absorb(source.getSourcePosition(), amount);
        return false;
    }

    /** Anything that strikes from outside: attacks, projectiles, explosions. Not falls, fire, drowning, hunger or /kill. */
    private static boolean blocks(DamageSource source) {
        return source.getSourcePosition() != null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    private static final class Ward {
        final ServerLevel level;
        final ServerPlayer owner;
        /** What its rebound's damage is multiplied by, fixed at the cast. */
        final float spellPower;
        float strength = SpellTuning.WARD_STRENGTH;
        int age;

        Ward(ServerLevel level, ServerPlayer owner, float spellPower) {
            this.level = level;
            this.owner = owner;
            this.spellPower = spellPower;
        }

        /** Returns true once the ward is gone. */
        boolean tick() {
            if (strength <= 0) return true; // Shattered; absorb() already announced it.
            if (owner.isRemoved() || !owner.isAlive() || owner.level() != level || ++age >= SpellTuning.WARD_DURATION_TICKS) {
                end(false);
                return true;
            }
            deflectProjectiles();
            return false;
        }

        /** The hit that breaks the ward is still fully absorbed. */
        void absorb(Vec3 from, float amount) {
            strength -= amount;
            if (strength <= 0) {
                end(true);
                return;
            }
            Vec3 center = owner.getBoundingBox().getCenter();
            Vec3 outward = from.subtract(center).normalize();
            Vec3 hit = center.add(outward.scale(SpellTuning.WARD_RADIUS));
            Effects.send(level, ModSpellEffects.WARD_HIT, hit, outward, strength / SpellTuning.WARD_STRENGTH, 0, owner.getId());
            level.playSound(null, hit.x, hit.y, hit.z, ModSounds.WARD_HIT, SoundSource.PLAYERS, 0.8f, 1.0f);
        }

        void end(boolean shattered) {
            strength = 0;
            Vec3 center = owner.getBoundingBox().getCenter();
            Effects.send(level, ModSpellEffects.WARD_END, center, new Vec3(0, 1, 0), shattered ? 1 : 0, 0, owner.getId());
            if (shattered) {
                level.playSound(null, center.x, center.y, center.z, ModSounds.WARD_BREAK, SoundSource.PLAYERS, 1.0f, 1.0f);
                rebound(center);
            } else {
                level.playSound(null, center.x, center.y, center.z, ModSounds.WARD_FADE, SoundSource.PLAYERS, 0.7f, 1.0f);
            }
        }

        /** Rebound: a broken ward bursts outwards, hurting and hurling whatever is near its owner. */
        void rebound(Vec3 center) {
            double radius = SpellTuning.WARD_REBOUND_RADIUS;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    e -> SpellTargets.canHarm(owner, e) && e.getBoundingBox().getCenter().distanceTo(center) <= radius)) {
                if (!victim.hurtServer(level, level.damageSources().indirectMagic(owner, owner), SpellTuning.WARD_REBOUND_DAMAGE * spellPower)) {
                    continue;
                }
                Vec3 away = victim.position().subtract(owner.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0e-6 ? Vec3.ZERO : away.normalize().scale(SpellTuning.WARD_REBOUND_KNOCKBACK);
                victim.push(away.x, 0.4, away.z);
                victim.hurtMarked = true;
            }
            level.playSound(null, center.x, center.y, center.z, ModSounds.WARD_REBOUND, SoundSource.PLAYERS, 1.0f, 1.0f);
        }

        /** Turns back any projectile that will be inside the shell next tick and is still heading inwards. */
        void deflectProjectiles() {
            Vec3 center = owner.getBoundingBox().getCenter();
            for (Projectile projectile : level.getEntitiesOfClass(Projectile.class,
                    owner.getBoundingBox().inflate(SpellTuning.WARD_RADIUS + 4), p -> p.getOwner() != owner)) {
                Vec3 offset = projectile.position().subtract(center);
                Vec3 velocity = projectile.getDeltaMovement();
                if (velocity.dot(offset) >= 0 || offset.length() - velocity.length() > SpellTuning.WARD_RADIUS) continue;
                // The ward's owner takes ownership, so the returned shot counts as theirs.
                projectile.deflect(ProjectileDeflection.REVERSE, owner, EntityReference.of(owner), false);
                Vec3 hit = center.add(offset.normalize().scale(SpellTuning.WARD_RADIUS));
                Effects.send(level, ModSpellEffects.WARD_HIT, hit, offset.normalize(), strength / SpellTuning.WARD_STRENGTH, 0, owner.getId());
                level.playSound(null, hit.x, hit.y, hit.z, ModSounds.WARD_HIT, SoundSource.PLAYERS, 0.8f, 1.35f);
                strength -= SpellTuning.WARD_DEFLECT_COST;
                if (strength <= 0) {
                    end(true);
                    return;
                }
            }
        }
    }
}
