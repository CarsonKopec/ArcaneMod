package dev.arcane.test;

import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Astral Staff and Celestial Judgment in a real client: the staff holds only its own spells, the spell refuses a
 * short channel, a mark too close or no mark at all, leaves terrain alone in Adventure mode and with the
 * {@code arcane_spells:terrain_damage} game rule off, and otherwise, cast with a real two-second right-click, bores a
 * crater, spares obsidian, scorches the walls, and kills what it should.
 * Screenshots record every phase.
 */
public final class JudgmentGameTest implements FabricClientGameTest {
    private static final int GLFW_MOUSE_BUTTON_RIGHT = 1;
    private static final BlockPos MARK = Plateau.MARK;
    private static final String CASTER_AT = Plateau.CASTER_AT;
    /** Every phase, with a few ticks to spare. */
    private static final int STRIKE_TICKS = SpellTuning.JUDGMENT_GATHER_TICKS + SpellTuning.JUDGMENT_BEAM_TICKS + SpellTuning.JUDGMENT_CARVE_TICKS + 5;

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set 6000");
            server.runCommand("gamemode survival @a");
            Plateau.build(server);
            server.runCommand("setblock 8 -48 40 minecraft:obsidian");
            server.runCommand("give @a arcane_spells:astral_staff");
            server.runCommand(CASTER_AT);
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // The Astral Staff holds Celestial Judgment alone; the Arcane Staff can't hold it.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                ItemStack astral = SpellCasting.heldStaff(player);
                check(astral.is(ModItems.ASTRAL_STAFF), "The Astral Staff isn't in hand");
                check(SpellCasting.spells(astral).getFirst() == ModSpells.CELESTIAL_JUDGMENT, "Astral Staff holds " + SpellCasting.spells(astral));
                check(!SpellCasting.spells(new ItemStack(ModItems.ARCANE_STAFF)).contains(ModSpells.CELESTIAL_JUDGMENT),
                        "The Arcane Staff holds Celestial Judgment");
                SpellCasting.select(player, ModSpells.ARCANE_BOLT);
                check(SpellCasting.selected(astral) == ModSpells.CELESTIAL_JUDGMENT, "The Astral Staff took a spell it doesn't hold");
            });

            // Refusals, none of which may cost anything: a short channel, a mark too close, and nothing to mark.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.CELESTIAL_JUDGMENT, 0.5f), "A half channel was cast");
                player.teleportTo(0.5, -43, 6.5);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, MARK.offset(0, 0, -16).getCenter());
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.CELESTIAL_JUDGMENT, 1), "A mark 18 blocks away was struck");
                player.setXRot(-90);
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.CELESTIAL_JUDGMENT, 1), "Aiming at the sky struck something");
                check(CasterState.of(player).manaAt() == SpellTuning.BASE_MAX_MANA, "A refused cast spent mana");
            });

            // In Adventure mode, then in Survival with the terrain_damage game rule off, the whole strike plays out and
            // the plateau is untouched.
            server.runCommand("gamemode adventure @a");
            strikeLeavesTerrain(context, server, "An Adventure-mode strike broke terrain");
            server.runCommand("gamemode survival @a");
            server.runCommand("gamerule arcane_spells:terrain_damage false");
            strikeLeavesTerrain(context, server, "A strike broke terrain with terrain_damage off");
            server.runCommand("gamerule arcane_spells:terrain_damage true");

            // The real thing: pigs at the mark, near the blast's edge and far away; a real two-second channel.
            // The edge pig stands just past the rim, inside the blast, to the side so the blast throws it along the plateau.
            server.runCommand("summon minecraft:pig 0.5 -43 40.5 {NoAI:1b}");
            server.runCommand("summon minecraft:pig 29.5 -43 40.5 {NoAI:1b}");
            server.runCommand("summon minecraft:pig 0 -60 110 {NoAI:1b}");
            server.runCommand(CASTER_AT);
            server.runOnServer(s -> CasterState.set(player(s), CasterState.FULL));
            context.waitTicks(5);
            context.getInput().holdMouse(GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(24);
            context.takeScreenshot("judgment-channel");
            context.waitTicks(SpellTuning.JUDGMENT_CHARGE_TICKS - 24 + 4);
            context.takeScreenshot("judgment-channel-complete");
            context.getInput().releaseMouse(GLFW_MOUSE_BUTTON_RIGHT);
            // The release reaches the server a tick or two later.
            context.waitTicks(3);
            server.runOnServer(s -> {
                CasterState state = CasterState.of(player(s));
                float spent = SpellTuning.BASE_MAX_MANA - state.manaAt();
                check(Math.abs(spent - SpellTuning.JUDGMENT_MANA) < 0.01, "The cast spent " + spent + " mana");
                check(state.cooldownLeft(ModSpells.CELESTIAL_JUDGMENT, s.overworld().getGameTime()) > 0, "No cooldown started");
            });

            // Watch from a spectator's vantage point off the plateau's corner, high enough to see the ground circles and
            // far enough back to see the sky circle too.
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 70 -15 -30 facing 0 -3 40");
            context.waitTicks(30);
            context.takeScreenshot("judgment-gather");
            context.waitTicks(SpellTuning.JUDGMENT_GATHER_TICKS - 30 + 12);
            context.takeScreenshot("judgment-beam");
            context.waitTicks(SpellTuning.JUDGMENT_BEAM_TICKS - 12 + 2);
            context.takeScreenshot("judgment-blast");
            context.waitTicks(12);
            context.takeScreenshot("judgment-shockwave");
            context.waitTicks(40);
            context.takeScreenshot("judgment-aftermath");
            server.runCommand("tp @a 0 15 25 facing 0 -50 40");
            context.waitTicks(5);
            context.takeScreenshot("judgment-crater");

            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                check(level.getBlockState(MARK.below(4)).isAir(), "No crater under the mark");
                check(level.getBlockState(new BlockPos(0, -63, 40)).isAir(), "The shaft didn't reach bedrock");
                check(level.getBlockState(new BlockPos(0, -64, 40)).is(Blocks.BEDROCK), "The shaft broke bedrock");
                check(level.getBlockState(new BlockPos(8, -48, 40)).is(Blocks.OBSIDIAN), "Obsidian didn't survive");
                check(!level.getBlockState(new BlockPos(36, -45, 40)).isAir(), "The crater reached past its rim");
                int seared = 0;
                for (BlockPos pos : BlockPos.betweenClosed(-30, -62, 10, 30, -40, 69)) {
                    BlockState state = level.getBlockState(pos);
                    if (state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.MAGMA_BLOCK)) seared++;
                }
                check(seared > 50, "Only " + seared + " scorched blocks");
                List<? extends Pig> pigs = level.getEntities(EntityType.PIG, pig -> pig.isAlive());
                check(pigs.stream().noneMatch(pig -> pig.position().distanceTo(MARK.getCenter()) < 8), "A pig at the mark survived");
                Pig near = pigs.stream().filter(pig -> pig.getX() > 20 && pig.getY() > -70).findFirst().orElse(null);
                check(near != null && near.getHealth() < near.getMaxHealth(), "The pig near the blast's edge was unhurt or killed: " + near);
                Pig far = pigs.stream().filter(pig -> pig.getZ() > 100).findFirst().orElse(null);
                check(far != null && far.getHealth() == far.getMaxHealth(), "The far pig was hurt");
            });
        }
    }

    /** Strikes the mark from the caster's spot, then checks once the strike is over that the mark still stands. */
    private static void strikeLeavesTerrain(ClientGameTestContext context, TestServerContext server, String failure) {
        server.runCommand(CASTER_AT);
        context.waitTicks(2);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            CasterState.set(player, CasterState.FULL);
            // Aimed here, in the same tick: the client's own rotation may not have caught up with the teleport.
            player.lookAt(EntityAnchorArgument.Anchor.EYES, MARK.getBottomCenter().add(0, 1, 0));
            check(SpellCasting.cast(s.overworld(), player, ModSpells.CELESTIAL_JUDGMENT, 1),
                    "Cast refused, aiming " + player.getXRot() + "/" + player.getYRot() + " from " + player.position());
        });
        context.waitTicks(STRIKE_TICKS);
        server.runOnServer(s -> check(!s.overworld().getBlockState(MARK).isAir(), failure));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
