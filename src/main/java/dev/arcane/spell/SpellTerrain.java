package dev.arcane.spell;

import dev.arcane.registry.ModGameRules;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What spells may do to terrain, shared so every spell follows the same rules: blocks only break where the caster
 * could break them by hand, so never in Adventure mode, under spawn protection or beyond the world border, and never
 * with the {@link ModGameRules#TERRAIN_DAMAGE} game rule off. Whether the spell may break blocks at all is read once,
 * when it is cast ({@link #mayBreak}), and passed in as {@code mayBuild}: a bolt or meteor still in flight follows the
 * rules of the moment it was cast, whatever the caster or the server does meanwhile.
 */
final class SpellTerrain {
    private SpellTerrain() {
    }

    /** Whether a spell {@code caster} casts now may break blocks at all; read it once, at the cast. */
    static boolean mayBreak(ServerLevel level, ServerPlayer caster) {
        return caster.mayBuild() && level.getGameRules().get(ModGameRules.TERRAIN_DAMAGE);
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

    /** Natural ground, which spells may scorch or reshape: stone, dirt, sand, gravel and terracotta. */
    static boolean isGround(BlockState state) {
        return state.is(BlockTags.BASE_STONE_OVERWORLD) || state.is(BlockTags.DIRT) || state.is(BlockTags.SAND)
                || state.is(Blocks.GRAVEL) || state.is(BlockTags.TERRACOTTA);
    }

    /** Whether the block at {@code pos} has air on any side. */
    static boolean exposed(ServerLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getBlockState(pos.relative(direction)).isAir()) return true;
        }
        return false;
    }

    /** Removes the block without drops; a container still spills what it held. */
    static void vaporise(ServerLevel level, BlockPos pos) {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** Sears a crater's exposed floor and walls ({@code shell}): natural ground turns partly to blackstone, basalt and magma. */
    static void scorch(ServerLevel level, ServerPlayer caster, boolean mayBuild, List<BlockPos> shell, RandomSource random) {
        for (BlockPos pos : shell) {
            BlockState state = level.getBlockState(pos);
            if (!isGround(state) || !mayVaporise(level, caster, mayBuild, pos, state) || !exposed(level, pos)) continue;
            float roll = random.nextFloat();
            BlockState seared = roll < 0.15f ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : roll < 0.5f ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 0.65f ? Blocks.BASALT.defaultBlockState()
                    : null;
            if (seared != null) level.setBlock(pos, seared, Block.UPDATE_ALL);
        }
    }
}
