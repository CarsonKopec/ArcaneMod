package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Astral Gate, an Astral Staff spell: the caster marks a place up to {@link SpellTuning#GATE_RANGE} blocks away, or
 * looks straight up to go home (to their respawn point, even in another dimension), and gates open at both ends. The
 * caster steps through at once; for {@link SpellTuning#GATE_OPEN_TICKS} ticks the gates stay open both ways, and any
 * creature or item that steps into either one comes out of the other. Owns every open gate.
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

    private static boolean lookingHome(ServerPlayer caster) {
        return caster.getXRot() <= -SpellTuning.GATE_HOME_PITCH;
    }

    /**
     * Somewhere to stand at the marked spot: solid ground with room for the caster above it. Looking straight up, the
     * gate goes home, and the ring marks the caster's own feet.
     */
    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        if (lookingHome(caster)) return Aim.at(caster.position());
        BlockHitResult hit = surface(level, caster, SpellTuning.GATE_RANGE);
        if (hit.getType() == HitResult.Type.MISS) return Aim.refused(null, "message.arcane_spells.no_target");
        Vec3 landing = landing(level, caster, hit.getBlockPos().relative(hit.getDirection()));
        if (landing == null) return Aim.refused(hit.getLocation(), "message.arcane_spells.gate_no_room");
        return tooClose(caster, level, landing) ? Aim.refused(landing, "message.arcane_spells.too_close") : Aim.at(landing);
    }

    private static boolean tooClose(ServerPlayer caster, ServerLevel level, Vec3 to) {
        double dx = to.x - caster.getX(), dz = to.z - caster.getZ();
        return caster.level() == level && dx * dx + dz * dz < SpellTuning.GATE_MIN_DISTANCE * SpellTuning.GATE_MIN_DISTANCE;
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
        ServerPlayer caster = context.caster();
        if (GATES.size() >= SpellTuning.MAX_ACTIVE_GATES) {
            context.level().playSound(null, caster.blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        End origin = new End(context.level(), caster.position());
        End destination;
        if (lookingHome(caster)) {
            // Where they'd respawn: their bed or anchor if it's still there (an anchor keeps its charge), else world spawn.
            TeleportTransition home = caster.findRespawnPositionAndUseSpawnBlock(false, TeleportTransition.DO_NOTHING);
            if (tooClose(caster, home.newLevel(), home.position())) {
                caster.displayClientMessage(Component.translatable("message.arcane_spells.too_close"), true);
                return false;
            }
            destination = new End(home.newLevel(), home.position());
        } else {
            destination = new End(context.level(), mark);
        }
        GATES.add(new Gate(caster, origin, destination, nextSourceId--));
        return true;
    }

    /** One end of a gate: where it stands, in which world. */
    private record End(ServerLevel level, Vec3 position) {
    }

    private static final class Gate {
        final ServerPlayer caster;
        final End origin;
        final End destination;
        final int id;
        /** Who just came out of which end; they can't go back through until they've stepped out of it. */
        final Map<UUID, End> arrived = new HashMap<>();
        int age;

        Gate(ServerPlayer caster, End origin, End destination, int id) {
            this.caster = caster;
            this.origin = origin;
            this.destination = destination;
            this.id = id;
            if (origin.level == destination.level) {
                SpellEffects.send(origin.level, ModSpellEffects.ASTRAL_GATE, origin.position,
                        destination.position.subtract(origin.position), 1, SpellTuning.GATE_OPEN_TICKS, id);
            } else {
                // Its ends are in different worlds: each world sees one gate, facing the way the caster faced.
                Vec3 facing = caster.getLookAngle().multiply(1, 0, 1);
                for (End end : List.of(origin, destination)) {
                    SpellEffects.send(end.level, ModSpellEffects.ASTRAL_GATE, end.position, facing, 0, SpellTuning.GATE_OPEN_TICKS, id);
                }
            }
            for (End end : List.of(origin, destination)) sound(end, ModSounds.GATE_OPEN, 2.0f);
        }

        /** Returns true once the gates have closed. */
        boolean tick() {
            age++;
            if (age == SpellTuning.GATE_CROSS_TICKS && caster.level() == origin.level && caster.isAlive()) {
                caster.stopRiding();
                cross(caster, origin, destination, 1);
            } else if (age > SpellTuning.GATE_CROSS_TICKS) {
                arrived.entrySet().removeIf(entry -> !standingIn(entry.getKey(), entry.getValue()));
                carry(origin, destination);
                carry(destination, origin);
            }
            return age >= SpellTuning.GATE_OPEN_TICKS;
        }

        /** Creatures and items standing in {@code from} go through to {@code to}, landing just around its centre. */
        private void carry(End from, End to) {
            double radius = SpellTuning.GATE_RADIUS;
            for (Entity entity : from.level.getEntities((Entity) null, new AABB(from.position, from.position).inflate(radius, 2, radius),
                    e -> !e.isSpectator() && !e.isPassenger() && arrived.get(e.getUUID()) == null
                            && (e instanceof LivingEntity || e instanceof ItemEntity) && inside(e, from))) {
                Vec3 offset = new Vec3(from.level.getRandom().nextDouble() - 0.5, 0, from.level.getRandom().nextDouble() - 0.5);
                Vec3 landing = to.position.add(offset);
                boolean fits = to.level.noCollision(entity, entity.getBoundingBox().move(landing.subtract(entity.position())));
                cross(entity, from, new End(to.level, fits ? landing : to.position), entity == caster ? 1 : 0.5f);
            }
        }

        private static boolean inside(Entity entity, End end) {
            double dx = entity.getX() - end.position.x, dz = entity.getZ() - end.position.z;
            return entity.level() == end.level && dx * dx + dz * dz <= SpellTuning.GATE_RADIUS * SpellTuning.GATE_RADIUS
                    && Math.abs(entity.getY() - end.position.y) <= 1.5;
        }

        /** Whether whoever came out of {@code end} is still standing in it (or near enough, so it doesn't flicker). */
        private static boolean standingIn(UUID id, End end) {
            Entity entity = end.level.getEntity(id);
            if (entity == null) return false;
            double reach = SpellTuning.GATE_RADIUS + 1;
            return entity.position().distanceToSqr(end.position) <= reach * reach;
        }

        private void cross(Entity entity, End from, End to, float weight) {
            Vec3 start = entity.position();
            Entity traveller = entity;
            if (from.level == to.level && entity instanceof ServerPlayer player) {
                player.teleportTo(to.level, to.position.x, to.position.y, to.position.z, Set.of(), player.getYRot(), player.getXRot(), true);
            } else if (from.level == to.level) {
                entity.teleportTo(to.position.x, to.position.y, to.position.z);
            } else {
                // Another world: vanilla's own way through, which carries players and recreates anything else there.
                traveller = entity.teleport(new TeleportTransition(to.level, to.position, Vec3.ZERO, entity.getYRot(), entity.getXRot(),
                        TeleportTransition.DO_NOTHING));
                if (traveller == null) return;
            }
            traveller.resetFallDistance();
            if (traveller instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(MobEffects.SPEED, SpellTuning.GATE_WAKE_TICKS, SpellTuning.GATE_WAKE_AMPLIFIER));
            }
            arrived.put(traveller.getUUID(), to);
            if (from.level == to.level) {
                SpellEffects.send(from.level, ModSpellEffects.GATE_CROSSING, start, to.position.subtract(start), weight, 0, id);
            } else {
                SpellEffects.send(from.level, ModSpellEffects.GATE_CROSSING, start, Vec3.ZERO, weight, 0, id);
                SpellEffects.send(to.level, ModSpellEffects.GATE_CROSSING, to.position, Vec3.ZERO, weight, 0, id);
            }
            sound(from, ModSounds.GATE_CROSS, weight);
            sound(to, ModSounds.GATE_CROSS, weight);
        }

        private static void sound(End end, SoundEvent sound, float volume) {
            end.level.playSound(null, end.position.x, end.position.y, end.position.z, sound, SoundSource.PLAYERS, volume, 1.0f);
        }
    }
}
