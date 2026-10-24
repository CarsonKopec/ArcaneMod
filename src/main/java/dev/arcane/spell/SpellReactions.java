package dev.arcane.spell;

import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * How the Arcane Staff's spells play off one another. <b>Shatter:</b> Arcane Bolt and Chain Lightning hit a frozen
 * target (after Frost Nova, or powder snow) {@link SpellTuning#SHATTER_MULTIPLIER} times as hard, breaking the ice.
 * <b>Conduction:</b> Chain Lightning hits a wet target (in water or rain) {@link SpellTuning#CONDUCTION_MULTIPLIER} times
 * as hard, and leaps that much farther from it.
 */
final class SpellReactions {
    private SpellReactions() {
    }

    /** {@code damage} to {@code victim}, shattering it first if it's frozen solid. */
    static float shatter(ServerLevel level, LivingEntity victim, float damage) {
        if (!victim.isFullyFrozen()) return damage;
        victim.setTicksFrozen(0);
        Vec3 at = victim.getBoundingBox().getCenter();
        level.playSound(null, at.x, at.y, at.z, ModSounds.SHATTER, SoundSource.PLAYERS, 1.0f, 1.0f);
        SpellEffects.send(level, ModSpellEffects.SHATTER, at, new Vec3(0, 1, 0), victim.getBbWidth(), 0, victim.getId());
        return damage * SpellTuning.SHATTER_MULTIPLIER;
    }

    /** Whether lightning conducts through {@code victim}. */
    static boolean wet(LivingEntity victim) {
        return victim.isInWaterOrRain();
    }

    /** {@code damage} to {@code victim}, surging through it if it's wet. */
    static float conduct(ServerLevel level, LivingEntity victim, float damage) {
        if (!wet(victim)) return damage;
        Vec3 at = victim.getBoundingBox().getCenter();
        level.playSound(null, at.x, at.y, at.z, ModSounds.CONDUCTION, SoundSource.PLAYERS, 0.8f, 1.0f);
        SpellEffects.send(level, ModSpellEffects.CONDUCTION, at, new Vec3(0, 1, 0), victim.getBbWidth(), 0, victim.getId());
        return damage * SpellTuning.CONDUCTION_MULTIPLIER;
    }
}
