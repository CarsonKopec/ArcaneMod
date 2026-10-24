package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Celestial Judgment, an Astral Staff spell. The caster channels for two seconds while a ring marks the ground they aim
 * at; on release the sky darkens over the mark, rune circles form on the ground and in the sky,
 * then a pillar of starlight bores a shaft into the earth and detonates, leaving a scorched crater. See
 * {@link JudgmentStrike} for the strike itself. Owns every strike in progress.
 */
public final class CelestialJudgment extends MarkedSpell {
    private static final List<JudgmentStrike> ACTIVE = new ArrayList<>();
    // Negative, like bolts, so strike ids never collide with the entity ids channel effects use.
    private static int nextSourceId = -1;

    public CelestialJudgment() {
        super(new Properties().manaCost(SpellTuning.JUDGMENT_MANA).cooldown(SpellTuning.JUDGMENT_COOLDOWN_TICKS)
                .charged(SpellTuning.JUDGMENT_CHARGE_TICKS).color(0xFFFFE08A), SpellTuning.JUDGMENT_CRATER_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.removeIf(JudgmentStrike::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        return ground(level, caster, SpellTuning.JUDGMENT_RANGE, SpellTuning.JUDGMENT_MIN_DISTANCE);
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (ACTIVE.size() >= SpellTuning.MAX_ACTIVE_JUDGMENTS) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        ACTIVE.add(new JudgmentStrike(context.level(), context.caster(), mark, context.spellPower(), nextSourceId--));
        return true;
    }
}
