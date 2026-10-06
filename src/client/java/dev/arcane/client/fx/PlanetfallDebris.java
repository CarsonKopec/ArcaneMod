package dev.arcane.client.fx;

import com.mojang.math.Transformation;
import dev.arcane.registry.ModParticles;
import dev.arcane.spell.PlanetfallTerrain;
import dev.arcane.spell.SpellTuning;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Real-looking earth ejecta, drawn with client-only block displays. Ground is sampled at impact, before excavation
 * reaches it, then slabs tear free as the shared rupture front passes. The largest pieces are fitted clusters of
 * angular blocks, with their original surface material above the freshly exposed earth.
 */
final class PlanetfallDebris {
    private static final int MAX_AGE = 180;
    private static final Brightness EARTH_LIGHT = new Brightness(9, 12);
    private static final double GOLDEN_ANGLE = Math.PI * (3 - Math.sqrt(5));
    private static int nextId = -0x3000_0000;

    private final ClientLevel level;
    private final Vec3 mark;
    private final List<Slab> slabs = new ArrayList<>();
    private final int displayBudget;
    private int age, displays;

    /** Snapshots at most 88 loaded surface columns. No displays are created until {@link #tick()}. */
    PlanetfallDebris(ClientLevel level, int seed, Vec3 mark) {
        this.level = level;
        this.mark = mark;
        ParticleStatus quality = Minecraft.getInstance().options.particles().get();
        boolean minimal = quality == ParticleStatus.MINIMAL, reduced = quality == ParticleStatus.DECREASED;
        int sites = minimal ? 28 : reduced ? 50 : 88;
        int heavySites = minimal ? 3 : reduced ? 6 : 10;
        displayBudget = minimal ? 36 : reduced ? 65 : 110;
        double reach = SpellTuning.PLANETFALL_CRATER_RADIUS * 1.58;
        double phase = RandomSource.create(seed).nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < sites; i++) {
            // Per-site random streams keep other slabs unchanged when a sampled column is unavailable.
            RandomSource random = RandomSource.create((long) seed ^ 0x6A09E667F3BCC909L ^ i * 0x9E3779B97F4A7C15L);
            double distance = reach * Math.pow((i + 0.35 + random.nextDouble() * 0.3) / sites, 0.63);
            double angle = phase + i * GOLDEN_ANGLE + (random.nextDouble() - 0.5) * 0.22;
            double x = mark.x + Math.cos(angle) * distance, z = mark.z + Math.sin(angle) * distance;
            Surface ground = sample(x, z);
            if (ground == null) continue;
            boolean heavy = i < heavySites;
            float fraction = (float) (distance / reach);
            float width = heavy ? 3.8f + random.nextFloat() * 2.0f
                    : Mth.lerp(fraction, 3.25f, 0.95f) * (0.85f + random.nextFloat() * 0.35f);
            float height = width * (heavy ? 0.36f + random.nextFloat() * 0.15f : 0.42f + random.nextFloat() * 0.35f);
            float depth = width * (0.67f + random.nextFloat() * 0.35f);
            Vector3f shape = new Vector3f(width, height, depth);
            Vec3 start = new Vec3(x, ground.y + height * 0.53, z);
            double launchAngle = angle + (random.nextDouble() - 0.5) * 0.24;
            double speed = (heavy ? 1.55 : Mth.lerp(fraction, 1.75, 0.62)) + random.nextDouble() * 0.65;
            double lift = (heavy ? 2.35 : Mth.lerp(fraction, 2.8, 0.92)) + random.nextDouble() * 0.7;
            Vec3 velocity = new Vec3(Math.cos(launchAngle) * speed, lift, Math.sin(launchAngle) * speed);
            Quaternionf rotation = new Quaternionf().rotationY((float) angle).rotateX((random.nextFloat() - 0.5f) * 0.14f);
            Vector3f tumbleAxis = new Vector3f((float) -Math.sin(angle), random.nextFloat() * 0.4f - 0.2f, (float) Math.cos(angle)).normalize();
            float tumble = (heavy ? 0.026f : 0.07f) + random.nextFloat() * (heavy ? 0.025f : 0.095f);
            Slab slab = new Slab(distance, start, velocity, rotation, tumbleAxis, tumble, shape, random, i);
            slab.parts.add(new Part(new Vector3f(), shape, new Quaternionf(), heavy ? ground.below : ground.top));
            if (heavy) {
                // An offset cap and a broken shoulder form one large irregular chunk, never three loose cubes.
                slab.parts.add(new Part(new Vector3f(-width * 0.13f, height * 0.38f, depth * 0.08f),
                        new Vector3f(width * 0.82f, height * 0.48f, depth * 0.87f),
                        new Quaternionf().rotationY(0.09f), ground.top));
                slab.parts.add(new Part(new Vector3f(width * 0.35f, -height * 0.12f, -depth * 0.16f),
                        new Vector3f(width * 0.52f, height * 0.76f, depth * 0.72f),
                        new Quaternionf().rotationZ(-0.19f).rotateY(-0.14f), ground.below));
            }
            slabs.add(slab);
        }
    }

    /** Advances both the expanding launch front and the independent ballistic slabs. */
    boolean tick() {
        if (slabs.isEmpty()) return true;
        if (++age >= MAX_AGE) {
            remove();
            return true;
        }
        // The initial contact throws the inner ground immediately; the slower rupture carries the rest outwards.
        double front = Math.max(16, PlanetfallTerrain.ruptureRadius(age));
        for (Iterator<Slab> it = slabs.iterator(); it.hasNext(); ) {
            Slab slab = it.next();
            if (slab.born < 0) {
                if (slab.distance > front) continue;
                if (!loaded(slab.position) || displays + slab.parts.size() > displayBudget) {
                    it.remove();
                    continue;
                }
                launch(slab);
                continue;
            }
            if (age >= slab.ends || slab.position.y < level.getMinY() - 12 || !loaded(slab.position)) {
                discard(slab);
                it.remove();
                continue;
            }
            if (!slab.settled) move(slab);
            float fade = Mth.clamp((slab.ends - age) / 28f, 0, 1);
            pose(slab, fade);
            if (!slab.settled && age - slab.born < 56 && (age + slab.index) % 12 == 0 && Fx.particlesVisible(slab.position)) {
                Fx.particle(ParticleTypes.SMOKE, slab.position, slab.velocity.scale(-0.025));
            }
        }
        return slabs.isEmpty();
    }

    private void launch(Slab slab) {
        slab.born = age;
        slab.ends = Math.min(MAX_AGE, age + 104 + slab.random.nextInt(12));
        for (Part part : slab.parts) {
            part.display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            part.display.setId(nextId--);
            part.display.setBlockState(part.state);
            part.display.setBrightnessOverride(EARTH_LIGHT);
            part.display.setViewRange(12);
            part.display.setPos(mark.x, mark.y, mark.z);
            level.addEntity(part.display);
            displays++;
        }
        pose(slab, 1);
        for (Part part : slab.parts) part.display.setTransformationInterpolationDuration(1);
        dust(slab, slab.position.add(0, -slab.shape.y * 0.5, 0), slab.parts.size() > 1 ? 7 : 3);
    }

    private void move(Slab slab) {
        Vec3 next = slab.position.add(slab.velocity);
        if (!loaded(next)) {
            // Unloaded flight paths fade out; no collision reads or chunks are requested beyond the visible world.
            slab.settled = true;
            slab.ends = Math.min(slab.ends, age + 12);
            return;
        }
        if (slab.velocity.y < 0 && age - slab.born > 3) {
            Vector3f up = new Quaternionf(slab.rotation).conjugate().transform(new Vector3f(0, 1, 0));
            double bottom = (Math.abs(up.x) * slab.shape.x + Math.abs(up.y) * slab.shape.y + Math.abs(up.z) * slab.shape.z) * 0.48;
            Vec3 from = slab.position.add(0, -bottom, 0), to = next.add(0, -bottom, 0);
            BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, slab.parts.getFirst().display));
            if (hit.getType() != HitResult.Type.MISS) {
                Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                next = hit.getLocation().add(0, bottom, 0).add(normal.scale(0.08));
                dust(slab, hit.getLocation(), slab.parts.size() > 1 ? 9 : 4);
                slab.velocity = slab.velocity.subtract(normal.scale(1.22 * slab.velocity.dot(normal))).scale(0.38);
                slab.tumble *= 0.3f;
                slab.ends = Math.min(slab.ends, age + 38);
                if (slab.bounces++ > 0 || slab.velocity.lengthSqr() < 0.12) {
                    slab.settled = true;
                    slab.velocity = Vec3.ZERO;
                }
            }
        }
        slab.position = next;
        if (!slab.settled) {
            slab.velocity = slab.velocity.scale(0.987).add(0, -0.083, 0);
            slab.rotation.rotateAxis(slab.tumble, slab.tumbleAxis);
        }
    }

    private void pose(Slab slab, float fade) {
        Vec3 relative = slab.position.subtract(mark);
        for (Part part : slab.parts) {
            Quaternionf turn = new Quaternionf(slab.rotation).mul(part.rotation);
            Vector3f size = new Vector3f(part.shape).mul(fade);
            Vector3f offset = new Quaternionf(slab.rotation).transform(new Vector3f(part.offset).mul(fade));
            offset.add((float) relative.x, (float) relative.y, (float) relative.z);
            offset.add(new Quaternionf(turn).transform(new Vector3f(size).mul(-0.5f)));
            part.display.setTransformation(new Transformation(offset, turn, size, null));
            part.display.setTransformationInterpolationDelay(0);
        }
    }

    private void dust(Slab slab, Vec3 at, int count) {
        if (!Fx.particlesVisible(at)) return;
        for (int i = 0, n = Fx.scaled(count); i < n; i++) {
            double angle = slab.random.nextDouble() * Mth.TWO_PI;
            Vec3 velocity = new Vec3(Math.cos(angle) * 0.35, 0.08 + slab.random.nextDouble() * 0.13, Math.sin(angle) * 0.35);
            Vec3 point = at.add((slab.random.nextDouble() - 0.5) * slab.shape.x, 0.1,
                    (slab.random.nextDouble() - 0.5) * slab.shape.z);
            Fx.particle(ModParticles.PLANETFALL_DUST, point, velocity);
        }
    }

    private Surface sample(double x, double z) {
        if (!level.hasChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))) return null;
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz) - 1;
        if (top < level.getMinY() || top < mark.y - SpellTuning.PLANETFALL_CRATER_DEPTH - 8) return null;
        boolean remoteRoof = top > mark.y + SpellTuning.PLANETFALL_PLANET_RADIUS * 2;
        if (remoteRoof) top = Math.min(level.getMaxY(), Mth.floor(mark.y) + 8);
        int bottom = Math.max(level.getMinY(), remoteRoof ? Mth.floor(mark.y - 36) : top - 12);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(bx, top, bz);
        boolean open = !remoteRoof;
        for (int y = top; y >= bottom; y--) {
            cursor.setY(y);
            BlockState state = level.getBlockState(cursor);
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
                open = true;
                continue;
            }
            if (!state.getFluidState().isEmpty()) return null;
            if (open && !state.hasBlockEntity() && !state.getCollisionShape(level, cursor).isEmpty()
                    && state.getDestroySpeed(level, cursor) >= 0
                    && state.getBlock().getExplosionResistance() < SpellTuning.VAPORISE_RESISTANCE_LIMIT) {
                BlockState below = y > level.getMinY() ? level.getBlockState(cursor.below()) : state;
                if (below.isAir() || !below.getFluidState().isEmpty() || below.hasBlockEntity()) below = state;
                return new Surface(y + 1, state, below);
            }
            open = state.getCollisionShape(level, cursor).isEmpty();
        }
        return null;
    }

    private boolean loaded(Vec3 at) {
        return level.hasChunk(SectionPos.blockToSectionCoord(at.x), SectionPos.blockToSectionCoord(at.z));
    }

    private void discard(Slab slab) {
        for (Part part : slab.parts) {
            if (part.display == null) continue;
            level.removeEntity(part.display.getId(), Entity.RemovalReason.DISCARDED);
            part.display = null;
            displays--;
        }
    }

    /** Also removes pending, unlaunched recipes, so dimension changes cannot leave delayed debris behind. */
    void remove() {
        for (Slab slab : slabs) discard(slab);
        slabs.clear();
    }

    private record Surface(int y, BlockState top, BlockState below) { }

    private static final class Slab {
        final double distance;
        final Quaternionf rotation;
        final Vector3f tumbleAxis, shape;
        final RandomSource random;
        final int index;
        final List<Part> parts = new ArrayList<>(3);
        Vec3 position, velocity;
        float tumble;
        int born = -1, ends, bounces;
        boolean settled;

        Slab(double distance, Vec3 position, Vec3 velocity, Quaternionf rotation, Vector3f tumbleAxis,
             float tumble, Vector3f shape, RandomSource random, int index) {
            this.distance = distance;
            this.position = position;
            this.velocity = velocity;
            this.rotation = rotation;
            this.tumbleAxis = tumbleAxis;
            this.tumble = tumble;
            this.shape = shape;
            this.random = random;
            this.index = index;
        }
    }

    private static final class Part {
        final Vector3f offset, shape;
        final Quaternionf rotation;
        final BlockState state;
        Display.BlockDisplay display;

        Part(Vector3f offset, Vector3f shape, Quaternionf rotation, BlockState state) {
            this.offset = offset;
            this.shape = shape;
            this.rotation = rotation;
            this.state = state;
        }
    }
}
