package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.effect.Effects;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * An Astral Staff spell: channelled in full while a ring marks where it will land. Everyone nearby sees the ring, red
 * where the spell can't land; releasing early, or with nowhere to land, costs nothing. Subclasses choose where it lands
 * ({@link #aim}) and what happens there ({@link #land}).
 */
abstract class MarkedSpell extends Spell {
    private final double markRadius;

    /** @param markRadius radius of the ring marking where the spell will land: its area, or the size of its effect */
    MarkedSpell(Properties properties, double markRadius) {
        super(properties);
        this.markRadius = markRadius;
    }

    @Override public void onChargeStart(SpellContext context) {
        ServerPlayer caster = context.caster();
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.ASTRAL_CHANNEL, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    @Override public void onChargeTick(SpellContext context, int chargedTicks) {
        if (chargedTicks % SpellTuning.CHARGE_EFFECT_INTERVAL != 0) return;
        ServerPlayer caster = context.caster();
        Aim aim = aim(context.level(), caster);
        Vec3 at = aim.point() != null ? aim.point() : caster.position();
        double radius = aim.problem() == null ? markRadius : -markRadius;
        Effects.send(context.level(), ModSpellEffects.ASTRAL_CHANNEL, at, new Vec3(0, radius, 0), context.power(), chargedTicks, caster.getId());
    }

    @Override public final boolean cast(SpellContext context) {
        ServerPlayer caster = context.caster();
        if (context.power() < 1) {
            caster.displayClientMessage(Component.translatable("message.arcane_spells.channel_unfinished", displayName()), true);
            return false;
        }
        Aim aim = aim(context.level(), caster);
        if (aim.problem() != null) {
            caster.displayClientMessage(aim.problem(), true);
            return false;
        }
        return land(context, aim.point());
    }

    /** Where the spell would land if released now, or why it can't land. Called every few ticks while channelling. */
    protected abstract Aim aim(ServerLevel level, ServerPlayer caster);

    /** Lands the spell at {@code mark}; returns false, costing nothing, if it couldn't after all. */
    protected abstract boolean land(SpellContext context, Vec3 mark);

    /**
     * The first surface the caster is aiming at, water included, within {@code range}; refused if it's nearer than
     * {@code minDistance} horizontally, so the caster isn't caught in it.
     */
    static Aim ground(ServerLevel level, ServerPlayer caster, double range, double minDistance) {
        BlockHitResult hit = surface(level, caster, range);
        if (hit.getType() == HitResult.Type.MISS) return Aim.refused(null, "message.arcane_spells.no_target");
        Vec3 point = hit.getLocation();
        double dx = point.x - caster.getX(), dz = point.z - caster.getZ();
        if (dx * dx + dz * dz < minDistance * minDistance) return Aim.refused(point, "message.arcane_spells.too_close");
        return Aim.at(point);
    }

    static BlockHitResult surface(ServerLevel level, ServerPlayer caster, double range) {
        Vec3 eye = caster.getEyePosition();
        return level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, caster));
    }

    /** {@code point} is null when nothing is in reach; {@code problem} is null when the spell can land there. */
    record Aim(Vec3 point, Component problem) {
        static Aim at(Vec3 point) {
            return new Aim(point, null);
        }

        static Aim refused(Vec3 point, String messageKey) {
            return new Aim(point, Component.translatable(messageKey));
        }
    }
}
