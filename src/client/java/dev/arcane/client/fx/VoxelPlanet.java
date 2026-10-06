package dev.arcane.client.fx;

import com.mojang.math.Transformation;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * A broken alien world: black continents, incandescent fault lines, crystal ridges and two orbiting debris belts.
 * Its crust opens as it heats up, exposing the core, before becoming a bounded shower of ballistic fragments.
 *
 * <p>These block displays exist only on the client, with negative ids. A coarser complete shell is used at lower
 * particle settings, and both the intact planet and its debris have hard entity budgets. All displays are released
 * on completion or when the owning animation leaves its world.
 */
final class VoxelPlanet {
    private static final float OVERLAP = 1.045f;
    private static final int DEBRIS_SHRINK = 48, DEBRIS_TICKS = 104;
    private static final Brightness ROCK = new Brightness(10, 7);
    private static final Brightness GLOWING = new Brightness(15, 15);
    private static final Brightness CRYSTAL = new Brightness(14, 13);
    private static int nextId = -0x2000_0000;

    private final ClientLevel level;
    private final RandomSource random;
    private final List<Voxel> voxels = new ArrayList<>();
    private final Vector3f axis;
    private final Vector3f outerAxis;
    private final float radius;
    private final int budget, debrisBudget, innerRingCount, outerRingCount, crystalBudget;
    /** Terrain waves: direction, frequency, phase, amplitude. */
    private final float[][] waves = new float[6][];
    /** Fault waves: direction, frequency, phase. */
    private final float[][] cracks = new float[3][];
    private Vec3 center = Vec3.ZERO;
    private Vec3 wake = Fx.UP;
    private boolean posed;
    private int debrisAge = -1;

    VoxelPlanet(ClientLevel level, int seed, float radius) {
        this.level = level;
        this.random = RandomSource.create(seed);
        this.radius = Math.max(4, radius);
        ParticleStatus quality = Minecraft.getInstance().options.particles().get();
        boolean minimal = quality == ParticleStatus.MINIMAL, reduced = quality == ParticleStatus.DECREASED;
        budget = minimal ? 250 : reduced ? 430 : 640;
        debrisBudget = minimal ? 42 : reduced ? 78 : 128;
        innerRingCount = minimal ? 28 : reduced ? 46 : 72;
        outerRingCount = minimal ? 16 : reduced ? 26 : 40;
        crystalBudget = minimal ? 6 : reduced ? 12 : 18;
        float tilt = 0.62f, azimuth = random.nextFloat() * Mth.TWO_PI;
        axis = new Vector3f(Mth.sin(tilt) * Mth.cos(azimuth), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(azimuth)).normalize();
        outerAxis = new Quaternionf().rotationX(0.15f).transform(new Vector3f(axis)).normalize();
        for (int i = 0; i < waves.length; i++) {
            Vector3f w = unit();
            waves[i] = new float[] {w.x, w.y, w.z, 1.1f + random.nextFloat() * 2.8f,
                    random.nextFloat() * Mth.TWO_PI, 1 / (1 + i * 0.65f)};
        }
        for (int i = 0; i < cracks.length; i++) {
            Vector3f w = unit();
            cracks[i] = new float[] {w.x, w.y, w.z, 1.7f + random.nextFloat() * 2.2f,
                    random.nextFloat() * Mth.TWO_PI};
        }
        build();
    }

