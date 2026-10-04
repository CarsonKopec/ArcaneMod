package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Celestial Judgment, the Astral Staff's first spell. The caster channels for two seconds while a circle marks the
 * ground they aim at; on release the sky darkens over the mark, rune circles form on the ground and in the sky,
 * then a pillar of starlight bores a shaft into the earth and detonates, leaving a scorched crater. See
 * {@link JudgmentStrike} for the strike itself. Owns every strike in progress.
 */
public final class CelestialJudgment extends Spell {
    private static final List<JudgmentStrike> ACTIVE = new ArrayList<>();
    // Negative, like bolts, so strike ids never collide with the entity ids channel effects use.
    private static int nextSourceId = -1;

    public CelestialJudgment() {
        super(new Properties().manaCost(SpellTuning.JUDGMENT_MANA).cooldown(SpellTuning.JUDGMENT_COOLDOWN_TICKS)
                .charged(SpellTuning.JUDGMENT_CHARGE_TICKS).color(0xFFFFE08A));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.removeIf(JudgmentStrike::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    @Override public void onChargeStart(SpellContext context) {
        ServerPlayer caster = context.caster();
        context.level().playSound(null, caster.getX(), caster.getY(), caster.getZ(), ModSounds.JUDGMENT_CHANNEL, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /** Everyone nearby sees the circle at the caster's feet and the mark on the ground, red where it can't strike. */
    @Override public void onChargeTick(SpellContext context, int chargedTicks) {
        if (chargedTicks % SpellTuning.CHARGE_EFFECT_INTERVAL != 0) return;
        ServerPlayer caster = context.caster();
        Aim aim = aim(context.level(), caster);
        Vec3 at = aim.point != null ? aim.point : caster.position();
        Vec3 valid = aim.problem == null ? new Vec3(0, 1, 0) : Vec3.ZERO;
        SpellEffects.send(context.level(), ModSpellEffects.JUDGMENT_CHANNEL, at, valid, context.power(), chargedTicks, caster.getId());
    }

    @Override public boolean cast(SpellContext context) {
        ServerLevel level = context.level();
        ServerPlayer caster = context.caster();
        if (context.power() < 1) {
            caster.displayClientMessage(Component.translatable("message.arcane_spells.judgment_unfinished"), true);
            return false;
        }
        Aim aim = aim(level, caster);
        if (aim.problem != null) {
            caster.displayClientMessage(aim.problem, true);
            return false;
        }
        if (ACTIVE.size() >= SpellTuning.MAX_ACTIVE_JUDGMENTS) {
            level.playSound(null, caster.blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        ACTIVE.add(new JudgmentStrike(level, caster, aim.point, nextSourceId--));
        return true;
    }

    /** Where the caster is aiming: the first surface (water included) within range, or why it can't be struck. */
    private static Aim aim(ServerLevel level, ServerPlayer caster) {
        Vec3 eye = caster.getEyePosition();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(SpellTuning.JUDGMENT_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
        if (hit.getType() == HitResult.Type.MISS) return new Aim(null, Component.translatable("message.arcane_spells.judgment_no_target"));
        Vec3 point = hit.getLocation();
        double dx = point.x - caster.getX(), dz = point.z - caster.getZ();
        if (dx * dx + dz * dz < SpellTuning.JUDGMENT_MIN_DISTANCE * SpellTuning.JUDGMENT_MIN_DISTANCE) {
            return new Aim(point, Component.translatable("message.arcane_spells.judgment_too_close"));
        }
        return new Aim(point, null);
    }

    /** {@code point} is null when nothing is in reach; {@code problem} is null when the point can be struck. */
    private record Aim(Vec3 point, Component problem) {
    }
}
