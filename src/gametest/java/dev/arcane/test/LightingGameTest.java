package dev.arcane.test;

import de.nexusrealms.nebulon.api.render.light.client.NebulonLights;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.network.SpellEffects;
import dev.arcane.spell.SpellBolts;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/**
 * End-to-end check in a real client: charge light renders, a server-fired bolt destroys a wall,
 * and every temporary light is cleaned up.
 */
public final class LightingGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set midnight");
            server.runCommand("tp @a 0 -60 -6 0 0");
            server.runCommand("fill -5 -60 6 5 -54 7 minecraft:stone");
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(10);
            context.takeScreenshot("arcane-before-lighting");

            for (int i = 0; i < 12; i++) {
                int age = i * 2;
                server.runOnServer(s -> {
                    var player = s.getPlayerList().getPlayers().getFirst();
                    SpellEffects.send(s.overworld(), SpellEffectPayload.Kind.CHARGE, new Vec3(0, -58.4, 1), new Vec3(0, 0, 1), 1, age, player.getId());
                });
                context.waitTicks(2);
            }
            context.runOnClient(client -> {
                if (NebulonLights.lights().stats().rendered() < 1) throw new AssertionError("Charging light did not render");
            });
            context.takeScreenshot("arcane-charge-lighting");

            server.runOnServer(s -> SpellBolts.cast(s.overworld(), s.getPlayerList().getPlayers().getFirst(), 1));
            context.waitTicks(8);
            context.takeScreenshot("arcane-impact-lighting");
            server.runOnServer(s -> {
                if (!s.overworld().getBlockState(new BlockPos(0, -59, 6)).isAir()) throw new AssertionError("Blast did not destroy the target wall");
            });

            context.waitTicks(45);
            context.runOnClient(client -> {
                if (NebulonLights.lights().size() != 0) throw new AssertionError("Expired spell lights leaked");
            });
            context.takeScreenshot("arcane-after-blast");
        }
    }
}
