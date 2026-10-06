package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.ArcaneBolt;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The mod on a real dedicated server, with the client connected over the network. In singleplayer, packets pass
 * between client and server in memory and are never encoded; here every one is. So this covers the spell and effect
 * registry sync at login, the spell wheel's pick, the staff's selected spell, synced mana and cooldowns, and effect
 * messages, as well as the terrain_damage game rule and an Astral Gate home from the Nether.
 */
public final class DedicatedServerGameTest implements FabricClientGameTest {
    private static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
    private static final BlockPos WALL = new BlockPos(0, -59, 6);
    private static final String BUILD_WALL = "fill -5 -60 6 5 -54 7 minecraft:stone";

    @Override public void runTest(ClientGameTestContext context) {
        // What the client decodes from each bolt impact the server sends.
        List<SpellEffect> impacts = new CopyOnWriteArrayList<>();
        context.runOnClient(client -> SpellEffectRenderers.register(ModSpellEffects.IMPACT, (effect, level) -> impacts.add(effect)));

        try (var server = context.worldBuilder().createServer(); var connection = server.connect()) {
            server.runCommand("gamemode survival @a");
            server.runCommand("give @a arcane_spells:arcane_staff");
            server.runCommand("summon minecraft:pig 0 -60 -1 {NoAI:1b}");
            server.runCommand("tp @a 0 -60 -6 0 10");
            connection.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // The wheel's pick crosses to the server, and the staff's new spell comes back to the client.
            context.getInput().holdKey(InputConstants.KEY_R);
            context.waitForScreen(SpellWheelScreen.class);
            context.getInput().moveCursor(190, -60);
            context.waitTicks(2);
            context.getInput().releaseKey(InputConstants.KEY_R);
            context.waitForScreen(null);
            context.waitTicks(5);
            server.runOnServer(s -> check(SpellCasting.selected(SpellCasting.heldStaff(player(s))) == ModSpells.CHAIN_LIGHTNING,
                    "The wheel's pick didn't reach the server"));
            context.runOnClient(client -> check(SpellCasting.selected(SpellCasting.heldStaff(client.player)) == ModSpells.CHAIN_LIGHTNING,
                    "The client's staff doesn't show the picked spell"));

            // A real right-click casts it; the client's mana and cooldowns match the server's.
            context.getInput().pressMouse(GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(3);
            server.runOnServer(s -> {
                var pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
                check(pig.getHealth() < pig.getMaxHealth(), "Chain Lightning missed the pig");
            });
            checkCasterSynced(context, server, SpellTuning.CHAIN_MANA);

            // A fully charged bolt, held and released, breaks the wall; the client decodes where it struck.
            server.runCommand("kill @e[type=minecraft:pig]");
            server.runCommand(BUILD_WALL);
            server.runCommand("tp @a 0 -60 -6 0 0");
            server.runOnServer(s -> {
                CasterState.set(player(s), CasterState.FULL);
                SpellCasting.select(player(s), ModSpells.ARCANE_BOLT);
            });
            context.waitTicks(3);
            context.getInput().holdMouse(GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(SpellTuning.FULL_CHARGE_TICKS + 2);
            context.getInput().releaseMouse(GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(10);
            context.takeScreenshot("dedicated-bolt");
            server.runOnServer(s -> check(s.overworld().getBlockState(WALL).isAir(), "The bolt didn't break the wall"));
            check(impacts.size() == 1, "The client decoded " + impacts.size() + " bolt impacts, expected 1");
            check(impacts.getFirst().position().distanceTo(WALL.getCenter()) < 2, "The client placed the impact at " + impacts.getFirst().position());
            checkCasterSynced(context, server, SpellTuning.BOLT_MANA);

            // The game rule, set from the server console, keeps the rebuilt wall standing.
            server.runCommand(BUILD_WALL);
            server.runCommand("gamerule arcane_spells:terrain_damage false");
            server.runOnServer(s -> ArcaneBolt.launch(s.overworld(), player(s), 1));
            context.waitTicks(10);
            server.runOnServer(s -> check(!s.overworld().getBlockState(WALL).isAir(), "A bolt broke terrain with terrain_damage off"));
            check(impacts.size() == 2, "The client decoded " + impacts.size() + " bolt impacts, expected 2");
            server.runCommand("gamerule arcane_spells:terrain_damage true");

            // An Astral Gate home from the Nether: the gate opens across dimensions and the client follows.
            Vec3 home = new Vec3(20.5, -60, 20.5);
            server.runCommand("spawnpoint @a 20 -60 20");
            server.runCommand("execute in minecraft:the_nether run forceload add -16 -16 15 15");
            // A sealed obsidian room, so no lava can reach the caster.
            server.runCommand("execute in minecraft:the_nether run fill -4 62 -4 4 69 4 minecraft:obsidian hollow");
            server.runCommand("item replace entity @a weapon.mainhand with arcane_spells:astral_staff");
            server.runCommand("execute in minecraft:the_nether run tp @a 0.5 63 0.5");
            context.waitFor(client -> client.level != null && client.level.dimension() == Level.NETHER, 200);
            connection.getClientWorld().waitForChunksRender();
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                player.setXRot(-90);
                check(SpellCasting.cast((ServerLevel) player.level(), player, ModSpells.ASTRAL_GATE, 1), "The gate home was refused in the Nether");
            });
            context.waitTicks(SpellTuning.GATE_CROSS_TICKS + 5);
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(player.level().dimension() == Level.OVERWORLD, "The gate home left the caster in " + player.level().dimension());
                check(player.position().distanceTo(home) < 1.5, "The gate home went to " + player.position());
            });
            context.waitFor(client -> client.level != null && client.level.dimension() == Level.OVERWORLD
                    && client.player.position().distanceTo(home) < 1.5, 200);
            connection.getClientWorld().waitForChunksRender();
            context.waitTicks(5);
            context.takeScreenshot("dedicated-gate-home");
            server.runCommand("execute in minecraft:the_nether run forceload remove all");
        }
    }

    /** The client's synced mana and cooldowns equal the server's, which record a cast costing {@code cost}. */
    private static void checkCasterSynced(ClientGameTestContext context, TestServerContext server, float cost) {
        CasterState onServer = server.computeOnServer(s -> CasterState.of(player(s)));
        CasterState onClient = context.computeOnClient(client -> CasterState.of(client.player));
        float spent = SpellTuning.BASE_MAX_MANA - onServer.manaAt();
        check(Math.abs(spent - cost) < 0.01, "The cast spent " + spent + " mana");
        check(onClient.equals(onServer), "The client has " + onClient + ", the server " + onServer);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