    private void build() {
        int shellBudget = budget - innerRingCount - outerRingCount - crystalBudget - 3;
        float voxelSize = radius / (budget > 500 ? 5.8f : budget > 300 ? 4.8f : 3.6f);
        List<Surface> shell = shell(voxelSize);
        // Changing grid spacing, rather than dropping random faces, preserves the silhouette at every quality.
        while (shell.size() > shellBudget) {
            voxelSize *= 1.06f;
            shell = shell(voxelSize);
        }
        int crystalSites = 0;
        for (Surface face : shell) if (face.elevation > 0.43f && face.crack > 0.22f) crystalSites++;
        int crystals = 0, site = 0;
        for (Surface face : shell) {
            surface(face, voxelSize);
            boolean crystal = false;
            if (face.elevation > 0.43f && face.crack > 0.22f) {
                int count = Math.min(crystalSites, crystalBudget);
                crystal = site * count / crystalSites != (site + 1) * count / crystalSites;
                site++;
            }
            if (crystal) {
                crystals++;
                float height = voxelSize * (1.1f + random.nextFloat() * 0.55f);
                Vector3f tip = new Vector3f(face.offset).add(new Vector3f(face.direction).mul(height * 0.48f));
                Quaternionf turn = new Quaternionf().rotationTo(new Vector3f(0, 1, 0), face.direction);
                add(tip, height, new Vector3f(0.38f, 1, 0.38f),
                        (crystals % 4 == 0 ? Blocks.CALCITE : Blocks.AMETHYST_BLOCK).defaultBlockState(),
                        CRYSTAL, Kind.CRYSTAL, turn, face.crack);
            }
        }
        // All core corners remain inside the rocky surface. White-gold heat shows behind the orange mantle.
        add(new Vector3f(), radius * 1.01f, new Vector3f(1), Blocks.MAGMA_BLOCK.defaultBlockState(), GLOWING,
                Kind.CORE, new Quaternionf().rotationY(Mth.PI / 4), 0);
        add(new Vector3f(), radius * 0.99f, new Vector3f(1), Blocks.MAGMA_BLOCK.defaultBlockState(), GLOWING,
                Kind.CORE, new Quaternionf().rotationX(Mth.PI / 4).rotateZ(Mth.PI / 4), 0);
        add(new Vector3f(), radius * 0.96f, new Vector3f(1), Blocks.SHROOMLIGHT.defaultBlockState(), GLOWING,
                Kind.CORE, new Quaternionf(), 0);
        ring(innerRingCount, radius * 1.58f, axis, Kind.INNER_RING);
        ring(outerRingCount, radius * 1.89f, outerAxis, Kind.OUTER_RING);
    }

    private List<Surface> shell(float voxelSize) {
        List<Surface> result = new ArrayList<>();
        int n = Mth.ceil(radius / voxelSize) + 2;
        for (int i = -n; i <= n; i++) {
            for (int j = -n; j <= n; j++) {
                for (int k = -n; k <= n; k++) {
                    Vector3f p = new Vector3f(i, j, k).mul(voxelSize);
                    float distance = p.length();
                    if (distance < 1.0e-3f) continue;
                    Vector3f direction = new Vector3f(p).div(distance);
                    float elevation = elevation(direction);
                    float surface = radius + Mth.clamp(elevation, -0.55f, 0.9f) * voxelSize * 0.9f;
                    if (distance > surface || distance <= surface - voxelSize) continue;
                    float crack = 1;
                    for (float[] fault : cracks) crack = Math.min(crack, Math.abs(wave(fault, direction)));
                    result.add(new Surface(p, direction, elevation, crack));
                }
            }
        }
        return result;
    }

    private void surface(Surface face, float voxelSize) {
        float e = face.elevation, polar = Math.abs(face.direction.dot(axis)), roll = random.nextFloat();
        BlockState block;
        Brightness light = ROCK;
        if (e < -0.42f || face.crack < 0.075f) {
            block = (face.crack < 0.018f ? Blocks.SHROOMLIGHT : Blocks.MAGMA_BLOCK).defaultBlockState();
            light = GLOWING;
        } else if (polar > 0.89f || e > 0.52f) {
            block = (roll < 0.68f ? Blocks.CRYING_OBSIDIAN : Blocks.AMETHYST_BLOCK).defaultBlockState();
            light = CRYSTAL;
        } else if (e > 0.24f) {
            block = (roll < 0.55f ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        } else {
            block = (roll < 0.48f ? Blocks.DEEPSLATE : roll < 0.8f ? Blocks.COBBLED_DEEPSLATE : Blocks.OBSIDIAN).defaultBlockState();
        }
        add(face.offset, voxelSize * OVERLAP, new Vector3f(1), block, light, Kind.SHELL, null, face.crack);
    }

    /** Ordered, broken bands read as planetary rings even at a distance; a few large shards punctuate them. */
    private void ring(int count, float bandRadius, Vector3f normal, Kind kind) {
        Vector3f u = new Vector3f(normal).cross(new Vector3f(1, 0, 0)).normalize();
        Vector3f v = new Vector3f(normal).cross(u).normalize();
        float phase = random.nextFloat() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            float angle = phase + (i + random.nextFloat() * 0.3f) * Mth.TWO_PI / count;
            float r = bandRadius + (random.nextFloat() - 0.5f) * radius * 0.1f;
            Vector3f offset = new Vector3f(u).mul(Mth.cos(angle) * r).add(new Vector3f(v).mul(Mth.sin(angle) * r))
                    .add(new Vector3f(normal).mul((random.nextFloat() - 0.5f) * 0.55f));
            float size = radius * (i % 9 == 0 ? 0.13f : 0.065f + random.nextFloat() * 0.035f);
            BlockState block = (i % 11 == 0 ? Blocks.SEA_LANTERN : i % 3 == 0 ? Blocks.CALCITE
                    : kind == Kind.OUTER_RING && i % 2 == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.AMETHYST_BLOCK).defaultBlockState();
            Quaternionf turn = new Quaternionf().rotationAxis(random.nextFloat() * Mth.TWO_PI, unit());
            add(offset, size, new Vector3f(1, 0.6f, 1.35f), block, CRYSTAL, kind, turn, 1);
        }
    }

