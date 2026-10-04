package dev.arcane.spell;

import dev.arcane.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;

/** Owns every bolt in flight and advances them once per server tick. */
public final class SpellBolts {
    private static final List<SpellBolt> ACTIVE = new ArrayList<>();
    // Negative so bolt ids never collide with the entity ids that charge effects use.
    private static int nextSourceId = -1;

    private SpellBolts() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.removeIf(SpellBolt::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> ACTIVE.clear());
    }

    /** Returns false, and the spell audibly fizzles, when too many bolts are already in flight. */
    public static boolean cast(ServerLevel level, ServerPlayer caster, float power) {
        if (ACTIVE.size() >= SpellTuning.MAX_ACTIVE_BOLTS) {
            level.playSound(null, caster.blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        // Start at the eye: offsetting forward can skip a wall the player is touching.
        ACTIVE.add(new SpellBolt(level, caster, caster.getEyePosition(), caster.getLookAngle().normalize(), power, nextSourceId--));
        level.playSound(null, caster.blockPosition(), ModSounds.BOLT_CAST, SoundSource.PLAYERS, 0.8f, 1.0f);
        return true;
    }
}
