package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.api.spell.Spell;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.ChronoField;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Astral Staff's other four spells in a real client, each cast on a stone plateau: Meteor Swarm cratering the
 * ground and hurting what's there; Singularity dragging creatures and items in, eating the ground and collapsing;
 * Chrono Field freezing a creature and an arrow, holding back damage until time resumes; Astral Gate carrying the
 * caster, and a pig standing in the gate, to the far end. Screenshots record each.
 */
public final class AstralSpellsGameTest implements FabricClientGameTest {
    /** As in JudgmentGameTest: a stone plateau with its top face at y=-43. */
    private static final String PLATEAU = "fill -16 -63 -2 16 -44 46 minecraft:stone";
    private static final BlockPos MARK = new BlockPos(0, -44, 30);
    /** The top face of the mark's block: where an aim at it lands. */
    private static final Vec3 MARK_TOP = MARK.getBottomCenter().add(0, 1, 0);
    private static final String CASTER_AT = "tp @a 0 -43 4 0 3.64";
    private static final String VANTAGE = "tp @a 34 -18 -4 facing 0 -13 30";

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set 6000");
            server.runCommand("gamemode survival @a");
            server.runCommand(PLATEAU);
            server.runCommand("give @a arcane_spells:astral_staff");
            server.runCommand(CASTER_AT);
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // The staff holds all five Astral spells, in order, and the wheel shows them.
            server.runOnServer(s -> {
                List<Spell> held = SpellCasting.spells(SpellCasting.heldStaff(player(s)));
                check(held.equals(List.of(ModSpells.CELESTIAL_JUDGMENT, ModSpells.METEOR_SWARM, ModSpells.SINGULARITY,
                        ModSpells.CHRONO_FIELD, ModSpells.ASTRAL_GATE)), "Astral Staff holds " + held);
            });
            context.getInput().holdKey(InputConstants.KEY_R);
            context.waitForScreen(SpellWheelScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("astral-wheel");
            context.getInput().releaseKey(InputConstants.KEY_R);
            context.waitForScreen(null);

            meteorSwarm(context, server);
            singularity(context, server);
            chronoField(context, server);
            astralGate(context, server);
        }
    }

    private static void meteorSwarm(ClientGameTestContext context, TestServerContext server) {
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5;
            server.runCommand(String.format("summon minecraft:pig %.1f -43 %.1f {NoAI:1b}", 0.5 + Math.cos(angle) * 7, 30.5 + Math.sin(angle) * 7));
        }
        server.runOnServer(s -> check(castAt(s, ModSpells.METEOR_SWARM, MARK_TOP), "Meteor Swarm was refused"));
        server.runCommand("gamemode spectator @a");
        server.runCommand(VANTAGE);
        context.waitTicks(30);
        context.takeScreenshot("astral-meteors");
        context.waitTicks(SpellTuning.METEOR_COUNT * SpellTuning.METEOR_INTERVAL_TICKS + 30);
        context.takeScreenshot("astral-meteors-after");
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            int broken = 0;
            for (BlockPos pos : BlockPos.betweenClosed(MARK.offset(-16, -2, -16), MARK.offset(16, 0, 16))) {
                if (level.getBlockState(pos).isAir() && pos.getZ() <= 46) broken++;
            }
            check(broken >= 20, "Meteors broke only " + broken + " blocks of the plateau's top");
            List<? extends Pig> pigs = level.getEntities(EntityType.PIG, pig -> true);
            long hit = pigs.stream().filter(pig -> !pig.isAlive() || pig.getHealth() < pig.getMaxHealth()).count();
            check(hit >= 1, "No pig was hit by " + SpellTuning.METEOR_COUNT + " meteors");
        });
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    private static void singularity(ClientGameTestContext context, TestServerContext server) {
        server.runCommand(PLATEAU);
        server.runCommand(CASTER_AT);
        // With their AI: a mob without it ignores its own velocity, so nothing could drag it.
        server.runCommand("summon minecraft:pig 9.5 -43 30.5");
        server.runCommand("summon minecraft:pig -8.5 -43 30.5");
        server.runCommand("summon minecraft:pig 0.5 -43 39.5");
        server.runCommand("summon minecraft:item 0.5 -43 22.5 {Item:{id:\"minecraft:diamond\",count:1}}");
        context.waitTicks(5);
        Vec3 center = MARK_TOP.add(0, SpellTuning.SINGULARITY_LIFT, 0);
        server.runOnServer(s -> check(castAt(s, ModSpells.SINGULARITY, MARK_TOP), "Singularity was refused"));
        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a 22 -34 12 facing 0 -40 30");
        context.waitTicks(40);
        context.takeScreenshot("astral-singularity");
        server.runOnServer(s -> {
            for (Pig pig : s.overworld().getEntities(EntityType.PIG, pig -> pig.isAlive())) {
                check(pig.getBoundingBox().getCenter().distanceTo(center) < 6, "A pig wasn't dragged in: " + pig.position());
            }
            List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(20));
            check(!items.isEmpty() && items.getFirst().position().distanceTo(center) < 6, "The diamond wasn't dragged in: " + items);
        });
        context.waitTicks(SpellTuning.SINGULARITY_DURATION_TICKS - 40 + 6);
        context.takeScreenshot("astral-singularity-collapse");
        server.runOnServer(s -> {
            check(s.overworld().getBlockState(MARK).isAir(), "The ground under the singularity wasn't eaten");
            long dead = s.overworld().getEntities(EntityType.PIG, pig -> !pig.isAlive()).size()
                    + 3 - s.overworld().getEntities(EntityType.PIG, pig -> true).size();
            check(dead >= 1, "The singularity crushed no pig");
        });
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    private static void chronoField(ClientGameTestContext context, TestServerContext server) {
        server.runCommand(PLATEAU);
        server.runCommand("tp @a 0 -43 10 0 20");
        // A pig free to wander, and an arrow flying in from outside the dome, wide of the caster.
        server.runCommand("summon minecraft:pig 3.5 -43 12.5");
        context.waitTicks(3);
        server.runOnServer(s -> check(castAt(s, ModSpells.CHRONO_FIELD, MARK_TOP), "Chrono Field was refused"));
        server.runCommand("summon minecraft:arrow 5.5 -41 -6 {Motion:[0.0d,0.0d,1.5d]}");
        context.waitTicks(15);
        Vec3[] held = new Vec3[2];
        server.runOnServer(s -> {
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            Arrow arrow = s.overworld().getEntities(EntityType.ARROW, a -> true).getFirst();
            check(ChronoField.isFrozen(pig) && pig.isNoAi(), "The pig wasn't frozen");
            check(ChronoField.isFrozen(arrow), "The arrow wasn't frozen, at " + arrow.position());
            held[0] = pig.position();
            held[1] = arrow.position();
            pig.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player(s)), 4);
            check(pig.getHealth() == pig.getMaxHealth(), "A frozen pig took damage at once");
        });
        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a 14 -36 -2 facing 0 -42 10");
        context.waitTicks(10);
        context.takeScreenshot("astral-chrono");
        server.runOnServer(s -> {
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            Arrow arrow = s.overworld().getEntities(EntityType.ARROW, a -> true).getFirst();
            check(pig.position().distanceTo(held[0]) < 0.01, "The frozen pig moved");
            check(arrow.position().distanceTo(held[1]) < 0.01, "The frozen arrow moved");
        });
        context.waitTicks(SpellTuning.CHRONO_DURATION_TICKS - 25 + 3);
        server.runOnServer(s -> {
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            float expected = pig.getMaxHealth() - 4 * SpellTuning.CHRONO_DAMAGE_MULTIPLIER;
            check(!ChronoField.isFrozen(pig) && !pig.isNoAi(), "The pig didn't thaw");
            check(Math.abs(pig.getHealth() - expected) < 0.01, "Held damage left the pig at " + pig.getHealth() + ", expected " + expected);
            List<? extends Arrow> arrows = s.overworld().getEntities(EntityType.ARROW, a -> true);
            check(arrows.isEmpty() || arrows.getFirst().position().distanceTo(held[1]) > 1, "The arrow didn't fly on");
        });
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    private static void astralGate(ClientGameTestContext context, TestServerContext server) {
        server.runCommand(PLATEAU);
        server.runCommand(CASTER_AT);
        server.runCommand("summon minecraft:pig 1.5 -43 4.5 {NoAI:1b}");
        context.waitTicks(3);
        Vec3 far = new Vec3(0.5, -43, 44.5);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setAttached(ModAttachments.CASTER, CasterState.FULL);
            player.setXRot(-90);
            check(!SpellCasting.cast(s.overworld(), player, ModSpells.ASTRAL_GATE, 1), "A gate opened to the sky");
            player.lookAt(EntityAnchorArgument.Anchor.EYES, new Vec3(0.5, -43, 9));
            check(!SpellCasting.cast(s.overworld(), player, ModSpells.ASTRAL_GATE, 1), "A gate opened 5 blocks away");
            check(castAt(s, ModSpells.ASTRAL_GATE, far), "Astral Gate was refused");
        });
        context.waitTicks(5);
        context.takeScreenshot("astral-gate");
        context.waitTicks(SpellTuning.GATE_CROSS_TICKS + 5);
        server.runOnServer(s -> {
            check(player(s).position().distanceTo(far) < 1, "The caster arrived at " + player(s).position());
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            check(pig.position().distanceTo(far) < 2.5, "The pig in the gate stayed at " + pig.position());
            float spent = SpellTuning.BASE_MAX_MANA - CasterState.of(player(s)).manaAt();
            check(Math.abs(spent - SpellTuning.GATE_MANA) < 0.01, "The gate cost " + spent + " mana");
        });
        server.runCommand("tp @a 0.5 -43 44.5 180 10");
        context.waitTicks(3);
        context.takeScreenshot("astral-gate-arrived");
    }

    /** Casts {@code spell} at {@code at} from wherever the caster stands, aimed in the same tick, with a full pool. */
    private static boolean castAt(MinecraftServer server, Spell spell, Vec3 at) {
        ServerPlayer player = player(server);
        player.setAttached(ModAttachments.CASTER, CasterState.FULL);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, at);
        return SpellCasting.cast(server.overworld(), player, spell, 1);
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
