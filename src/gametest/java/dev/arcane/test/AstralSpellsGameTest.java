package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import de.nexusrealms.nebulon.api.render.client.post.NebulonPostProcessing;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.spell.Spell;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.progression.PlayerProgressionComponent;
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
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Astral Staff's other four spells in a real client, each cast on a stone plateau: Meteor Swarm cratering the
 * ground and hurting what's there, and its seekers finding a lone pig; Singularity dragging creatures and items in, eating the ground and collapsing;
 * Chrono Field freezing a creature and an arrow, holding back damage until time resumes; Astral Gate carrying the
 * caster, and a pig standing in the gate, to the far end. Screenshots record each.
 */
public final class AstralSpellsGameTest implements FabricClientGameTest {
    private static final BlockPos MARK = Plateau.MARK;
    private static final Vec3 MARK_TOP = Plateau.MARK_TOP;
    private static final String CASTER_AT = Plateau.CASTER_AT;
    private static final String VANTAGE = "tp @a 56 -10 -14 facing 0 -10 40";
    /** The whole swarm, the Dread Star last, with time for it to land. */
    private static final int SWARM_TICKS = SpellTuning.METEOR_COUNT * SpellTuning.METEOR_INTERVAL_TICKS + 12
            + (int) (SpellTuning.METEOR_SKY_HEIGHT / SpellTuning.DREAD_STAR_SPEED) + 20;

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set 6000");
            server.runCommand("gamemode survival @a");
            Plateau.build(server);
            server.runCommand("give @a arcane_spells:astral_staff");
            server.runCommand(CASTER_AT);
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // The staff holds all six Astral spells, in order, and the wheel shows them.
            server.runOnServer(s -> {
                List<Spell> held = SpellCasting.spells(SpellCasting.heldStaff(player(s)));
                check(held.equals(List.of(ModSpells.CELESTIAL_JUDGMENT, ModSpells.METEOR_SWARM, ModSpells.SINGULARITY,
                        ModSpells.CHRONO_FIELD, ModSpells.ASTRAL_GATE, ModSpells.PLANETFALL)), "Astral Staff holds " + held);
            });
            context.getInput().holdKey(InputConstants.KEY_R);
            context.waitForScreen(SpellWheelScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("astral-wheel");
            context.getInput().releaseKey(InputConstants.KEY_R);
            context.waitForScreen(null);

            meteorSwarm(context, server);
            meteorSeeking(context, server);
            singularity(context, server);
            chronoField(context, server);
            astralGate(context, server);
        }
    }

    private static void meteorSwarm(ClientGameTestContext context, TestServerContext server) {
        for (int i = 0; i < 5; i++) {
            double angle = i * Math.PI * 2 / 5;
            server.runCommand(String.format("summon minecraft:pig %.1f -43 %.1f {NoAI:1b}", 0.5 + Math.cos(angle) * 10, 40.5 + Math.sin(angle) * 10));
        }
        server.runOnServer(s -> check(castAt(s, ModSpells.METEOR_SWARM, MARK_TOP), "Meteor Swarm was refused"));
        server.runCommand("gamemode spectator @a");
        server.runCommand(VANTAGE);
        context.waitTicks(30);
        context.takeScreenshot("astral-meteors");
        context.waitTicks(SWARM_TICKS - 30);
        context.takeScreenshot("astral-meteors-after");
        server.runOnServer(s -> {
            ServerLevel level = s.overworld();
            int broken = 0;
            for (BlockPos pos : BlockPos.betweenClosed(MARK.offset(-24, -2, -24), MARK.offset(24, 0, 24))) {
                if (level.getBlockState(pos).isAir() && pos.getZ() <= 69) broken++;
            }
            check(broken >= 200, "Meteors broke only " + broken + " blocks of the plateau's top");
            // The Dread Star lands on the mark itself, and its blast is far bigger than a meteor's. Like any explosion it
            // gets only a couple of blocks into stone, so look one layer under the top, which a lone meteor can't reach.
            boolean cratered = false;
            for (BlockPos pos : BlockPos.betweenClosed(MARK.offset(-1, -1, -1), MARK.offset(1, -1, 1))) {
                cratered |= level.getBlockState(pos).isAir();
            }
            check(cratered, "The Dread Star left no crater at the mark");
            // Killed pigs are soon gone altogether, so count the pigs left unharmed.
            long unharmed = level.getEntities(EntityType.PIG, pig -> pig.isAlive() && pig.getHealth() == pig.getMaxHealth()).size();
            check(unharmed < 5, "No pig was hit by " + SpellTuning.METEOR_COUNT + " meteors and the Dread Star");
        });
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    /**
     * Seeking: every other meteor aims at a creature in the area. With one pig there, out near the edge, every seeker
     * aims at it, where by chance alone two or three meteors would land near it. (Among several creatures dying at
     * random, the share left for any one of them is chance too.) The pig takes no harm but logs every blast that
     * reaches it; with terrain_damage off the ground under it stays flat.
     */
    private static void meteorSeeking(ClientGameTestContext context, TestServerContext server) {
        Plateau.build(server);
        server.runCommand(CASTER_AT);
        server.runCommand("gamerule arcane_spells:terrain_damage false");
        Pig[] lone = new Pig[1];
        server.runOnServer(s -> {
            DamageLog.clear();
            lone[0] = DamageLog.spawn(s.overworld(), EntityType.PIG, MARK_TOP.add(-20, 0, 0));
            // This extra swarm's corruption would carry into the spells cast after it, so the caster is left as they were.
            PlayerProgressionComponent progression = ArcaneProgression.get(player(s));
            int corruption = progression.corruption();
            check(castAt(s, ModSpells.METEOR_SWARM, MARK_TOP), "Meteor Swarm was refused");
            progression.setCorruption(corruption);
        });
        context.waitTicks(SWARM_TICKS);
        server.runOnServer(s -> {
            long hits = DamageLog.of(lone[0]).stream().filter(blow -> blow.source().is(DamageTypeTags.IS_EXPLOSION)).count();
            int seekers = SpellTuning.METEOR_COUNT / SpellTuning.METEOR_SEEKING_EVERY;
            check(hits >= seekers * 3 / 4, "Seeking meteors hit the lone pig only " + hits + " times, of " + seekers + " seekers");
            lone[0].discard();
        });
        server.runCommand("gamerule arcane_spells:terrain_damage true");
    }

    private static void singularity(ClientGameTestContext context, TestServerContext server) {
        Plateau.build(server);
        server.runCommand(CASTER_AT);
        // With their AI: a mob without it ignores its own velocity, so nothing could drag it.
        server.runCommand("summon minecraft:pig 12.5 -43 40.5");
        server.runCommand("summon minecraft:pig -11.5 -43 40.5");
        server.runCommand("summon minecraft:pig 0.5 -43 52.5");
        server.runCommand("summon minecraft:item 0.5 -43 30.5 {Item:{id:\"minecraft:diamond\",count:1}}");
        context.waitTicks(5);
        Vec3 center = MARK_TOP.add(0, SpellTuning.SINGULARITY_LIFT, 0);
        server.runOnServer(s -> check(castAt(s, ModSpells.SINGULARITY, MARK_TOP), "Singularity was refused"));
        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a 34 -26 14 facing 0 -38 40");
        context.waitTicks(40);
        context.takeScreenshot("astral-singularity");
        server.runOnServer(s -> {
            for (Pig pig : s.overworld().getEntities(EntityType.PIG, pig -> pig.isAlive())) {
                check(pig.getBoundingBox().getCenter().distanceTo(center) < 8, "A pig wasn't dragged in: " + pig.position());
            }
            List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(20));
            check(!items.isEmpty() && items.getFirst().position().distanceTo(center) < 8, "The diamond wasn't dragged in: " + items);
        });
        // Its last four seconds: a true black hole, drawn by the space-time pass. Seen from above, then edge on.
        context.waitTicks(SpellTuning.SINGULARITY_DURATION_TICKS - 50 - 40);
        context.runOnClient(client -> check(spaceTimePass(), "The black hole isn't being drawn"));
        context.takeScreenshot("astral-black-hole");
        server.runCommand("tp @a 0.5 -36 17 facing 0.5 -37 40.5");
        context.waitTicks(4);
        context.takeScreenshot("astral-black-hole-edge");
        context.waitTicks(50 - 4 + 6);
        context.takeScreenshot("astral-singularity-collapse");
        server.runOnServer(s -> {
            check(s.overworld().getBlockState(MARK).isAir(), "The ground under the singularity wasn't eaten");
            long dead = s.overworld().getEntities(EntityType.PIG, pig -> !pig.isAlive()).size()
                    + 3 - s.overworld().getEntities(EntityType.PIG, pig -> true).size();
            check(dead >= 1, "The singularity crushed no pig");
            // What it swallowed, it gives back.
            List<ItemEntity> items = s.overworld().getEntitiesOfClass(ItemEntity.class, new AABB(center, center).inflate(48),
                    item -> item.getItem().is(Items.DIAMOND));
            check(!items.isEmpty(), "The singularity destroyed the diamond it swallowed");
        });
        context.waitTicks(10);
        context.runOnClient(client -> check(!spaceTimePass(), "The black hole outlived the singularity"));
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    /** Whether the space-time pass, which draws black holes and stopped time, is running. */
    private static boolean spaceTimePass() {
        return NebulonPostProcessing.registeredPasses().contains(ArcaneSpells.id("spacetime"));
    }

    private static void chronoField(ClientGameTestContext context, TestServerContext server) {
        Plateau.build(server);
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
            check(player(s).hasEffect(MobEffects.STRENGTH) && player(s).hasEffect(MobEffects.SPEED), "The caster wasn't empowered in their own field");
            held[0] = pig.position();
            held[1] = arrow.position();
            pig.hurtServer(s.overworld(), s.overworld().damageSources().playerAttack(player(s)), 2);
            check(pig.getHealth() == pig.getMaxHealth(), "A frozen pig took damage at once");
        });
        server.runCommand("gamemode spectator @a");
        server.runCommand("tp @a 14 -36 -2 facing 0 -42 10");
        context.waitTicks(10);
        context.takeScreenshot("astral-chrono");
        context.runOnClient(client -> check(spaceTimePass(), "Chrono Field isn't draining the colour from what it holds"));
        server.runOnServer(s -> {
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            Arrow arrow = s.overworld().getEntities(EntityType.ARROW, a -> true).getFirst();
            check(pig.position().distanceTo(held[0]) < 0.01, "The frozen pig moved");
            check(arrow.position().distanceTo(held[1]) < 0.01, "The frozen arrow moved");
        });
        context.waitTicks(SpellTuning.CHRONO_DURATION_TICKS - 25 + 3);
        server.runOnServer(s -> {
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> true).getFirst();
            float expected = pig.getMaxHealth() - 2 * SpellTuning.CHRONO_DAMAGE_MULTIPLIER;
            check(!ChronoField.isFrozen(pig) && !pig.isNoAi(), "The pig didn't thaw");
            check(Math.abs(pig.getHealth() - expected) < 0.01, "Held damage left the pig at " + pig.getHealth() + ", expected " + expected);
            // Rewind: the arrow, which wasn't the caster's, flies back the way it came, and is the caster's now.
            List<? extends Arrow> arrows = s.overworld().getEntities(EntityType.ARROW, a -> true);
            check(!arrows.isEmpty(), "The arrow vanished when time resumed");
            Arrow arrow = arrows.getFirst();
            check(arrow.getDeltaMovement().z < 0 && arrow.getZ() < held[1].z - 1,
                    "The arrow didn't fly back: at " + arrow.position() + ", moving " + arrow.getDeltaMovement());
            check(arrow.getOwner() == player(s), "The rewound arrow isn't the caster's: " + arrow.getOwner());
        });
        // Colour floods back out from the centre.
        context.waitTicks(3);
        context.takeScreenshot("astral-chrono-resume");
        server.runCommand("kill @e[type=!minecraft:player]");
        server.runCommand("gamemode survival @a");
    }

    private static void astralGate(ClientGameTestContext context, TestServerContext server) {
        Plateau.build(server);
        server.runCommand(CASTER_AT);
        server.runCommand("spawnpoint @a 30 -43 20");
        server.runCommand("summon minecraft:pig 1.5 -43 6.5 {NoAI:1b}");
        context.waitTicks(3);
        Vec3 origin = new Vec3(0.5, -43, 6.5), home = new Vec3(30.5, -43, 20.5), far = new Vec3(0.5, -43, 60.5);

        // Too close is refused; far away opens, and a pig standing in the gate follows.
        server.runOnServer(s -> {
            check(!castAt(s, ModSpells.ASTRAL_GATE, new Vec3(0.5, -43, 11)), "A gate opened 5 blocks away");
            check(castAt(s, ModSpells.ASTRAL_GATE, far), "Astral Gate was refused");
        });
        context.waitTicks(5);
        context.takeScreenshot("astral-gate");
        context.waitTicks(SpellTuning.GATE_CROSS_TICKS + 5);
        server.runOnServer(s -> {
            check(player(s).position().distanceTo(far) < 1.5, "The caster arrived at " + player(s).position());
            Pig pig = s.overworld().getEntities(EntityType.PIG, p -> p.isAlive()).getFirst();
            check(pig.position().distanceTo(far) < 3, "The pig in the gate stayed at " + pig.position());
            float spent = SpellTuning.BASE_MAX_MANA - CasterState.of(player(s)).manaAt();
            check(Math.abs(spent - SpellTuning.GATE_MANA) < 0.01, "The gate cost " + spent + " mana");
            // Astral Wake: both come out quickened.
            for (LivingEntity traveller : List.of(player(s), pig)) {
                MobEffectInstance wake = traveller.getEffect(MobEffects.SPEED);
                check(wake != null && wake.getAmplifier() == SpellTuning.GATE_WAKE_AMPLIFIER, "No Astral Wake on " + traveller + ": " + wake);
            }
        });
        server.runCommand("tp @a 0.5 -43 60.5 180 10");
        context.waitTicks(3);
        context.takeScreenshot("astral-gate-arrived");

        // Both ways: step out of the far gate, then back into it, and come out of the first.
        server.runCommand("tp @a 5.5 -43 60.5");
        context.waitTicks(3);
        server.runCommand("tp @a 0.5 -43 60.5");
        context.waitTicks(4);
        server.runOnServer(s -> check(player(s).position().distanceTo(origin) < 1.5, "Stepping back in went to " + player(s).position()));

        // Looking straight up opens a gate home, to the respawn point.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            CasterState.set(player, CasterState.FULL);
            player.setXRot(-90);
            check(SpellCasting.cast(s.overworld(), player, ModSpells.ASTRAL_GATE, 1), "The gate home was refused");
        });
        context.waitTicks(SpellTuning.GATE_CROSS_TICKS + 5);
        server.runOnServer(s -> check(player(s).position().distanceTo(home) < 1.5, "The gate home went to " + player(s).position()));
    }

    /** Casts {@code spell} at {@code at} from wherever the caster stands, aimed in the same tick, with a full pool. */
    private static boolean castAt(MinecraftServer server, Spell spell, Vec3 at) {
        ServerPlayer player = player(server);
        CasterState.set(player, CasterState.FULL);
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