    private float elevation(Vector3f direction) {
        float sum = 0, total = 0;
        for (float[] w : waves) {
            sum += w[5] * wave(w, direction);
            total += w[5];
        }
        return sum / total * 1.8f;
    }

    private static float wave(float[] w, Vector3f direction) {
        return Mth.sin((w[0] * direction.x + w[1] * direction.y + w[2] * direction.z) * w[3] * Mth.PI + w[4]);
    }

    private Vector3f unit() {
        Vector3f v = new Vector3f((float) random.nextGaussian(), (float) random.nextGaussian(), (float) random.nextGaussian());
        return v.lengthSquared() < 1.0e-6f ? new Vector3f(0, 1, 0) : v.normalize();
    }

    private void add(Vector3f offset, float size, Vector3f shape, BlockState block, Brightness light,
                     Kind kind, Quaternionf turn, float crack) {
        if (voxels.size() >= budget) return;
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setId(nextId--);
        display.setBlockState(block);
        display.setBrightnessOverride(light);
        display.setViewRange(12);
        level.addEntity(display);
        voxels.add(new Voxel(display, offset, size, shape, kind, turn, crack, random.nextFloat()));
    }

    int size() {
        return voxels.size();
    }

    void pose(Vec3 center, float spin) {
        pose(center, spin, 0);
    }

    /** Heat is descent progress, from a cool crust at zero to spreading faults and peeling plates at one. */
    void pose(Vec3 center, float spin, float heat) {
        if (shattered()) return;
        Vec3 travel = this.center.subtract(center);
        if (posed && travel.lengthSqr() > 1.0e-5) wake = travel.normalize();
        this.center = center;
        heat = Mth.clamp(heat, 0, 1);
        float stress = heat * heat * heat;
        Quaternionf planet = new Quaternionf().rotationAxis(spin, axis);
        Quaternionf inner = new Quaternionf().rotationAxis(spin * 1.35f, axis);
        Quaternionf outer = new Quaternionf().rotationAxis(-spin * 0.72f, outerAxis);
        for (Voxel voxel : voxels) {
            Quaternionf orbit = voxel.kind == Kind.INNER_RING ? inner : voxel.kind == Kind.OUTER_RING ? outer : planet;
            Quaternionf turn = new Quaternionf(orbit);
            if (voxel.turn != null) turn.mul(voxel.turn);
            Vector3f offset = new Quaternionf(orbit).transform(new Vector3f(voxel.offset));
            float size = voxel.size;
            if (voxel.kind == Kind.CORE) {
                size *= 1 + 0.06f * stress + 0.012f * Mth.sin(spin * 8);
            } else if (voxel.kind == Kind.SHELL || voxel.kind == Kind.CRYSTAL) {
                float fault = Mth.clamp(1 - voxel.crack / 0.42f, 0, 1);
                float opening = stress * fault;
                offset.mul(1 + opening * 0.075f);
                size *= 1 - opening * 0.22f;
                // Only a few plates on the trailing hemisphere peel away; the planet keeps its solid outline.
                float trailing = (float) (offset.x * wake.x + offset.y * wake.y + offset.z * wake.z) / radius;
                if (voxel.phase > 0.86f && trailing > 0.25f) {
                    float peel = opening * (voxel.phase - 0.86f) / 0.14f;
                    offset.add((float) wake.x * peel * radius * 0.38f,
                            (float) wake.y * peel * radius * 0.38f, (float) wake.z * peel * radius * 0.38f);
                    turn.rotateAxis(peel * 0.65f, axis);
                }
                if (voxel.kind == Kind.SHELL) {
                    int thermal = voxel.crack < 0.1f && heat > 0.78f ? 2
                            : voxel.crack < 0.075f + stress * 0.23f ? 1 : 0;
                    if (thermal > voxel.thermal) {
                        voxel.thermal = thermal;
                        voxel.display.setBlockState((thermal == 2 ? Blocks.SHROOMLIGHT : Blocks.MAGMA_BLOCK).defaultBlockState());
                        voxel.display.setBrightnessOverride(GLOWING);
                    }
                }
            } else {
                offset.mul(1 + stress * 0.06f);
                offset.add((float) wake.x * stress * radius * 0.09f,
                        (float) wake.y * stress * radius * 0.09f, (float) wake.z * stress * radius * 0.09f);
            }
            if (posed) voxel.display.setTransformationInterpolationDuration(1);
            voxel.position = center.add(offset.x, offset.y, offset.z);
            voxel.tumbling = new Quaternionf(turn);
            voxel.drawSize = size;
            place(voxel, center, offset, turn, size);
        }
        posed = true;
    }

