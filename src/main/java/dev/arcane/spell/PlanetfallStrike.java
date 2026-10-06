package dev.arcane.spell;

import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModDamageTypes;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * One Planetfall, simulated on the server. Clients are sent a message at each phase and draw the rest themselves:
 * <ol>
 *   <li>Rift ({@link SpellTuning#PLANETFALL_RIFT_TICKS}): the sky tears open high above the mark, and gravity fails
 *       beneath it: creatures float up, loose items drift, and the ground tears loose and rises.</li>
 *   <li>Fall ({@link SpellTuning#PLANETFALL_FALL_TICKS}): a planet emerges from the rift and falls, faster and faster,
 *       onto the mark ({@link Planetfall#planetAt}), crushing anything it touches and ploughing through whatever stands
 *       above the ground in its way. In the second half of its fall burning shards break off its face and streak down,
 *       each cratering the ground where it lands within the lift radius.</li>
 *   <li>Impact: everything near is hurled outwards, gravity returns, the floating ground is flung away, and a crater is
 *       carved outwards over {@link SpellTuning#PLANETFALL_CARVE_TICKS} ticks while a rim of rubble rises around it.
 *       The crater's walls are left scorched and strange, and the planet's glowing core lies at its bottom.</li>
 * </ol>
 *
 * <p>Terrain follows the bolt's rules, read once at the cast: blocks only break, and rubble only lands, where the
 * caster could build, and anything unbreakable or as blast resistant as obsidian survives. Broken blocks are vaporised;
 * ground torn loose never lands or drops. The work is spread over many ticks.
 */
final class PlanetfallStrike {
    private static final int FALL_AT = SpellTuning.PLANETFALL_RIFT_TICKS;
    private static final int IMPACT_AT = FALL_AT + SpellTuning.PLANETFALL_FALL_TICKS;
    private static final int END = IMPACT_AT + SpellTuning.PLANETFALL_CARVE_TICKS;
    /** Ground stops tearing loose this long before the impact, so the last of it has time to rise. */
    private static final int LIFT_STOP = IMPACT_AT - 20;
    /** Levitation is renewed every few ticks, so it lapses soon after a creature leaves the zone. */
    private static final int LEVITATION_TICKS = 15;
    private static final int LEVITATION_RENEW = 5;
    private static final double R = SpellTuning.PLANETFALL_PLANET_RADIUS;
    /** Negative, so shard ids never collide with entity ids; clients track shards apart from strikes. */
    private static int nextShardId = -1;

    private final ServerLevel level;
    private final ServerPlayer caster;
    private final Vec3 mark;
    private final Vec3 rift;
    private final int sourceId;
    /** Shared with every client, so they all draw the same planet. */
    private final int seed;
    /** Read once at the cast, like the bolt: switching to Adventure mid-strike doesn't stop it. */
    private final boolean mayBuild;
    private final RandomSource random;
    private final DamageSource damage;
    private final List<FallingBlockEntity> floating = new ArrayList<>();
    private final Set<LivingEntity> lifted = new HashSet<>();
    private final Set<Integer> struck = new HashSet<>();
    private Vec3 planet;
    private int age;
    private PlanetfallCrater crater;
    private boolean terrainComplete;
    private final ArrayDeque<PloughSweep> sweeps = new ArrayDeque<>();
    private final List<Shard> shards = new ArrayList<>();

    PlanetfallStrike(ServerLevel level, ServerPlayer caster, Vec3 mark, Vec3 rift, int sourceId) {
        this.level = level;
        this.caster = caster;
        this.mark = mark;
        this.rift = rift;
        this.sourceId = sourceId;
        this.mayBuild = SpellTerrain.mayBreak(level, caster);
        this.random = level.getRandom();
        this.seed = random.nextInt(1 << 16);
        this.damage = ModDamageTypes.source(level, ModDamageTypes.PLANETFALL, caster);
        this.planet = Planetfall.planetAt(mark, rift, 0);
        this.crater = mayBuild ? new PlanetfallCrater(level, caster, mark, seed) : null;
        this.terrainComplete = !mayBuild;
        SpellEffects.send(level, ModSpellEffects.PLANETFALL_RIFT, mark, rift, seed, SpellTuning.PLANETFALL_RIFT_TICKS, sourceId);
        level.playSound(null, mark.x, mark.y, mark.z, ModSounds.PLANETFALL_RIFT, SoundSource.PLAYERS, 12.0f, 1.0f);
    }

    /** Advances the strike one tick. Returns true once it's over. */
    boolean tick() {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(mark.x), SectionPos.blockToSectionCoord(mark.z))) {
            stop();
            return true;
        }
        age++;
        if (age < IMPACT_AT) {
            if (crater != null) crater.prepare();
            lift();
            if (age < LIFT_STOP) tearLoose();
            if (age == FALL_AT) startFall();
            if (age > FALL_AT) fall();
        } else if (age == IMPACT_AT) {
            impact();
            carve();
        } else {
            int sinceImpact = age - IMPACT_AT;
            if (sinceImpact <= SpellTuning.PLANETFALL_PRESSURE_TICKS) blast(PlanetfallTerrain.pressureRadius(sinceImpact));
            else struck.clear();
            carve();
        }
        shards.removeIf(this::land);
        return age >= Math.max(END, IMPACT_AT + SpellTuning.PLANETFALL_PRESSURE_TICKS) && terrainComplete && sweeps.isEmpty()
                && shards.isEmpty();
    }

    /** Ends the strike early: nothing it tore loose is left floating. */
    void stop() {
        floating.forEach(Entity::discard);
        floating.clear();
        lifted.clear();
        sweeps.clear();
        struck.clear();
        shards.clear();
    }

    /** Within the lift radius, creatures float, and loose items and experience drift upwards. */
    private void lift() {
        double radius = SpellTuning.PLANETFALL_LIFT_RADIUS;
        AABB zone = new AABB(mark.x - radius, mark.y - 8, mark.z - radius, mark.x + radius, mark.y + 48, mark.z + radius);
        for (Entity entity : level.getEntities((Entity) null, zone, this::liftable)) {
            if (Mth.square(entity.getX() - mark.x) + Mth.square(entity.getZ() - mark.z) > radius * radius) continue;
            if (entity instanceof LivingEntity living) {
                if (lifted.add(living) || age % LEVITATION_RENEW == 0) {
                    living.addEffect(new MobEffectInstance(MobEffects.LEVITATION, LEVITATION_TICKS, 0, true, false));
                }
            } else {
                Vec3 velocity = entity.getDeltaMovement();
                entity.setDeltaMovement(velocity.x * 0.9, Math.max(velocity.y, 0.06), velocity.z * 0.9);
            }
        }
    }

    /** Creatures it may hurt (never anyone in creative), loose items and experience. */
    private boolean liftable(Entity entity) {
        if (entity instanceof Player player && player.isCreative()) return false;
        if (entity instanceof LivingEntity living) return SpellTargets.canHarm(caster, living);
        return entity instanceof ItemEntity || entity instanceof ExperienceOrb;
    }

    /** Tears a few blocks of ground loose each tick, from anywhere in the lift radius, to float slowly upwards. */
    private void tearLoose() {
        if (!mayBuild) return;
        double radius = SpellTuning.PLANETFALL_LIFT_RADIUS;
        for (int i = 0; i < SpellTuning.PLANETFALL_LIFT_PER_TICK && floating.size() < SpellTuning.PLANETFALL_LIFTED_BLOCKS; i++) {
            double angle = random.nextDouble() * Mth.TWO_PI, distance = Math.sqrt(random.nextDouble()) * radius;
            int x = Mth.floor(mark.x + Math.cos(angle) * distance), z = Mth.floor(mark.z + Math.sin(angle) * distance);
            if (!available(new BlockPos(x, Mth.clamp(Mth.floor(mark.y), level.getMinY(), level.getMaxY()), z))) continue;
            BlockPos pos = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
            if (!available(pos) || pos.getY() > mark.y + 32 || pos.getY() < mark.y - 12) continue;
            BlockState state = level.getBlockState(pos);
            if (state.hasBlockEntity() || !state.getFluidState().isEmpty() || !SpellTerrain.mayVaporise(level, caster, mayBuild, pos, state)) continue;
            FallingBlockEntity block = FallingBlockEntity.fall(level, pos, state);
            // Never lands as a block, and never drops one.
            block.disableDrop();
            block.dropItem = false;
            block.setNoGravity(true);
            block.setDeltaMovement((random.nextDouble() - 0.5) * 0.06, 0.05 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.06);
            floating.add(block);
        }
        floating.removeIf(Entity::isRemoved);
    }

    private void startFall() {
        SpellEffects.send(level, ModSpellEffects.PLANETFALL_FALL, mark, rift, seed, SpellTuning.PLANETFALL_FALL_TICKS, sourceId);
        level.playSound(null, mark.x, mark.y, mark.z, ModSounds.PLANETFALL_DESCENT, SoundSource.PLAYERS, 16.0f, 1.0f);
    }

    private void fall() {
        float progress = (age - FALL_AT) / (float) SpellTuning.PLANETFALL_FALL_TICKS;
        Vec3 next = Planetfall.planetAt(mark, rift, progress);
        crush(next);
        plough(planet, next);
        planet = next;
        if (progress >= SpellTuning.PLANETFALL_SHED_FROM && (age - FALL_AT) % SpellTuning.PLANETFALL_SHED_INTERVAL_TICKS == 0) shed();
    }

    /** A burning shard breaks off the planet's face, aimed at a random spot on the ground below. */
    private void shed() {
        double angle = random.nextDouble() * Mth.TWO_PI;
        double distance = SpellTuning.PLANETFALL_SHARD_MIN_DISTANCE
                + random.nextDouble() * (SpellTuning.PLANETFALL_LIFT_RADIUS - SpellTuning.PLANETFALL_SHARD_MIN_DISTANCE);
        int x = Mth.floor(mark.x + Math.cos(angle) * distance), z = Mth.floor(mark.z + Math.sin(angle) * distance);
        if (!available(new BlockPos(x, Mth.clamp(Mth.floor(mark.y), level.getMinY(), level.getMaxY()), z))) return;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        // A roof far above (a cave, the Nether) or a drop far below is no place to aim.
        if (ground > mark.y + R || ground < mark.y - 24) return;
        Vec3 target = new Vec3(x + 0.5, ground, z + 0.5);
        Vec3 from = planet.add(target.subtract(planet).normalize().scale(R));
        int flight = Mth.clamp(Mth.ceil(from.distanceTo(target) / SpellTuning.PLANETFALL_SHARD_SPEED), 6, 30);
        shards.add(new Shard(target, age + flight));
        // Played where it will strike, so its shriek grows as it comes.
        level.playSound(null, target.x, target.y, target.z, ModSounds.PLANETFALL_SHARD, SoundSource.PLAYERS, 3.0f,
                0.85f + random.nextFloat() * 0.3f);
        SpellEffects.send(level, ModSpellEffects.PLANETFALL_SHARD, from, target.subtract(from), 0.7f + random.nextFloat() * 0.6f,
                flight, nextShardId--);
    }

    /** A shard strikes when its time comes: a blast that burns and throws, and a charged creeper's crater. */
    private boolean land(Shard shard) {
        if (age < shard.landsAt) return false;
        Vec3 at = shard.target;
        level.playSound(null, at.x, at.y, at.z, ModSounds.METEOR_IMPACT, SoundSource.PLAYERS, 4.0f, 0.7f + random.nextFloat() * 0.2f);
        double radius = SpellTuning.PLANETFALL_SHARD_BLAST_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> SpellTargets.canHarm(caster, e))) {
            Vec3 center = victim.getBoundingBox().getCenter();
            double distance = center.distanceTo(at);
            if (distance > radius) continue;
            float falloff = (float) (1 - distance / radius);
            if (victim.hurtServer(level, damage, SpellTuning.PLANETFALL_SHARD_DAMAGE * falloff)) {
                victim.igniteForSeconds(4);
                Vec3 away = center.subtract(at).normalize().scale(falloff * 1.2);
                victim.push(away.x, 0.3 + falloff * 0.5, away.z);
            }
        }
        level.explode(caster, damage, SpellTerrain.explosion(level, caster, mayBuild), at.add(0, 0.5, 0),
                SpellTuning.PLANETFALL_SHARD_TERRAIN_STRENGTH, false, Level.ExplosionInteraction.BLOCK);
        return true;
    }

    /** Anything the planet touches. */
    private void crush(Vec3 center) {
        double reach = R + 1.5;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach),
                e -> SpellTargets.canHarm(caster, e))) {
            if (victim.getBoundingBox().getCenter().distanceTo(center) <= reach) victim.hurtServer(level, damage, SpellTuning.PLANETFALL_CRUSH_DAMAGE);
        }
    }

    /**
     * Vaporises what the planet passes through above the ground (trees, hills, buildings in its path): every block it
     * has just entered. Below the mark the crater does the work, more gradually.
     */
    private void plough(Vec3 from, Vec3 to) {
        if (!mayBuild) return;
        if (to.y - R <= level.getMaxY()) sweeps.addLast(new PloughSweep(from, to));
        drainPlough();
    }

    private void impact() {
        // No sound here: each client plays the boom itself when the shock front reaches it, after the flash.
        SpellEffects.send(level, ModSpellEffects.PLANETFALL_IMPACT, mark, rift, seed, 0, sourceId);
        // Gravity returns: our own levitation (ambient and hidden) lapses at once.
        for (LivingEntity living : lifted) {
            MobEffectInstance levitation = living.getEffect(MobEffects.LEVITATION);
            if (levitation != null && levitation.isAmbient() && !levitation.isVisible()) living.removeEffect(MobEffects.LEVITATION);
        }
        lifted.clear();
        blast(PlanetfallTerrain.pressureRadius(0));
        fling();
    }

    /** Each creature is hit once when the outward pressure front reaches it, hardest near the contact point. */
    private void blast(double reach) {
        double radius = SpellTuning.PLANETFALL_IMPACT_RADIUS;
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(mark, mark).inflate(reach),
                e -> SpellTargets.canHarm(caster, e))) {
            Vec3 at = victim.getBoundingBox().getCenter();
            double distance = at.distanceTo(mark);
            if (distance > reach || !struck.add(victim.getId())) continue;
            float falloff = (float) (1 - distance / radius);
            if (victim.hurtServer(level, damage, SpellTuning.PLANETFALL_IMPACT_DAMAGE * falloff)) {
                Vec3 away = new Vec3(at.x - mark.x, 0, at.z - mark.z);
                away = away.lengthSqr() < 1.0e-6 ? Vec3.ZERO : away.normalize();
                double push = SpellTuning.PLANETFALL_IMPACT_KNOCKBACK * falloff;
                victim.push(away.x * push, 0.6 + 1.2 * falloff, away.z * push);
            }
        }
    }

    /** The floating ground falls again, flung outwards; it vanishes where it lands. */
    private void fling() {
        for (FallingBlockEntity block : floating) {
            if (block.isRemoved()) continue;
            Vec3 away = new Vec3(block.getX() - mark.x, 0, block.getZ() - mark.z);
            away = away.lengthSqr() < 1.0e-6 ? new Vec3(1, 0, 0) : away.normalize();
            block.setNoGravity(false);
            block.setDeltaMovement(away.scale(1.4 + random.nextDouble() * 1.6).add(0, 0.9 + random.nextDouble() * 1.0, 0));
        }
        floating.clear();
    }

    private void carve() {
        drainPlough();
        if (!terrainComplete && crater != null) terrainComplete = crater.tick(age - IMPACT_AT);
    }

    /** Descent also has a hard budget; any remaining sweep is drained alongside the impact excavation. */
    private void drainPlough() {
        int checked = 0, columns = 0;
        while (!sweeps.isEmpty() && checked < 1536 && columns < 1024) {
            PloughSweep sweep = sweeps.getFirst();
            if (sweep.finished()) {
                sweeps.removeFirst();
            } else if (!sweep.hasColumn) {
                sweep.nextColumn();
                columns++;
            } else {
                sweep.clearNext();
                checked++;
            }
        }
    }

    /** Bounds and loading are checked before heightmap reads and immediate block-update neighbor probes. */
    private boolean available(BlockPos pos) {
        return pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()
                && level.getWorldBorder().isWithinBounds(pos)
                && PlanetfallCrater.loadedForUpdate(level, pos.getX(), pos.getZ());
    }

    /** A shard on its way down: where it lands, and on which tick. */
    private record Shard(Vec3 target, int landsAt) {
    }

    private final class PloughSweep {
        private final Vec3 from, to;
        private final int minX, maxX, minZ, maxZ;
        private int x, z, y, bottom;
        private boolean hasColumn;

        PloughSweep(Vec3 from, Vec3 to) {
            this.from = from;
            this.to = to;
            this.minX = Mth.floor(to.x - R);
            this.maxX = Mth.floor(to.x + R);
            this.minZ = Mth.floor(to.z - R);
            this.maxZ = Mth.floor(to.z + R);
            this.x = minX - 1;
            this.z = minZ;
        }

        boolean finished() {
            return z > maxZ;
        }

        void nextColumn() {
            if (++x > maxX) {
                x = minX;
                z++;
            }
            if (finished()) return;
            double horizontal = Mth.square(x + 0.5 - to.x) + Mth.square(z + 0.5 - to.z);
            if (horizontal > R * R) return;
            BlockPos anchor = new BlockPos(x, Mth.clamp(Mth.floor(mark.y), level.getMinY(), level.getMaxY()), z);
            if (!available(anchor)) return;
            double half = Math.sqrt(R * R - horizontal);
            bottom = Math.max(Math.max(level.getMinY(), Mth.floor(mark.y) + 1), Mth.ceil(to.y - half - 0.5));
            y = Math.min(Math.min(level.getMaxY(), Mth.floor(to.y + half - 0.5)),
                    level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
            hasColumn = y >= bottom;
        }

        void clearNext() {
            BlockPos pos = new BlockPos(x, y--, z);
            hasColumn = y >= bottom;
            if (!available(pos) || pos.getCenter().distanceToSqr(from) <= R * R) return;
            BlockState state = level.getBlockState(pos);
            if (SpellTerrain.mayVaporise(level, caster, mayBuild, pos, state)) SpellTerrain.vaporise(level, pos);
        }
    }
}
