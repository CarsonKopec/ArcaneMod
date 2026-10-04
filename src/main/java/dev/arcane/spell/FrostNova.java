package dev.arcane.spell;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.SpellEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A burst of cold from the caster: damages, slows and freezes everything nearby that it can reach. */
final class FrostNova {
    private FrostNova() {
    }

    static boolean cast(ServerLevel level, ServerPlayer caster) {
        Vec3 center = caster.getBoundingBox().getCenter();
        double radius = SpellTuning.NOVA_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(radius),
                e -> SpellTargets.canTarget(caster, e))) {
            Vec3 at = victim.getBoundingBox().getCenter();
            if (at.distanceTo(center) > radius || !SpellTargets.clearPath(level, center, victim.getEyePosition(), caster)) continue;

            boolean heatLoving = victim.getType().is(EntityTypeTags.FREEZE_HURTS_EXTRA_TYPES);
            float damage = SpellTuning.NOVA_DAMAGE * (heatLoving ? SpellTuning.NOVA_HEAT_MULTIPLIER : 1);
            victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), damage);
            // Past the freezing threshold, the target shakes, frosts over and takes vanilla freeze damage until it
            // thaws. canFreeze() is false for strays, snow golems and anyone wearing leather, as with powder snow.
            if (victim.canFreeze()) {
                victim.setTicksFrozen(Math.max(victim.getTicksFrozen(), victim.getTicksRequiredToFreeze() + SpellTuning.NOVA_FREEZE_TICKS));
            }
            victim.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, SpellTuning.NOVA_SLOW_TICKS, SpellTuning.NOVA_SLOW_AMPLIFIER), caster);
            Vec3 push = at.subtract(center).multiply(1, 0, 1).normalize().scale(SpellTuning.NOVA_KNOCKBACK);
            victim.push(push.x, 0.15, push.z);
        }
        level.playSound(null, center.x, center.y, center.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.9f, 1.3f);
        level.playSound(null, center.x, center.y, center.z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 1.0f, 0.7f);
        SpellEffects.send(level, SpellEffectPayload.Kind.NOVA, caster.position(), new Vec3(0, 1, 0), 1, 0, caster.getId());
        return true;
    }
}
