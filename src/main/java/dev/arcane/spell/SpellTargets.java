package dev.arcane.spell;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Who spells may hurt, shared so every spell follows the same rules. */
final class SpellTargets {
    private SpellTargets() {
    }

    /** Alive, visible, not the caster, and not a player the server's PvP rules protect. */
    static boolean canHarm(ServerPlayer caster, LivingEntity entity) {
        return entity != caster && entity.isAlive() && !entity.isSpectator()
                && (!(entity instanceof ServerPlayer other) || caster.canHarmPlayer(other));
    }

    /** {@link #canHarm}, minus the caster's mount and anyone riding with them, as vanilla projectiles do. */
    static boolean canTarget(ServerPlayer caster, LivingEntity entity) {
        return canHarm(caster, entity) && !caster.isPassengerOfSameVehicle(entity);
    }

    /** No solid block between the two points. */
    static boolean clearPath(ServerLevel level, Vec3 from, Vec3 to, ServerPlayer caster) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster))
                .getType() == HitResult.Type.MISS;
    }
}