    private static void place(Voxel voxel, Vec3 at, Vector3f offset, Quaternionf turn, float size) {
        voxel.display.setPos(at.x, at.y, at.z);
        Vector3f scale = new Vector3f(voxel.shape).mul(size);
        Vector3f corner = new Quaternionf(turn).transform(new Vector3f(scale).mul(-0.5f));
        voxel.display.setTransformation(new Transformation(offset.add(corner), new Quaternionf(turn), scale, null));
        voxel.display.setTransformationInterpolationDelay(0);
    }

    /** The bright core disappears in the flash; representative fragments preserve the heated, opened pose. */
    void shatter(Vec3 impact) {
        if (shattered()) return;
        if (!posed) pose(impact, 0, 1);
        debrisAge = 0;
        int total = voxels.size(), index = 0;
        int keep = Math.min(total, debrisBudget);
        for (Iterator<Voxel> it = voxels.iterator(); it.hasNext(); ) {
            Voxel voxel = it.next();
            boolean selected = index * keep / total != (index + 1) * keep / total;
            index++;
            if (voxel.kind == Kind.CORE || !selected) {
                discard(voxel);
                it.remove();
                continue;
            }
            Vec3 out = voxel.position.subtract(impact).multiply(1, 0, 1);
            if (out.lengthSqr() < 1.0e-6) out = new Vec3(voxel.phase - 0.5, 0, 0.5);
            out = out.normalize();
            boolean ring = voxel.kind == Kind.INNER_RING || voxel.kind == Kind.OUTER_RING;
            double speed = ring ? 1.05 + random.nextDouble() * 1.25 : 0.6 + random.nextDouble() * 1.65;
            Vec3 tangent = new Vec3(-out.z, 0, out.x).scale(ring ? 0.65 : 0.16);
            voxel.velocity = out.scale(speed).add(tangent).add(0, 0.65 + random.nextDouble() * (ring ? 1.15 : 1.85), 0);
            voxel.tumbleAxis = unit();
            voxel.tumble = (0.06f + random.nextFloat() * 0.22f) * (random.nextBoolean() ? 1 : -1);
            voxel.lifetime = DEBRIS_TICKS - random.nextInt(24);
            // Start close to the last intact size; fragments crumble further as the blast expands.
            voxel.drawSize *= ring ? 1 : 0.78f + random.nextFloat() * 0.17f;
        }
    }

    boolean shattered() {
        return debrisAge >= 0;
    }

