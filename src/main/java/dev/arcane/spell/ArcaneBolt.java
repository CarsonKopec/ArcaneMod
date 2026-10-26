package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.effect.Effects;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Arcane Bolt: held to charge, then fires an explosive bolt. Owns every bolt in flight and advances them once per
 * server tick.
 */
public final class ArcaneBolt extends Spell {
    private static final List<SpellBolt> ACTIVE = new ArrayList<>();
    // Negative so bolt ids never collide with the entity ids that charge effects use.
    private static int nextSourceId = -1;

    public ArcaneBolt() {
        super(new Properties().manaCost(SpellTuning.BOLT_MANA).cooldown(SpellTuning.BOLT_COOLDOWN_TICKS).charged().color(0xFFB46CFF));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.removeIf(SpellBolt::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    @Override public boolean cast(SpellContext context) {
        return launch(context.level(), context.caster(), context.power(), context.spellPower());
    }

    @Override public void onChargeStart(SpellContext context) {
        context.level().playSound(null, context.caster().blockPosition(), ModSounds.BOLT_CHARGE, SoundSource.PLAYERS, 0.6f, 1.0f);
    }

    /** A rune circle gathering in front of the caster, growing with the charge. */
    @Override public void onChargeTick(SpellContext context, int chargedTicks) {
        if (chargedTicks % SpellTuning.CHARGE_EFFECT_INTERVAL != 0) return;
        ServerPlayer caster = context.caster();
        Vec3 direction = caster.getLookAngle();
        Vec3 center = caster.getEyePosition().add(direction.scale(1.4));
        Effects.send(context.level(), ModSpellEffects.CHARGE, center, direction, context.power(), chargedTicks, caster.getId());
    }

    /**
     * Fires a bolt at the bolt's own damage, skipping mana and cooldown. Returns false, and the spell audibly fizzles,
     * when too many bolts are already in flight.
     */
    public static boolean launch(ServerLevel level, ServerPlayer caster, float power) {
        return launch(level, caster, power, 1);
    }

    /** Fires a bolt as {@link #launch(ServerLevel, ServerPlayer, float)} does, its damage multiplied by {@code spellPower}. */
    public static boolean launch(ServerLevel level, ServerPlayer caster, float power, float spellPower) {
        if (ACTIVE.size() >= SpellTuning.MAX_ACTIVE_BOLTS) {
            level.playSound(null, caster.blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        // Start at the eye: offsetting forward can skip a wall the player is touching.
        ACTIVE.add(new SpellBolt(level, caster, caster.getEyePosition(), caster.getLookAngle().normalize(), power, spellPower,
                nextSourceId--));
        level.playSound(null, caster.blockPosition(), ModSounds.BOLT_CAST, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }
}
