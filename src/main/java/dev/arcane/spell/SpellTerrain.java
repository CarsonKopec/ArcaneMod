package dev.arcane.spell;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What spells may do to terrain, shared so every spell follows the same rules: blocks only break where the caster
 * could break them by hand, so never in Adventure mode, under spawn protection or beyond the world border. Whether the
 * caster may build is read once, when the spell is cast, and passed in as {@code mayBuild}: a bolt or meteor still in
 * flight follows the rules of the moment it was cast, whatever the caster does meanwhile.
 */
final class SpellTerrain {
    private SpellTerrain() {
    }

    /**
     * The terrain half of a vanilla explosion: vanilla resistance, drops and TNT chain reactions, where the caster may
     * break blocks. It hurts and pushes no entities; spells do that themselves, so nothing is hit twice. Skipped blocks
     * aren't hit at all, so protected TNT isn't set off either.
     */
    static ExplosionDamageCalculator explosion(ServerLevel level, ServerPlayer caster, boolean mayBuild) {
        return new ExplosionDamageCalculator() {
            @Override public boolean shouldBlockExplode(Explosion explosion, BlockGetter blocks, BlockPos pos, BlockState state, float strength) {
                return mayBuild && level.mayInteract(caster, pos) && super.shouldBlockExplode(explosion, blocks, pos, state, strength);
            }

            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return false;
            }

            @Override public float getKnockbackMultiplier(Entity entity) {
                return 0;
            }
        };
    }

    /**
     * Whether a spell may vaporise this block: where the caster may break blocks, and unless it's unbreakable or at
     * least as blast resistant as obsidian.
     */
    static boolean mayVaporise(ServerLevel level, ServerPlayer caster, boolean mayBuild, BlockPos pos, BlockState state) {
        return mayBuild && !state.isAir() && state.getDestroySpeed(level, pos) >= 0
                && state.getBlock().getExplosionResistance() < SpellTuning.VAPORISE_RESISTANCE_LIMIT && level.mayInteract(caster, pos);
    }

    /** Removes the block without drops; a container still spills what it held. */
    static void vaporise(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
