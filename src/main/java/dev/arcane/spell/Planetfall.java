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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Planetfall, the Astral Staff's ultimate. The caster channels for three seconds while a ring marks the ground they aim
 * at; on release the sky tears open high above, gravity fails beneath it, and a burning planet falls out of the rift onto
 * the mark. See {@link PlanetfallStrike} for the strike itself. Owns every strike in progress.
 */
public final class Planetfall extends MarkedSpell {
    private static final List<PlanetfallStrike> ACTIVE = new ArrayList<>();
    // Negative, like bolts, so strike ids never collide with the entity ids channel effects use.
    private static int nextSourceId = -1;

    public Planetfall() {
        super(new Properties().manaCost(SpellTuning.PLANETFALL_MANA).cooldown(SpellTuning.PLANETFALL_COOLDOWN_TICKS)
                .charged(SpellTuning.PLANETFALL_CHARGE_TICKS).color(0xFFC86BFF), SpellTuning.PLANETFALL_CRATER_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> ACTIVE.removeIf(PlanetfallStrike::tick));
        // Ground torn loose floats with no gravity, and must never be saved that way.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            ACTIVE.forEach(PlanetfallStrike::stop);
            ACTIVE.clear();
        });
    }

    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        return ground(level, caster, SpellTuning.PLANETFALL_RANGE, SpellTuning.PLANETFALL_MIN_DISTANCE);
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (ACTIVE.size() >= SpellTuning.MAX_ACTIVE_PLANETFALLS) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        ServerPlayer caster = context.caster();
        Vec3 away = new Vec3(mark.x - caster.getX(), 0, mark.z - caster.getZ());
        away = away.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : away.normalize();
        Vec3 rift = away.scale(SpellTuning.PLANETFALL_RIFT_LEAD).add(0, SpellTuning.PLANETFALL_RIFT_HEIGHT, 0);
        ACTIVE.add(new PlanetfallStrike(context.level(), caster, mark, rift, nextSourceId--));
        return true;
    }

    /**
     * Where the planet's centre is {@code progress} (0 to 1) of the way through its fall: emerging from the rift at
     * {@code mark + rift}, then falling faster and faster until it comes to rest mostly buried in the mark. Shared by the
     * server, which crushes what the planet touches, and the client, which draws it.
     */
    public static Vec3 planetAt(Vec3 mark, Vec3 rift, float progress) {
        double p = Mth.clamp(progress, 0, 1);
        Vec3 start = mark.add(rift).add(0, SpellTuning.PLANETFALL_PLANET_RADIUS, 0);
        Vec3 end = mark.add(0, SpellTuning.PLANETFALL_PLANET_RADIUS * SpellTuning.PLANETFALL_SINK, 0);
        return start.lerp(end, p * p);
    }
}
