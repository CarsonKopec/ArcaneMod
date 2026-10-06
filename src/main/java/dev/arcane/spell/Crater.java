package dev.arcane.spell;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A crater's plan: every block in it, nearest the centre first so it can be carved outwards, and the shell of blocks just
 * outside its floor and walls. The crater is an ellipsoid around the impact, {@code radius} wide with a rim roughened
 * by three waves, and shallower below the impact than it is wide. Air is left out, so carving spends its budget on real
 * blocks.
 */
final class Crater {
    /** The rim varies by up to this fraction of the radius. */
    private static final double RIM_ROUGHNESS = 0.15;

    /** Every non-air block inside, nearest the centre first. */
    final List<BlockPos> blocks;
    /** The blocks just outside the floor and walls. */
    final List<BlockPos> shell;

    private Crater(List<BlockPos> blocks, List<BlockPos> shell) {
        this.blocks = blocks;
        this.shell = shell;
    }

    static Crater plan(ServerLevel level, Vec3 impact, double radius, double depth, RandomSource random) {
        // Phases of the three waves that roughen the rim.
        double[] phases = {random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * Mth.TWO_PI};
        int reach = Mth.ceil(radius * (1 + RIM_ROUGHNESS)) + 2;
        BlockPos centre = BlockPos.containing(impact);
        List<Spot> inside = new ArrayList<>();
        List<BlockPos> outside = new ArrayList<>();
        for (int dy = -Mth.ceil(depth) - 2; dy <= reach; dy++) {
            int y = centre.getY() + dy;
            if (y < level.getMinY() || y > level.getMaxY()) continue;
            for (int dx = -reach; dx <= reach; dx++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    double x = centre.getX() + dx + 0.5 - impact.x, z = centre.getZ() + dz + 0.5 - impact.z;
                    double rim = rimRadius(radius, phases, Math.atan2(z, x));
                    double up = y + 0.5 - impact.y;
                    double vertical = up < 0 ? depth * rim / radius : rim;
                    double d = Math.sqrt(Mth.square(x / rim) + Mth.square(z / rim) + Mth.square(up / vertical));
                    BlockPos pos = new BlockPos(centre.getX() + dx, y, centre.getZ() + dz);
                    if (d <= 1) {
                        if (!level.getBlockState(pos).isAir()) inside.add(new Spot(pos, d));
                    } else if (d <= 1.2 && up <= 1) {
                        outside.add(pos);
                    }
                }
            }
        }
        inside.sort(Comparator.comparingDouble(Spot::distance));
        return new Crater(inside.stream().map(Spot::pos).toList(), outside);
    }

    /** How far the rim is from the centre at {@code angle}, in radians around the impact from +x towards +z. */
    private static double rimRadius(double radius, double[] phases, double angle) {
        double wave = 0.6 * Math.sin(3 * angle + phases[0]) + 0.3 * Math.sin(7 * angle + phases[1]) + 0.1 * Math.sin(13 * angle + phases[2]);
        return radius * (1 + RIM_ROUGHNESS * wave);
    }

    /** A block in the crater, and how far through the crater it is: 0 at the centre, 1 at the edge. */
    private record Spot(BlockPos pos, double distance) {
    }
}
