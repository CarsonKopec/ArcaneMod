package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Singularity, an Astral Staff spell: a black hole hangs over the marked ground for six seconds. It drags in creatures
 * (players under the server's PvP rules), dropped items, experience and projectiles, crushes whatever reaches its heart,
 * and devours the ground around it, nearest first: a few blocks a tick are torn loose and fly into it, the rest simply
 * vanish. Then it collapses in a blast that hurls everything outwards. Owns every singularity.
 *
 * <p>Nothing it pulls takes fall damage while caught; what it flings out at the end may.
 */
public final class Singularity extends MarkedSpell {
    private static final List<Hole> HOLES = new ArrayList<>();
    private static int nextSourceId = -1;
    /** The most blocks of ground looked at in a tick, eaten or not. */
    private static final int MAX_CHECKED_PER_TICK = 256;
    /** Torn-loose blocks this close to the heart are swallowed. */
    private static final double SWALLOW_RADIUS = 1.5;

    public Singularity() {
        super(new Properties().manaCost(SpellTuning.SINGULARITY_MANA).cooldown(SpellTuning.SINGULARITY_COOLDOWN_TICKS)
                .charged(SpellTuning.SINGULARITY_CHARGE_TICKS).color(0xFFA05CFF), SpellTuning.SINGULARITY_PULL_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> HOLES.removeIf(Hole::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> HOLES.clear());
    }

    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        return ground(level, caster, SpellTuning.SINGULARITY_RANGE, SpellTuning.SINGULARITY_MIN_DISTANCE);
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (HOLES.size() >= SpellTuning.MAX_ACTIVE_SINGULARITIES) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        HOLES.add(new Hole(context.level(), context.caster(), mark.add(0, SpellTuning.SINGULARITY_LIFT, 0), nextSourceId--));
        return true;
    }

    private static final class Hole {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final int id;
        final boolean mayBuild;
        /** The ground within reach, nearest first: the order it's eaten in. */
        final List<BlockPos> meal;
        /** Blocks it has torn loose, still flying in. */
        final List<FallingBlockEntity> debris = new ArrayList<>();
        int eaten;
        int age;

        Hole(ServerLevel level, ServerPlayer caster, Vec3 center, int id) {
            this.level = level;
            this.caster = caster;
            this.center = center;
            this.id = id;
            this.mayBuild = caster.mayBuild();
            this.meal = meal(center);
            SpellEffects.send(level, ModSpellEffects.SINGULARITY, center, Vec3.ZERO, (float) SpellTuning.SINGULARITY_PULL_RADIUS,
                    SpellTuning.SINGULARITY_DURATION_TICKS, id);
            level.playSound(null, center.x, center.y, center.z, ModSounds.SINGULARITY_OPEN, SoundSource.PLAYERS, 4.0f, 1.0f);
        }

        private static List<BlockPos> meal(Vec3 center) {
            double radius = SpellTuning.SINGULARITY_CONSUME_RADIUS;
            List<BlockPos> blocks = new ArrayList<>();
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(center.subtract(radius, radius, radius)),
                    BlockPos.containing(center.add(radius, radius, radius)))) {
                if (pos.getCenter().distanceToSqr(center) <= radius * radius) blocks.add(pos.immutable());
            }
            blocks.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(center)));
            return blocks;
        }

        /** Returns true once it has collapsed. */
        boolean tick() {
            if (!level.hasChunk(SectionPos.blockToSectionCoord(center.x), SectionPos.blockToSectionCoord(center.z))) return true;
            age++;
            pull();
            if (age % SpellTuning.SINGULARITY_CRUSH_INTERVAL == 0) crush();
            consume();
            if (age < SpellTuning.SINGULARITY_DURATION_TICKS) return false;
            collapse();
            return true;
        }

        /** Creatures it may hurt, loose items, experience and projectiles. Never the caster, nor anyone in creative. */
        private boolean caught(Entity entity) {
            if (entity == caster || entity.isSpectator() || entity instanceof Player player && player.isCreative()) return false;
            if (entity instanceof LivingEntity living) return SpellTargets.canHarm(caster, living);
            return entity instanceof ItemEntity || entity instanceof ExperienceOrb || entity instanceof Projectile
                    || entity instanceof FallingBlockEntity;
        }

        /** Everything caught accelerates towards the heart, harder the nearer it is; old speed bleeds away, so few orbit. */
        private void pull() {
            double radius = SpellTuning.SINGULARITY_PULL_RADIUS;
            for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), this::caught)) {
                Vec3 toward = center.subtract(entity.getBoundingBox().getCenter());
                double distance = toward.length();
                if (distance > radius) continue;
                if (entity instanceof FallingBlockEntity && distance < SWALLOW_RADIUS) {
                    entity.discard();
                    continue;
                }
                Vec3 direction = distance < 1.0e-3 ? Vec3.ZERO : toward.scale(1 / distance);
                double strength = SpellTuning.SINGULARITY_PULL * (1 + 2 * (1 - distance / radius));
                Vec3 velocity = entity.getDeltaMovement().scale(distance < 1 ? 0.5 : 0.92).add(direction.scale(strength));
                if (velocity.length() > 1.5) velocity = velocity.normalize().scale(1.5);
                entity.setDeltaMovement(velocity);
                // Sends the new speed to players, who otherwise move themselves.
                entity.hurtMarked = true;
                entity.resetFallDistance();
            }
        }

        private void crush() {
            double radius = SpellTuning.SINGULARITY_CRUSH_RADIUS;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::caught)) {
                if (victim.getBoundingBox().getCenter().distanceTo(center) <= radius) {
                    victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SpellTuning.SINGULARITY_CRUSH_DAMAGE);
                }
            }
        }

        /**
         * Eats blocks of ground each tick, nearest the heart first, under the same rules as Celestial Judgment. The first
         * few plain blocks each tick are torn loose and fly in, as falling blocks that never land; the rest vanish.
         */
        private void consume() {
            int vaporised = 0;
            for (int checked = 0; checked < MAX_CHECKED_PER_TICK && eaten < meal.size()
                    && vaporised < SpellTuning.SINGULARITY_CONSUME_PER_TICK; checked++) {
                BlockPos pos = meal.get(eaten++);
                BlockState state = level.getBlockState(pos);
                if (!SpellTerrain.mayVaporise(level, caster, mayBuild, pos, state)) continue;
                if (vaporised < SpellTuning.SINGULARITY_DEBRIS_PER_TICK && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
                    tearLoose(pos, state);
                } else {
                    SpellTerrain.vaporise(level, pos);
                }
                vaporised++;
            }
            debris.removeIf(Entity::isRemoved);
        }

        private void tearLoose(BlockPos pos, BlockState state) {
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.disableDrop();
            block.setNoGravity(true);
            block.setDeltaMovement(center.subtract(pos.getCenter()).normalize().scale(0.3).add(0, 0.2, 0));
            debris.add(block);
        }

        private void collapse() {
            // Whatever it hadn't swallowed yet goes with it.
            debris.forEach(Entity::discard);
            debris.clear();
            SpellEffects.send(level, ModSpellEffects.SINGULARITY_COLLAPSE, center, Vec3.ZERO, 1, 0, id);
            level.playSound(null, center.x, center.y, center.z, ModSounds.SINGULARITY_COLLAPSE, SoundSource.PLAYERS, 8.0f, 1.0f);
            double radius = SpellTuning.SINGULARITY_COLLAPSE_RADIUS;
            for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), this::caught)) {
                Vec3 at = entity.getBoundingBox().getCenter();
                double distance = at.distanceTo(center);
                if (distance > radius) continue;
                float falloff = (float) (1 - distance / radius);
                if (entity instanceof LivingEntity victim
                        && !victim.hurtServer(level, level.damageSources().explosion(caster, caster), SpellTuning.SINGULARITY_COLLAPSE_DAMAGE * falloff)) {
                    continue;
                }
                Vec3 away = at.subtract(center);
                away = away.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : away.normalize();
                double push = SpellTuning.SINGULARITY_COLLAPSE_KNOCKBACK * Mth.clamp(falloff, 0.3f, 1);
                entity.setDeltaMovement(away.x * push, 0.5 + 0.6 * falloff, away.z * push);
                entity.hurtMarked = true;
            }
            // Entities were thrown first, so the blast's terrain half can't add a second shove.
            level.explode(caster, level.damageSources().explosion(caster, caster), SpellTerrain.explosion(level, caster, mayBuild),
                    center, SpellTuning.SINGULARITY_COLLAPSE_TERRAIN_STRENGTH, false, Level.ExplosionInteraction.BLOCK);
        }
    }
}
