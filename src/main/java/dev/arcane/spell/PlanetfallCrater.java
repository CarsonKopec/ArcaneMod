package dev.arcane.spell;

import dev.leyline.terrain.TerrainRules;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * A column-at-a-time excavation. No whole-volume scan, sort or final-tick burst: every read and mutation has a
 * per-tick budget, including the terraces, ejecta, scorched lining and exposed planetary heart.
 */
final class PlanetfallCrater {
    private static final int COLUMNS_PER_TICK = 256;
    private static final int BLOCKS_PER_TICK = 4096;
    private static final int CHANGES_PER_TICK = 2048;
    private static final int PLAN_COORDINATES_PER_TICK = 1024;
    private final ServerLevel level;
    private final ServerPlayer caster;
    private final Vec3 mark;
    private final PlanetfallTerrain shape;
    private final int reach = Mth.ceil(SpellTuning.PLANETFALL_CRATER_RADIUS * 1.6);
    private final List<ArrayDeque<Site>> rings = new ArrayList<>();
    private final List<ArrayDeque<Site>> fractures = new ArrayList<>();
    private final ArrayDeque<Surface> surfaces = new ArrayDeque<>();
    private final Iterator<BlockPos> heart;
    private final Vec3 heartCenter;
    private int planIndex, ringIndex, fractureIndex, columnWork;
    private Column active;
    private Column activeFracture;
    private int changes;

