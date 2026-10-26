package dev.arcane.spell;

import dev.arcane.registry.ModGameRules;
import dev.leyline.terrain.TerrainRules;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Whether a spell may break blocks at all: only where the caster could build, so never in Adventure mode, and never with
 * the {@link ModGameRules#TERRAIN_DAMAGE} game rule off. It's read once, when the spell is cast ({@link #mayBreak}), and
 * passed on as {@code mayBuild}: a bolt or meteor still in flight follows the rules of the moment it was cast, whatever
 * the caster or the server does meanwhile. Everything else about terrain, the rules every spell shares block by block,
 * is Leyline's {@link TerrainRules}.
 */
final class SpellTerrain {
    private SpellTerrain() {
    }

    /** Whether a spell {@code caster} casts now may break blocks at all; read it once, at the cast. */
    static boolean mayBreak(ServerLevel level, ServerPlayer caster) {
        return caster.mayBuild() && level.getGameRules().get(ModGameRules.TERRAIN_DAMAGE);
    }
}
