package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Astral Gate, an Astral Staff spell: the caster marks a place up to {@link SpellTuning#GATE_RANGE} blocks away, and
 * gates open at both ends. The caster steps through at once; for a few seconds more, any creature or item that steps
 * into the first gate follows them. Owns every open gate.
 */
public final class AstralGate extends MarkedSpell {
    private static final List<Gate> GATES = new ArrayList<>();
    private static int nextSourceId = -1;
    /** How far below the marked spot, then above it, to look for solid ground with room to stand. */
    private static final int SEARCH_DOWN = 6, SEARCH_UP = 3;

    public AstralGate() {
        super(new Properties().manaCost(SpellTuning.GATE_MANA).cooldown(SpellTuning.GATE_COOLDOWN_TICKS)
                .charged(SpellTuning.GATE_CHARGE_TICKS).color(0xFFFFB8F0), SpellTuning.GATE_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> GATES.removeIf(Gate::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> GATES.clear());
    }

    /** Somewhere to stand at the marked spot: solid ground with room for the caster above it. */
    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        BlockHitResult hit = surface(level, caster, SpellTuning.GATE_RANGE);
        if (hit.getType() == HitResult.Type.MISS) return Aim.refused(null, "message.arcane_spells.no_target");
        Vec3 landing = landing(level, caster, hit.getBlockPos().relative(hit.getDirection()));
        if (landing == null) return Aim.refused(hit.getLocation(), "message.arcane_spells.gate_no_room");
        double dx = landing.x - caster.getX(), dz = landing.z - caster.getZ();
        if (dx * dx + dz * dz < SpellTuning.GATE_MIN_DISTANCE * SpellTuning.GATE_MIN_DISTANCE) {
            return Aim.refused(landing, "message.arcane_spells.too_close");
        }
        return Aim.at(landing);
    }

    private static Vec3 landing(ServerLevel level, ServerPlayer caster, BlockPos start) {
        for (int i = 0; i <= SEARCH_DOWN + SEARCH_UP; i++) {
            BlockPos feet = i <= SEARCH_DOWN ? start.below(i) : start.above(i - SEARCH_DOWN);
            Vec3 at = feet.getBottomCenter();
            boolean ground = !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty();
            if (ground && level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())))) return at;
        }
        return null;
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (GATES.size() >= SpellTuning.MAX_ACTIVE_GATES) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        GATES.add(new Gate(context.level(), context.caster(), context.caster().position(), mark, nextSourceId--));
        return true;
    }

    private static final class Gate {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 origin;
        final Vec3 destination;
        final int id;
        final Set<UUID> carried = new HashSet<>();
        int age;

        Gate(ServerLevel level, ServerPlayer caster, Vec3 origin, Vec3 destination, int id) {
            this.level = level;
            this.caster = caster;
            this.origin = origin;
            this.destination = destination;
            this.id = id;
            SpellEffects.send(level, ModSpellEffects.ASTRAL_GATE, origin, destination.subtract(origin), 1, SpellTuning.GATE_OPEN_TICKS, id);
            for (Vec3 end : List.of(origin, destination)) {
                level.playSound(null, end.x, end.y, end.z, ModSounds.GATE_OPEN, SoundSource.PLAYERS, 2.0f, 1.0f);
            }
        }

        /** Returns true once the gates have closed. */
        boolean tick() {
            age++;
            if (age == SpellTuning.GATE_CROSS_TICKS && caster.level() == level && caster.isAlive()) {
                caster.stopRiding();
                cross(caster, destination, 1);
            } else if (age > SpellTuning.GATE_CROSS_TICKS) {
                carryFollowers();
            }
            return age >= SpellTuning.GATE_OPEN_TICKS;
        }

        /** Creatures and items standing in the first gate follow, landing just around where the caster did. */
        private void carryFollowers() {
            double radius = SpellTuning.GATE_RADIUS;
            for (Entity entity : level.getEntities((Entity) null, new AABB(origin, origin).inflate(radius, 2, radius),
                    e -> e != caster && !e.isSpectator() && !e.isPassenger() && !carried.contains(e.getUUID())
                            && (e instanceof LivingEntity || e instanceof ItemEntity))) {
                double dx = entity.getX() - origin.x, dz = entity.getZ() - origin.z;
                if (dx * dx + dz * dz > radius * radius || Math.abs(entity.getY() - origin.y) > 1.5) continue;
                Vec3 offset = new Vec3(level.getRandom().nextDouble() - 0.5, 0, level.getRandom().nextDouble() - 0.5);
                Vec3 to = destination.add(offset);
                boolean fits = level.noCollision(entity, entity.getBoundingBox().move(to.subtract(entity.position())));
                cross(entity, fits ? to : destination, 0.5f);
            }
        }

        private void cross(Entity entity, Vec3 to, float weight) {
            Vec3 from = entity.position();
            if (entity instanceof ServerPlayer player) {
                player.teleportTo(level, to.x, to.y, to.z, Set.of(), player.getYRot(), player.getXRot(), true);
            } else {
                entity.teleportTo(to.x, to.y, to.z);
            }
            entity.resetFallDistance();
            carried.add(entity.getUUID());
            SpellEffects.send(level, ModSpellEffects.GATE_CROSSING, from, to.subtract(from), weight, 0, id);
            for (Vec3 end : List.of(from, to)) {
                level.playSound(null, end.x, end.y, end.z, ModSounds.GATE_CROSS, SoundSource.PLAYERS, weight, 1.0f);
            }
        }
    }
}
