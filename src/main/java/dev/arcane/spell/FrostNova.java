package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** A burst of cold from the caster: damages, slows and freezes everything nearby that it can reach. */
public final class FrostNova extends Spell {
    public FrostNova() {
        super(new Properties().manaCost(SpellTuning.NOVA_MANA).cooldown(SpellTuning.NOVA_COOLDOWN_TICKS).color(0xFFCFF6FF));
    }

    @Override public boolean cast(SpellContext context) {
        ServerLevel level = context.level();
        ServerPlayer caster = context.caster();
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
        level.playSound(null, center.x, center.y, center.z, ModSounds.FROST_NOVA, SoundSource.PLAYERS, 1.0f, 1.0f);
        SpellEffects.send(level, ModSpellEffects.NOVA, caster.position(), new Vec3(0, 1, 0), 1, 0, caster.getId());
        return true;
    }
}
