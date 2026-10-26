package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.terrain.Carving;
import dev.leyline.terrain.TerrainRules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
 * Singularity, an Astral Staff spell: a black hole hangs over the marked ground for ten seconds. It drags in creatures
 * (players under the server's PvP rules), dropped items, experience and projectiles, crushes whatever reaches its heart,
 * and devours the ground around it, nearest first: a few blocks a tick are torn loose and fly into it, the rest simply
 * vanish. Everything it eats makes it bigger, up to three times its first size, and its heart, where it crushes, grows
 * with it. Then it collapses in a blast that hurls everything outwards and leaves a crater, both as much bigger as it
 * grew. Owns every singularity.
 *
 * <p>Items and experience that reach its heart are held there, out of reach and unharmed, and those still loose when it
 * collapses are taken in too: the blast that would destroy them throws them all back out instead. Nothing it pulls
 * takes fall damage while caught; what it flings out at the end may.
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
        HOLES.add(new Hole(context.level(), context.caster(), mark.add(0, SpellTuning.SINGULARITY_LIFT, 0), context.spellPower(),
                nextSourceId--));
        return true;
    }

    private static final class Hole {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final int id;
        final boolean mayBuild;
        /** What its damage is multiplied by, fixed at the cast. */
        final float spellPower;
        /** The ground within reach, nearest first: the order it's eaten in. */
        final List<BlockPos> meal;
        /** Blocks it has torn loose, still flying in. */
        final List<FallingBlockEntity> debris = new ArrayList<>();
        /** Items and experience held at its heart until it collapses. */
        final Set<Entity> held = new HashSet<>();
        int eaten;
        int age;
        /** What it has eaten, which it grows by: see {@link SpellTuning#SINGULARITY_GROWTH_MASS}. */
        float mass;
        /** How many times its first size it is, from 1 towards {@link SpellTuning#SINGULARITY_MAX_GROWTH}. */
        float growth = 1;
        /** The size clients were last told of. */
        float told = 1;

        Hole(ServerLevel level, ServerPlayer caster, Vec3 center, float spellPower, int id) {
            this.level = level;
            this.caster = caster;
            this.center = center;
            this.spellPower = spellPower;
            this.id = id;
            this.mayBuild = SpellTerrain.mayBreak(level, caster);
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
            if (age < SpellTuning.SINGULARITY_DURATION_TICKS) {
                tell();
                return false;
            }
            collapse();
            return true;
        }

        /** Eats {@code amount} of mass, and grows by it. */
        private void feed(float amount) {
            mass += amount;
            float fed = 1 - (float) Math.exp(-mass / SpellTuning.SINGULARITY_GROWTH_MASS);
            growth = 1 + (SpellTuning.SINGULARITY_MAX_GROWTH - 1) * fed;
        }

        /** Tells clients how big it has grown: as it grows, and once a second for anyone who has just come in range. */
        private void tell() {
            if (growth - told < 0.05f && age % 20 != 0) return;
            told = growth;
            SpellEffects.send(level, ModSpellEffects.SINGULARITY_GROWTH, center, Vec3.ZERO, growth, age, id);
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
                if (held.contains(entity)) {
                    entity.setPos(center.x, center.y - entity.getBbHeight() / 2, center.z);
                    continue;
                }
                Vec3 toward = center.subtract(entity.getBoundingBox().getCenter());
                double distance = toward.length();
                if (distance > radius) continue;
                if (distance < SWALLOW_RADIUS * growth) {
                    if (entity instanceof FallingBlockEntity) {
                        entity.discard();
                        continue;
                    }
                    if (hold(entity)) continue;
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

        /**
         * Holds an item or experience orb at its heart; returns false for anything else. It is pinned there afresh each
         * tick rather than changed, so nothing about it is saved if its chunk unloads mid-spell.
         */
        private boolean hold(Entity entity) {
            if (!(entity instanceof ItemEntity || entity instanceof ExperienceOrb)) return false;
            entity.setDeltaMovement(Vec3.ZERO);
            entity.setPos(center.x, center.y - entity.getBbHeight() / 2, center.z);
            if (held.add(entity)) feed(1);
            return true;
        }

        /** Throws everything it held back out, once the blast that would have destroyed it is over. */
        private void release() {
            for (Entity entity : held) {
                if (entity.isRemoved()) continue;
                entity.setInvulnerable(false);
                if (entity instanceof ItemEntity item) item.setPickUpDelay(SpellTuning.SINGULARITY_RELEASE_PICKUP_DELAY);
                double angle = level.random.nextDouble() * Mth.TWO_PI;
                double speed = 0.4 + 0.5 * level.random.nextDouble();
                entity.setDeltaMovement(Math.cos(angle) * speed, 0.35 + 0.35 * level.random.nextDouble(), Math.sin(angle) * speed);
                entity.hurtMarked = true;
            }
            held.clear();
        }

        /** Crushes what has reached its heart; what dies there feeds it. */
        private void crush() {
            double radius = SpellTuning.SINGULARITY_CRUSH_RADIUS * growth;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius), this::caught)) {
                if (victim.getBoundingBox().getCenter().distanceTo(center) <= radius
                        && victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster), SpellTuning.SINGULARITY_CRUSH_DAMAGE * spellPower)
                        && victim.isDeadOrDying()) {
                    feed(victim.getMaxHealth());
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
                if (!TerrainRules.mayVaporise(level, caster, mayBuild, pos, state)) continue;
                if (vaporised < SpellTuning.SINGULARITY_DEBRIS_PER_TICK && !state.hasBlockEntity() && state.getFluidState().isEmpty()) {
                    tearLoose(pos, state);
                } else {
                    TerrainRules.vaporise(level, pos);
                }
                vaporised++;
            }
            if (vaporised > 0) feed(vaporised);
            debris.removeIf(Entity::isRemoved);
        }

        private void tearLoose(BlockPos pos, BlockState state) {
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            block.disableDrop();
            block.setNoGravity(true);
            block.setDeltaMovement(center.subtract(pos.getCenter()).normalize().scale(0.3).add(0, 0.2, 0));
            debris.add(block);
        }

        /**
         * Collapses, as much bigger as it grew: its blast reaches as many times further as it grew, and hits, throws,
         * breaks and carves (1 + growth) / 2 times as hard.
         */
        private void collapse() {
            // Whatever it hadn't swallowed yet goes with it.
            debris.forEach(Entity::discard);
            debris.clear();
            float size = growth, force = (1 + size) / 2;
            SpellEffects.send(level, ModSpellEffects.SINGULARITY_COLLAPSE, center, Vec3.ZERO, size, 0, id);
            // Bigger sounds deeper, and carries further.
            level.playSound(null, center.x, center.y, center.z, ModSounds.SINGULARITY_COLLAPSE, SoundSource.PLAYERS, 8.0f * size,
                    1 / (float) Math.sqrt(size));
            double radius = SpellTuning.SINGULARITY_COLLAPSE_RADIUS * size;
            for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), this::caught)) {
                Vec3 at = entity.getBoundingBox().getCenter();
                double distance = at.distanceTo(center);
                if (distance > radius || held.contains(entity)) continue;
                // Loose items and experience are taken in, so the blast throws them out rather than destroying them.
                if (hold(entity)) continue;
                float falloff = (float) (1 - distance / radius);
                if (entity instanceof LivingEntity victim
                        && !victim.hurtServer(level, level.damageSources().explosion(caster, caster),
                                SpellTuning.SINGULARITY_COLLAPSE_DAMAGE * falloff * spellPower * force)) {
                    continue;
                }
                Vec3 away = at.subtract(center);
                away = away.lengthSqr() < 1.0e-6 ? new Vec3(0, 1, 0) : away.normalize();
                double push = SpellTuning.SINGULARITY_COLLAPSE_KNOCKBACK * force * Mth.clamp(falloff, 0.3f, 1);
                entity.setDeltaMovement(away.x * push, Math.min(1.2, (0.5 + 0.6 * falloff) * force), away.z * push);
                entity.hurtMarked = true;
            }
            // Entities were thrown first, so the blast's terrain half can't add a second shove. What it held sits out the
            // blast, for this tick only.
            for (Entity entity : held) entity.setInvulnerable(true);
            level.explode(caster, level.damageSources().explosion(caster, caster), TerrainRules.explosion(level, caster, mayBuild),
                    center, SpellTuning.SINGULARITY_COLLAPSE_TERRAIN_STRENGTH * force, false, Level.ExplosionInteraction.BLOCK);
            // And a crater in the ground it hung over, beyond the hollow it ate.
            Carving.start(level, caster, mayBuild, center.subtract(0, SpellTuning.SINGULARITY_LIFT, 0),
                    SpellTuning.SINGULARITY_CRATER_RADIUS * force, SpellTuning.SINGULARITY_CRATER_DEPTH * force,
                    SpellTuning.SINGULARITY_CARVE_TICKS, false);
            release();
        }
    }
}
