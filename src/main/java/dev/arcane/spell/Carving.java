package dev.arcane.spell;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Craters carved over a few ticks rather than all in one: a spell plans one ({@link Crater}) and hands it here, and each
 * tick the next share of its blocks vanishes, nearest the centre first, under the usual terrain rules. Blocks in chunks
 * that have unloaded are left alone. Owns every crater still being carved.
 */
public final class Carving {
    private static final List<Carving> CARVINGS = new ArrayList<>();

    private final ServerLevel level;
    private final ServerPlayer caster;
    private final boolean mayBuild;
    private final Crater crater;
    private final int perTick;
    private final boolean scorch;
    private int carved;

    private Carving(ServerLevel level, ServerPlayer caster, boolean mayBuild, Crater crater, int ticks, boolean scorch) {
        this.level = level;
        this.caster = caster;
        this.mayBuild = mayBuild;
        this.crater = crater;
        this.perTick = Math.max(1, Mth.ceil(crater.blocks.size() / (float) Math.max(1, ticks)));
        this.scorch = scorch;
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> CARVINGS.removeIf(Carving::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CARVINGS.clear());
    }

    /**
     * Carves a crater {@code radius} wide and {@code depth} deep around {@code at} ({@link Crater#plan}) over
     * {@code ticks} ticks, starting now, so one given a single tick is carved at once; then, if {@code scorch}, sears its
     * floor and walls. Where the caster may not break blocks, nothing is even planned.
     */
    static void start(ServerLevel level, ServerPlayer caster, boolean mayBuild, Vec3 at, double radius, double depth, int ticks,
                      boolean scorch) {
        if (!mayBuild) return;
        Crater crater = Crater.plan(level, at, radius, depth, level.getRandom());
        Carving carving = new Carving(level, caster, mayBuild, crater, ticks, scorch);
        if (!carving.tick()) CARVINGS.add(carving);
    }

    /** Returns true once it's done. */
    private boolean tick() {
        for (int i = 0; i < perTick && carved < crater.blocks.size(); i++) {
            BlockPos pos = crater.blocks.get(carved++);
            if (level.isLoaded(pos) && SpellTerrain.mayVaporise(level, caster, mayBuild, pos, level.getBlockState(pos))) {
                SpellTerrain.vaporise(level, pos);
            }
        }
        if (carved < crater.blocks.size()) return false;
        if (scorch) SpellTerrain.scorch(level, caster, mayBuild, crater.shell, level.getRandom());
        return true;
    }
}
