package dev.arcane.test;

import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * A stone plateau for the Astral spells to wreck: superflat ground is only four blocks deep. It's 81 blocks square
 * and 20 deep, from x -40 to 40 and z -10 to 70, its top face at y=-43, wide enough for Celestial Judgment's crater.
 */
final class Plateau {
    /** The block whose top face the spells are aimed at, in the plateau's middle. */
    static final BlockPos MARK = new BlockPos(0, -44, 40);
    /** Where an aim at the mark lands: the middle of its top face. */
    static final Vec3 MARK_TOP = MARK.getBottomCenter().add(0, 1, 0);
    /** Standing on the plateau, 34 blocks south of the mark, looking down at it from eye height (2.73 degrees). */
    static final String CASTER_AT = "tp @a 0 -43 6 0 2.73";

    private Plateau() {
    }

    /** Builds it, or rebuilds it over whatever a spell left. {@code /fill} takes at most 32768 blocks, so in slices. */
    static void build(TestServerContext server) {
        for (int z = -10; z < 70; z += 16) {
            server.runCommand(String.format("fill -40 -63 %d 40 -44 %d minecraft:stone", z, Math.min(69, z + 15)));
        }
    }
}