    PlanetfallCrater(ServerLevel level, ServerPlayer caster, Vec3 mark, int seed) {
        this.level = level;
        this.caster = caster;
        this.mark = mark;
        this.shape = new PlanetfallTerrain(seed);
        for (int i = 0; i <= reach; i++) {
            rings.add(new ArrayDeque<>());
            fractures.add(new ArrayDeque<>());
        }
        double radius = SpellTuning.PLANETFALL_CORE_RADIUS;
        this.heartCenter = new Vec3(mark.x, Math.max(mark.y - SpellTuning.PLANETFALL_CRATER_DEPTH + radius * 0.6,
                level.getMinY() + radius + 1), mark.z);
        BlockPos center = BlockPos.containing(heartCenter);
        int r = Mth.ceil(radius);
        this.heart = BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r)).iterator();
    }

    /** Prepare a small batch of horizontal coordinates during the rift. This never reads or writes world blocks. */
    void prepare() {
        int side = reach * 2 + 1;
        for (int i = 0; i < PLAN_COORDINATES_PER_TICK && planIndex < side * side; i++, planIndex++) {
            int x = Mth.floor(mark.x) + planIndex % side - reach;
            int z = Mth.floor(mark.z) + planIndex / side - reach;
            double dx = x + 0.5 - mark.x, dz = z + 0.5 - mark.z;
            double distance = Math.hypot(dx, dz);
            if (distance > SpellTuning.PLANETFALL_CRATER_RADIUS * 1.6) continue;
            double rim = shape.rimRadius(Math.atan2(dz, dx));
            double fracture = shape.fractureStrength(dx, dz);
            if (distance - rim > SpellTuning.PLANETFALL_RIM_WIDTH + 9 && fracture <= 0) continue;
            Site site = new Site(x, z, distance, rim, fracture);
            // Exterior fault channels lead the deeper bowl excavation instead of waiting behind its entire volume.
            var queue = distance >= rim && fracture > 0.08 ? fractures : rings;
            queue.get((int) distance).addLast(site);
        }
    }

    private boolean prepared() {
        int side = reach * 2 + 1;
        return planIndex >= side * side;
    }

    /** Returns true once all work is finished; a tall site can keep settling under the same strict work budget. */
    boolean tick(int elapsed) {
        changes = 0;
        columnWork = 0;
        if (!prepared()) {
            prepare();
            return false;
        }
        int checked = 0;
        // Heat follows the broken earth immediately, rather than repainting the entire crater at the end.
        while (checked < 512 && !surfaces.isEmpty() && surfaces.getFirst().readyAt() <= elapsed && changes < CHANGES_PER_TICK) {
            scorch(surfaces.removeFirst());
            checked++;
        }
        // Once the central columns are clear, the spalled heart can emerge while the distant terrain still collapses.
        if (ringIndex > SpellTuning.PLANETFALL_CORE_RADIUS * 2 + 2) {
            for (int i = 0; i < 128 && heart.hasNext() && changes < CHANGES_PER_TICK; i++, checked++) core(heart.next());
        }
        int allowedReach = Mth.ceil(PlanetfallTerrain.ruptureRadius(elapsed));
        checked += workColumns(true, 512, allowedReach, elapsed);
        checked += workColumns(false, BLOCKS_PER_TICK - checked, allowedReach, elapsed);
        return ringIndex > reach && fractureIndex > reach && active == null && activeFracture == null
                && surfaces.isEmpty() && !heart.hasNext();
    }

    private int workColumns(boolean fracture, int budget, int allowedReach, int elapsed) {
        int checked = 0;
        while (checked < budget && changes < CHANGES_PER_TICK) {
            Column column = fracture ? activeFracture : active;
            if (column != null) {
                if (!column.step()) {
                    column.finish(elapsed);
                    if (fracture) activeFracture = null;
                    else active = null;
                }
                checked++;
            } else {
                if (columnWork >= COLUMNS_PER_TICK) break;
                Site site = nextSite(fracture, allowedReach);
                if (site == null) break;
                columnWork++;
                column = planColumn(site);
                if (fracture) activeFracture = column;
                else active = column;
                checked++;
            }
        }
        return checked;
    }

    private Site nextSite(boolean fracture, int allowedReach) {
        var queues = fracture ? fractures : rings;
        int index = fracture ? fractureIndex : ringIndex;
        while (index <= reach && queues.get(index).isEmpty()) index++;
        if (fracture) fractureIndex = index;
        else ringIndex = index;
        return index <= reach && index <= allowedReach ? queues.get(index).removeFirst() : null;
    }

    private Column planColumn(Site site) {
        int x = site.x(), z = site.z();
        double dx = x + 0.5 - mark.x, dz = z + 0.5 - mark.z;
        double distance = site.distance(), angle = Math.atan2(dz, dx);
        double rim = site.rim(), fracture = site.fracture();
        double beyond = distance - rim;
        BlockPos anchor = new BlockPos(x, Mth.clamp(Mth.floor(mark.y), level.getMinY(), level.getMaxY()), z);
        if (!available(anchor)) return null;
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
        if (ground < level.getMinY()) return null;
        if (distance < rim) {
            int floor = Math.max(level.getMinY(), Mth.floor(mark.y - shape.floorDepth(dx, dz)));
            // Also bite through the foot of hills and structures, without cutting an infinite cylinder into mountains.
            int dome = Mth.floor(mark.y + SpellTuning.PLANETFALL_CRATER_RADIUS * Math.sqrt(Math.max(0, 1 - distance * distance / (rim * rim))));
            int top = Math.min(level.getMaxY(), Math.min(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, dome));
            if (top <= floor) return null;
            return new Column(x, z, top, floor + 1, false, fracture, distance / rim);
        }
        // Heightmaps point at the roof in caves/the Nether. Never throw a surface fracture or deposit onto a remote
        // ceiling (or a valley far below this impact); the bowl itself is already clipped to the local impact dome.
        if (ground > mark.y + SpellTuning.PLANETFALL_PLANET_RADIUS
                || ground < mark.y - SpellTuning.PLANETFALL_CRATER_DEPTH * 0.65) return null;
        if (fracture > 0.08) {
            int floor = Math.max(level.getMinY(), ground - 2 - (int) Math.ceil(fracture * 6));
            return new Column(x, z, ground, floor + 1, false, fracture, 1);
        }
        BlockPos support = new BlockPos(x, ground, z);
        if (!available(support)) return null;
        BlockState beneath = level.getBlockState(support);
        // Deposits need actual, breakable natural ground; never build atop protected blocks, containers or canopies.
        if (!TerrainRules.isGround(beneath) || !TerrainRules.mayVaporise(level, caster, true, support, beneath)) return null;
        double width = SpellTuning.PLANETFALL_RIM_WIDTH;
        double variation = shape.variation(x, z);
        int height;
        if (beyond < width) {
            double taper = 1 - beyond / width;
            double folds = shape.ejectaWeight(angle) * (0.9 + 0.15 * Math.sin(angle * 11 + distance * 0.35));
            height = (int) Math.round(SpellTuning.PLANETFALL_RIM_HEIGHT * Math.pow(taper, 1.4) * folds * (0.9 + variation * 0.2));
        } else {
            // Clusters of shattered slabs give the outer apron weight; their gaps leave readable fracture channels.
            double cluster = Math.sin(x * 0.37 + angle) * Math.sin(z * 0.31 - angle);
            height = cluster > 0.38 && variation > 0.56 && beyond < width + 9 ? 1 + (variation > 0.78 ? 2 : 0) : 0;
        }
        return height > 0 ? new Column(x, z, ground + 1, Math.min(level.getMaxY(), ground + height), true, 0, 1) : null;
    }

    private final class Column {
        private final int x, z, end;
        private final boolean deposit;
        private final double heat, radius;
        private int y;

        Column(int x, int z, int start, int end, boolean deposit, double heat, double radius) {
            this.x = x;
            this.z = z;
            this.y = start;
            this.end = end;
            this.deposit = deposit;
            this.heat = heat;
            this.radius = radius;
        }

        void finish(int elapsed) {
            if (deposit) return;
            // Give neighboring columns time to uncover the sides of the tall, collapsed terraces.
            for (int i = 0; i <= 4 && end - 1 - i >= level.getMinY(); i++)
                surfaces.addLast(new Surface(new BlockPos(x, end - 1 - i, z), heat, radius, elapsed + 5));
        }

        boolean step() {
            if (deposit ? y > end : y < end) return false;
            BlockPos pos = new BlockPos(x, y, z);
            if (available(pos)) {
                BlockState state = level.getBlockState(pos);
                if (deposit) {
                    if (!state.canBeReplaced() || state.hasBlockEntity() || !level.mayInteract(caster, pos)) return false;
                    double roll = shape.variation(x + y * 3, z - y);
                    set(pos, roll < 0.42 ? Blocks.BLACKSTONE.defaultBlockState()
                            : roll < 0.75 ? Blocks.COBBLED_DEEPSLATE.defaultBlockState()
                            : roll < 0.90 ? Blocks.BASALT.defaultBlockState() : Blocks.TUFF.defaultBlockState());
                } else if (TerrainRules.mayVaporise(level, caster, true, pos, state)) {
                    TerrainRules.vaporise(level, pos);
                    changes++;
                }
            }
            y += deposit ? 1 : -1;
            return true;
        }
    }

    private void scorch(Surface surface) {
        BlockPos pos = surface.pos();
        if (!available(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!TerrainRules.isGround(state) || !TerrainRules.mayVaporise(level, caster, true, pos, state) || !exposed(pos)) return;
        double roll = shape.variation(pos.getX() + pos.getY(), pos.getZ());
        BlockState material;
        if (surface.fracture() > 0.33) {
            material = roll < 0.7 ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.CRYING_OBSIDIAN.defaultBlockState();
        } else if (surface.radius() < 0.3) {
            material = roll < 0.45 ? Blocks.OBSIDIAN.defaultBlockState()
                    : roll < 0.65 ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        } else {
            // Broad dark strata interrupted by connected incandescent veins, rather than random confetti.
            double vein = Math.sin(pos.getX() * 0.23 + pos.getZ() * 0.19 + pos.getY() * 0.7);
            material = vein > 0.94 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                    : roll < 0.52 ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 0.82 ? Blocks.BASALT.defaultBlockState() : Blocks.COBBLED_DEEPSLATE.defaultBlockState();
        }
        set(pos, material);
    }

    private void core(BlockPos mutable) {
        BlockPos pos = mutable.immutable();
        double radius = SpellTuning.PLANETFALL_CORE_RADIUS;
        double distance = pos.getCenter().distanceTo(heartCenter);
        Vec3 local = pos.getCenter().subtract(heartCenter);
        double angle = Math.atan2(local.z, local.x);
        double brokenRadius = radius * (0.9 + 0.075 * Math.sin(angle * 3 + local.y) + 0.045 * Math.sin(angle * 7 - local.y * 1.5));
        if (distance > brokenRadius || !available(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (!(state.canBeReplaced() || TerrainRules.mayVaporise(level, caster, true, pos, state))
                || state.hasBlockEntity() || !level.mayInteract(caster, pos)) return;
        double seam = Math.abs(Math.sin(Math.atan2(local.z, local.x) * 3 + local.y * 0.7));
        // Riches: the planet's heart, studded with ore for the caster to dig out.
        double ore = shape.variation(pos.getX() * 7 + pos.getY(), pos.getZ() * 13 - pos.getY() * 5);
        BlockState material = distance < radius * 0.5 || seam < 0.22 ? Blocks.SHROOMLIGHT.defaultBlockState()
                : seam < 0.4 ? Blocks.MAGMA_BLOCK.defaultBlockState()
                : ore < SpellTuning.PLANETFALL_CORE_DEBRIS ? Blocks.ANCIENT_DEBRIS.defaultBlockState()
                : ore < SpellTuning.PLANETFALL_CORE_DEBRIS + SpellTuning.PLANETFALL_CORE_DIAMOND ? Blocks.DEEPSLATE_DIAMOND_ORE.defaultBlockState()
                : ore < SpellTuning.PLANETFALL_CORE_DEBRIS + SpellTuning.PLANETFALL_CORE_DIAMOND + SpellTuning.PLANETFALL_CORE_GOLD
                        ? Blocks.DEEPSLATE_GOLD_ORE.defaultBlockState()
                : shape.variation(pos.getX(), pos.getZ() + pos.getY()) < 0.45 ? Blocks.CRYING_OBSIDIAN.defaultBlockState()
                : Blocks.OBSIDIAN.defaultBlockState();
        set(pos, material);
    }

    /** Explicit scans and the immediate neighbors of vanilla block updates must already be loaded. */
    private boolean available(BlockPos pos) {
        return pos.getY() >= level.getMinY() && pos.getY() <= level.getMaxY()
                && level.getWorldBorder().isWithinBounds(pos)
                && loadedForUpdate(level, pos.getX(), pos.getZ());
    }

    /** UPDATE_ALL probes neighboring block shapes; an edge block therefore needs that adjacent chunk as well. */
    static boolean loadedForUpdate(ServerLevel level, int x, int z) {
        int chunkX = SectionPos.blockToSectionCoord(x), chunkZ = SectionPos.blockToSectionCoord(z);
        if (!level.hasChunk(chunkX, chunkZ)) return false;
        int localX = x & 15, localZ = z & 15;
        return (localX != 0 || level.hasChunk(chunkX - 1, chunkZ))
                && (localX != 15 || level.hasChunk(chunkX + 1, chunkZ))
                && (localZ != 0 || level.hasChunk(chunkX, chunkZ - 1))
                && (localZ != 15 || level.hasChunk(chunkX, chunkZ + 1));
    }

    private boolean exposed(BlockPos pos) {
        for (Direction direction : Direction.values()) {
            BlockPos neighbor = pos.relative(direction);
            if (available(neighbor) && level.getBlockState(neighbor).isAir()) return true;
        }
        return false;
    }

    private void set(BlockPos pos, BlockState state) {
        if (level.setBlock(pos, state, Block.UPDATE_ALL)) changes++;
    }

    private record Site(int x, int z, double distance, double rim, double fracture) {
    }

    private record Surface(BlockPos pos, double fracture, double radius, int readyAt) {
    }
}
