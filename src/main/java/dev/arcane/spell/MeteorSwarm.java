package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor Swarm, an Astral Staff spell: a burning sigil opens high over the marked ground and looses a meteor every few
 * ticks onto random points across it, then, after a breath, the Dread Star: one colossal meteor straight onto the mark.
 * Each meteor is an explosion: damage, fire and knockback, and terrain broken under the bolt's rules. Owns every swarm
 * and meteor in flight.
 *
 * <p>Clients are told when each meteor launches and where it lands; they fly it themselves in between.
 */
public final class MeteorSwarm extends MarkedSpell {
    private static final List<Swarm> SWARMS = new ArrayList<>();
    private static final List<Meteor> METEORS = new ArrayList<>();
    private static int nextSwarmId = -1;
    private static int nextMeteorId = -1;
    /** The pause between the last meteor and the Dread Star. */
    private static final int DREAD_STAR_PAUSE_TICKS = 12;

    public MeteorSwarm() {
        super(new Properties().manaCost(SpellTuning.METEOR_MANA).cooldown(SpellTuning.METEOR_COOLDOWN_TICKS)
                .charged(SpellTuning.METEOR_CHARGE_TICKS).color(0xFFFF7A33), SpellTuning.METEOR_AREA_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            SWARMS.removeIf(Swarm::tick);
            METEORS.removeIf(Meteor::tick);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SWARMS.clear();
            METEORS.clear();
        });
    }

    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        return ground(level, caster, SpellTuning.METEOR_RANGE, SpellTuning.METEOR_MIN_DISTANCE);
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (SWARMS.size() >= SpellTuning.MAX_ACTIVE_SWARMS) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        SWARMS.add(new Swarm(context.level(), context.caster(), mark, nextSwarmId--));
        return true;
    }

    private static boolean loaded(ServerLevel level, Vec3 at) {
        return level.hasChunk(SectionPos.blockToSectionCoord(at.x), SectionPos.blockToSectionCoord(at.z));
    }

    /** The sigil, launching the swarm's meteors one by one. */
    private static final class Swarm {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 mark;
        final Vec3 sky;
        /** Horizontal, from the caster towards the mark: meteors come in from behind the sigil, over the caster's head. */
        final Vec3 heading;
        /** Whether the caster could build when they cast it; every meteor follows that. */
        final boolean mayBuild;
        final RandomSource random;
        int age;
        int launched;
        boolean dreadStarLaunched;

        Swarm(ServerLevel level, ServerPlayer caster, Vec3 mark, int id) {
            this.level = level;
            this.caster = caster;
            this.mark = mark;
            this.sky = new Vec3(mark.x, Math.min(level.getMaxY() - 1, mark.y + SpellTuning.METEOR_SKY_HEIGHT), mark.z);
            Vec3 toward = new Vec3(mark.x - caster.getX(), 0, mark.z - caster.getZ());
            this.heading = toward.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : toward.normalize();
            this.mayBuild = SpellTerrain.mayBreak(level, caster);
            this.random = level.getRandom();
            double dreadFall = (sky.y - mark.y) / SpellTuning.DREAD_STAR_SPEED;
            int duration = SpellTuning.METEOR_COUNT * SpellTuning.METEOR_INTERVAL_TICKS + DREAD_STAR_PAUSE_TICKS + (int) dreadFall + 10;
            SpellEffects.send(level, ModSpellEffects.METEOR_SWARM, mark, sky.subtract(mark), (float) SpellTuning.METEOR_AREA_RADIUS, duration, id);
        }

        /** Returns true once every meteor, the Dread Star last, has been launched. */
        boolean tick() {
            if (!loaded(level, mark)) return true;
            if (launched < SpellTuning.METEOR_COUNT) {
                if (age % SpellTuning.METEOR_INTERVAL_TICKS == 0) {
                    launch();
                    launched++;
                }
            } else if (age >= SpellTuning.METEOR_COUNT * SpellTuning.METEOR_INTERVAL_TICKS + DREAD_STAR_PAUSE_TICKS) {
                // Straight down out of the heart of the sigil, onto the mark itself.
                Vec3 start = sky.subtract(0, 1, 0);
                Vec3 velocity = mark.subtract(start).normalize().scale(SpellTuning.DREAD_STAR_SPEED);
                METEORS.add(new Meteor(level, caster, mayBuild, start, velocity, nextMeteorId--, true));
                dreadStarLaunched = true;
            }
            age++;
            return dreadStarLaunched;
        }

        private void launch() {
            double angle = random.nextDouble() * Mth.TWO_PI, distance = Math.sqrt(random.nextDouble()) * SpellTuning.METEOR_AREA_RADIUS;
            Vec3 target = mark.add(Math.cos(angle) * distance, 0, Math.sin(angle) * distance);
            Vec3 start = sky.add((random.nextDouble() - 0.5) * 16, random.nextDouble() * 6, (random.nextDouble() - 0.5) * 16)
                    .subtract(heading.scale(12));
            Vec3 velocity = target.subtract(start).normalize().scale(SpellTuning.METEOR_SPEED);
            METEORS.add(new Meteor(level, caster, mayBuild, start, velocity, nextMeteorId--, false));
        }
    }

    /**
     * One meteor, swept along its path each tick like a bolt, so it can't tunnel through anything. The Dread Star is a
     * meteor too, only far bigger, slower and deadlier.
     */
    private static final class Meteor {
        final ServerLevel level;
        final ServerPlayer caster;
        final boolean mayBuild;
        final Vec3 velocity;
        final int id;
        /** How large it looks, sent to clients: 1 for a meteor. */
        final float size;
        final double blastRadius;
        final float damage;
        final float terrainStrength;
        Vec3 position;
        int age;

        Meteor(ServerLevel level, ServerPlayer caster, boolean mayBuild, Vec3 start, Vec3 velocity, int id, boolean dreadStar) {
            this.level = level;
            this.caster = caster;
            this.mayBuild = mayBuild;
            this.position = start;
            this.velocity = velocity;
            this.id = id;
            this.size = dreadStar ? SpellTuning.DREAD_STAR_SIZE : 1;
            this.blastRadius = dreadStar ? SpellTuning.DREAD_STAR_BLAST_RADIUS : SpellTuning.METEOR_BLAST_RADIUS;
            this.damage = dreadStar ? SpellTuning.DREAD_STAR_DAMAGE : SpellTuning.METEOR_DAMAGE;
            this.terrainStrength = dreadStar ? SpellTuning.DREAD_STAR_TERRAIN_STRENGTH : SpellTuning.METEOR_TERRAIN_STRENGTH;
            SpellEffects.send(level, ModSpellEffects.METEOR, start, velocity, size, SpellTuning.METEOR_LIFETIME_TICKS, id);
            // Bigger sounds deeper, and carries further.
            level.playSound(null, start.x, start.y, start.z, ModSounds.METEOR_FALL, SoundSource.PLAYERS, 6.0f * size,
                    (0.85f + level.getRandom().nextFloat() * 0.3f) / (float) Math.sqrt(size));
        }

        /** Returns true once it has struck, or burnt out. */
        boolean tick() {
            if (++age > SpellTuning.METEOR_LIFETIME_TICKS || !loaded(level, position)) return true;
            Vec3 end = position.add(velocity);
            if (!loaded(level, end)) return true;
            BlockHitResult blockHit = level.clip(new ClipContext(position, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, caster));
            Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
            double nearest = position.distanceToSqr(stop);
            boolean struck = blockHit.getType() != HitResult.Type.MISS;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(position, end).inflate(size),
                    e -> e.isPickable() && SpellTargets.canTarget(caster, e))) {
                Vec3 hit = entity.getBoundingBox().inflate(0.5 * size).clip(position, end).orElse(null);
                if (hit != null && position.distanceToSqr(hit) < nearest) {
                    stop = hit;
                    nearest = position.distanceToSqr(hit);
                    struck = true;
                }
            }
            if (!struck) {
                position = end;
                return false;
            }
            position = stop;
            detonate();
            return true;
        }

        private void detonate() {
            SpellEffects.send(level, ModSpellEffects.METEOR_IMPACT, position, velocity, size, 0, id);
            level.playSound(null, position.x, position.y, position.z, ModSounds.METEOR_IMPACT, SoundSource.PLAYERS, 4.0f * size,
                    (0.85f + level.getRandom().nextFloat() * 0.3f) / (float) Math.sqrt(size));
            double radius = blastRadius;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(position, position).inflate(radius),
                    e -> SpellTargets.canHarm(caster, e))) {
                Vec3 at = victim.getBoundingBox().getCenter();
                double distance = at.distanceTo(position);
                if (distance > radius) continue;
                float falloff = (float) (1 - distance / radius);
                if (victim.hurtServer(level, level.damageSources().explosion(caster, caster), damage * falloff)) {
                    victim.igniteForSeconds(SpellTuning.METEOR_FIRE_SECONDS);
                    Vec3 push = at.subtract(position).normalize().scale(falloff * 1.2 * Math.sqrt(size));
                    victim.push(push.x, 0.3 + falloff * 0.5 * size, push.z);
                }
            }
            // Back the blast off the struck surface a little, so the wall doesn't hide every ray.
            Vec3 blast = position.subtract(velocity.normalize().scale(0.3));
            level.explode(caster, level.damageSources().explosion(caster, caster), SpellTerrain.explosion(level, caster, mayBuild),
                    blast, terrainStrength, false, Level.ExplosionInteraction.BLOCK);
        }
    }
}