    /** One bounded collision ray per moving fragment; no terrain mutation or requests for unloaded chunks. */
    boolean tickDebris() {
        if (!shattered()) return false;
        debrisAge++;
        boolean trails = Fx.particlesVisible(center);
        int index = 0;
        for (Iterator<Voxel> it = voxels.iterator(); it.hasNext(); ) {
            Voxel voxel = it.next();
            index++;
            if (debrisAge >= voxel.lifetime || voxel.position.y < level.getMinY() - 12) {
                discard(voxel);
                it.remove();
                continue;
            }
            if (!voxel.settled) {
                Vec3 next = voxel.position.add(voxel.velocity);
                if (debrisAge > 8 && voxel.velocity.y < 0
                        && loaded(voxel.position) && loaded(next)) {
                    BlockHitResult hit = level.clip(new ClipContext(voxel.position, next,
                            ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, voxel.display));
                    if (hit.getType() != HitResult.Type.MISS) {
                        Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                        next = hit.getLocation().add(normal.scale(voxel.drawSize * 0.35));
                        voxel.velocity = voxel.velocity.subtract(normal.scale(1.4 * voxel.velocity.dot(normal))).scale(0.52);
                        voxel.tumble *= 0.45f;
                        voxel.lifetime = Math.min(voxel.lifetime, debrisAge + 26);
                        if (voxel.bounces++ > 0 || voxel.velocity.lengthSqr() < 0.07) {
                            voxel.settled = true;
                            voxel.velocity = Vec3.ZERO;
                        }
                    }
                }
                voxel.position = next;
                if (!voxel.settled) {
                    voxel.velocity = voxel.velocity.scale(0.987).add(0, -0.065, 0);
                    voxel.tumbling.rotateAxis(voxel.tumble, voxel.tumbleAxis);
                }
            }
            if (trails && !voxel.settled && debrisAge < 58 && index % 8 == debrisAge % 8) {
                Fx.particle(voxel.thermal > 0 ? ParticleTypes.FLAME : voxel.kind == Kind.SHELL ? ParticleTypes.SMOKE : ParticleTypes.END_ROD,
                        voxel.position, voxel.velocity.scale(-0.025));
            }
            int fadeAt = Math.min(DEBRIS_SHRINK, voxel.lifetime - 22);
            float shrink = debrisAge <= fadeAt ? 1 : Mth.clamp((voxel.lifetime - debrisAge) / (float) (voxel.lifetime - fadeAt), 0, 1);
            // Keep the original entity anchor: interpolating local translations makes flight smooth and avoids
            // double-applying the old shell offset on the first frame of the explosion.
            Vec3 relative = voxel.position.subtract(center);
            place(voxel, center, new Vector3f((float) relative.x, (float) relative.y, (float) relative.z),
                    voxel.tumbling, voxel.drawSize * shrink);
        }
        if (!voxels.isEmpty() && debrisAge < DEBRIS_TICKS) return false;
        remove();
        return true;
    }

    private boolean loaded(Vec3 position) {
        return level.hasChunk(SectionPos.blockToSectionCoord(position.x), SectionPos.blockToSectionCoord(position.z));
    }

    private void discard(Voxel voxel) {
        level.removeEntity(voxel.display.getId(), Entity.RemovalReason.DISCARDED);
    }

    void remove() {
        for (Voxel voxel : voxels) discard(voxel);
        voxels.clear();
    }

    private enum Kind { SHELL, CRYSTAL, CORE, INNER_RING, OUTER_RING }

    private record Surface(Vector3f offset, Vector3f direction, float elevation, float crack) { }

    private static final class Voxel {
        final Display.BlockDisplay display;
        final Vector3f offset, shape;
        final float size, crack, phase;
        final Kind kind;
        final Quaternionf turn;
        Vec3 position = Vec3.ZERO, velocity = Vec3.ZERO;
        Quaternionf tumbling;
        Vector3f tumbleAxis;
        float tumble, drawSize;
        int thermal, bounces, lifetime;
        boolean settled;

        Voxel(Display.BlockDisplay display, Vector3f offset, float size, Vector3f shape,
              Kind kind, Quaternionf turn, float crack, float phase) {
            this.display = display;
            this.offset = offset;
            this.size = size;
            this.shape = shape;
            this.kind = kind;
            this.turn = turn;
            this.crack = crack;
            this.phase = phase;
            this.thermal = display.getBlockState().is(Blocks.SHROOMLIGHT) ? 2 : display.getBlockState().is(Blocks.MAGMA_BLOCK) ? 1 : 0;
        }
    }
}
