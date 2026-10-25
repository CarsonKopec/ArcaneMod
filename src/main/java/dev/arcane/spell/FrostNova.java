package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A burst of cold from the caster: damages, slows and freezes everything nearby that it can reach. Rime: water in
 * reach freezes over, melting back as Frost Walker's ice does, and fire in reach goes out, on creatures and blocks.
 */
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
            float damage = SpellTuning.NOVA_DAMAGE * (heatLoving ? SpellTuning.NOVA_HEAT_MULTIPLIER : 1) * context.spellPower();
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
        rime(level, caster, center);
        level.playSound(null, center.x, center.y, center.z, ModSounds.FROST_NOVA, SoundSource.PLAYERS, 1.0f, 1.0f);
        SpellEffects.send(level, ModSpellEffects.NOVA, caster.position(), new Vec3(0, 1, 0), 1, 0, caster.getId());
        return true;
    }

    /**
     * Rime: still water in reach with air above it turns to frosted ice, and fire goes out. Blocks only change where
     * the caster could change them by hand; burning creatures, the caster too, are put out regardless.
     */
    private static void rime(ServerLevel level, ServerPlayer caster, Vec3 center) {
        double radius = SpellTuning.NOVA_RADIUS;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isOnFire() && e.getBoundingBox().getCenter().distanceTo(center) <= radius)) {
            entity.clearFire();
        }
        if (!SpellTerrain.mayBreak(level, caster)) return;
        BlockPos feet = caster.blockPosition();
        int reach = Mth.ceil(radius);
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-reach, -3, -reach), feet.offset(reach, 2, reach))) {
            if (pos.getCenter().distanceTo(center) > radius || !level.mayInteract(caster, pos)) continue;
            var state = level.getBlockState(pos);
            if (state.is(BlockTags.FIRE)) {
                level.removeBlock(pos, false);
            } else if (state == Blocks.WATER.defaultBlockState() && level.getBlockState(pos.above()).isAir()) {
                level.setBlockAndUpdate(pos, Blocks.FROSTED_ICE.defaultBlockState());
                level.scheduleTick(pos.immutable(), Blocks.FROSTED_ICE, Mth.nextInt(caster.getRandom(), 60, 120));
            }
        }
    }
}
