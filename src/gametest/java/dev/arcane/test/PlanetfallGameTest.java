package dev.arcane.test;

import de.nexusrealms.nebulon.api.render.client.post.NebulonPostProcessing;
import dev.arcane.ArcaneSpells;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Full Planetfall phase, terrain-permission, protected-block and container-drop regression with visual captures. */
public final class PlanetfallGameTest implements FabricClientGameTest {
    private static final BlockPos MARK = new BlockPos(0, -20, 24);
    private static final BlockPos OBSIDIAN = MARK.offset(7, -7, 0);
    private static final BlockPos BEDROCK = MARK.offset(-6, -6, 0);
    private static final BlockPos CHEST = MARK.offset(10, -3, 0);
    private static final String CASTER = "execute as @a at @s anchored eyes run tp @s 0.5 -19 -50.5 facing 0.5 -19 24.5";
    private static final int STRIKE_TICKS = SpellTuning.PLANETFALL_RIFT_TICKS + SpellTuning.PLANETFALL_FALL_TICKS
            + SpellTuning.PLANETFALL_CARVE_TICKS;

    @Override public void runTest(ClientGameTestContext context) {
        int[] previousFov = new int[1];
        int[] previousRenderDistance = new int[1];
        context.runOnClient(client -> {
            previousFov[0] = client.options.fov().get();
            previousRenderDistance[0] = client.options.renderDistance().get();
            client.options.renderDistance().set(8);
        });
        try (var game = context.worldBuilder().create()) {
            TestServerContext server = game.getServer();
            server.runCommand("time set 6000");
            server.runCommand("weather clear");
            server.runCommand("forceload add -96 -96 112 128");
            // A deep, broad site: the old smaller Astral test plateau clips both the bowl and the outward fractures.
            for (int y = -58; y <= -20; y++)
                server.runCommand("fill -74 " + y + " -50 74 " + y + " 98 minecraft:stone");
            server.runCommand("fill -2 -20 -54 2 -20 -48 minecraft:stone");
            server.runCommand("gamemode survival @a");
            server.runCommand("give @a arcane_spells:astral_staff");
            server.runCommand(CASTER);
            // The harness waits for a full square of chunks, but vanilla streams a rounded region. At wider view
            // distances its square corners never arrive; require the target terrain instead of that impossible wait.
            context.waitTicks(40);
            context.runOnClient(client -> {
                check(client.level.getBlockState(MARK).is(Blocks.STONE), "Planetfall's target terrain has not reached the client");
                check(client.player.getXRot() > 0.5f, "Planetfall's test caster is not aimed down at the mark");
            });
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                player.lookAt(EntityAnchorArgument.Anchor.EYES, MARK.getBottomCenter().add(0, 1, 0));
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.PLANETFALL, 0.5f), "A half-channel Planetfall was cast");
                check(CasterState.of(player).manaAt() == SpellTuning.BASE_MAX_MANA, "A refused Planetfall spent mana");
            });

            server.runCommand("gamemode adventure @a");
            terrainUnchanged(context, server, "Adventure-mode Planetfall changed terrain");
            server.runCommand("gamemode survival @a");
            server.runCommand("gamerule arcane_spells:terrain_damage false");
            terrainUnchanged(context, server, "Planetfall ignored the terrain_damage game rule");
            server.runCommand("gamerule arcane_spells:terrain_damage true");
            // Let the permission-test ash and afterglow expire before photographing a fresh full cast.
            context.waitTicks(220);

            server.runOnServer(s -> {
                ServerLevel level = s.overworld();
                level.setBlockAndUpdate(OBSIDIAN, Blocks.OBSIDIAN.defaultBlockState());
                level.setBlockAndUpdate(BEDROCK, Blocks.BEDROCK.defaultBlockState());
                level.setBlockAndUpdate(CHEST, Blocks.CHEST.defaultBlockState());
                ((ChestBlockEntity) level.getBlockEntity(CHEST)).setItem(0, new ItemStack(Items.DIAMOND));
            });
            server.runCommand("summon minecraft:pig 0.5 -19 24.5 {NoAI:1b}");
            server.runCommand("summon minecraft:pig 62.5 -14 24.5 {NoAI:1b,NoGravity:1b,Tags:[\"planetfall_wave_near\"]}");
            server.runCommand("summon minecraft:pig 68.5 -14 24.5 {NoAI:1b,NoGravity:1b,Tags:[\"planetfall_wave_far\"]}");
            server.runOnServer(s -> {
                for (String tag : new String[] {"planetfall_wave_near", "planetfall_wave_far"}) {
                    Pig pig = waveProbe(s.overworld(), tag);
                    pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
                    pig.setHealth(1000);
                }
            });
            server.runCommand(CASTER);
            server.runOnServer(s -> {
                CasterState.set(player(s), CasterState.FULL);
                SpellCasting.select(player(s), ModSpells.PLANETFALL);
            });
            context.waitTicks(4);
            context.getInput().holdMouse(1);
            context.waitTicks(SpellTuning.PLANETFALL_CHARGE_TICKS + 4);
            context.takeScreenshot("planetfall-channel");
            context.getInput().releaseMouse(1);
            context.waitTicks(3);
            long[] impactAt = new long[1];
            server.runOnServer(s -> {
                long now = s.overworld().getGameTime();
                int cooldown = CasterState.of(player(s)).cooldownLeft(ModSpells.PLANETFALL, now);
                check(cooldown > 0, "The fully charged Planetfall was not cast");
                impactAt[0] = now + cooldown - SpellTuning.PLANETFALL_COOLDOWN_TICKS
                        + SpellTuning.PLANETFALL_RIFT_TICKS + SpellTuning.PLANETFALL_FALL_TICKS;
            });
            server.runCommand("gamemode spectator @a");
            server.runCommand("tp @a 48 45 -8 facing 0 105 65");
            context.runOnClient(client -> client.options.fov().set(80));
            context.waitTicks(32);
            capture(context, "planetfall-rift", new Vec3(0, 105, 65));
            context.waitTicks(SpellTuning.PLANETFALL_RIFT_TICKS - 32 + 28);
            capture(context, "planetfall-emergence", new Vec3(0, 100, 60));
            // From the ground near the mark, halfway through the fall: the planet looming overhead.
            server.runCommand("tp @a 30 -16 -8 facing 0 104 58");
            waitUntil(context, server, impactAt[0] - 40);
            capture(context, "planetfall-looming", new Vec3(0, 104, 58));
            server.runCommand("tp @a 48 30 -8 facing 0 -5 24");
            waitUntil(context, server, impactAt[0] - 14);
            // The screen pass draws the falling planet; it is only built of block displays when it shatters.
            context.runOnClient(client -> {
                check(screenPass(), "Planetfall's screen pass isn't drawing the descent");
                // A few shards are displays; the planet would be hundreds.
                int displays = planetDisplays(client.level);
                check(displays < 64, "Planetfall built the planet of block displays the screen pass replaces: " + displays);
            });
            capture(context, "planetfall-descent", new Vec3(0, 10, 24));
            // The instant of impact: the flash and the impact frame.
            waitUntil(context, server, impactAt[0]);
            capture(context, "planetfall-impact-frame", new Vec3(0, -8, 24));
            waitUntil(context, server, impactAt[0] + 4);
            server.runOnServer(s -> check(waveProbe(s.overworld(), "planetfall_wave_far").getHealth() == 1000,
                    "The outer victim was hit before the pressure front arrived"));
            capture(context, "planetfall-impact", new Vec3(0, -8, 24));
            waitUntil(context, server, impactAt[0] + 20);
            capture(context, "planetfall-shockwave", new Vec3(0, 0, 24));
            waitUntil(context, server, impactAt[0] + 55);
            server.runOnServer(s -> {
                float near = waveProbe(s.overworld(), "planetfall_wave_near").getHealth();
                float far = waveProbe(s.overworld(), "planetfall_wave_far").getHealth();
                check(near > 935 && near < 955, "The near pressure-wave victim was missed or hit repeatedly: " + near);
                check(far > 972 && far < 988, "The far pressure-wave victim was missed or hit repeatedly: " + far);
            });
            capture(context, "planetfall-earth-rupture", new Vec3(0, 12, 24));
            waitUntil(context, server, impactAt[0] + 90);
            capture(context, "planetfall-wide-aftermath", new Vec3(0, 6, 24));
            // A dense site may finish its bounded terrain queue after the visible shockwave; never force a final burst.
            waitUntil(context, server, impactAt[0] + SpellTuning.PLANETFALL_CARVE_TICKS + 120);
            server.runCommand("execute as @a at @s anchored eyes run tp @s 35 40 -4 facing 0 -37 24");
            context.waitTicks(18);
            context.runOnClient(client -> check(client.player.position().distanceToSqr(new Vec3(35, 40, -4)) < 4,
                    "The crater camera teleport has not reached the client"));
            capture(context, "planetfall-crater", new Vec3(0, -37, 24));
            context.runOnClient(client -> check(planetDisplays(client.level) == 0, "Planetfall left client block displays behind"));
            // The screen pass's mushroom cloud lingers a while longer, then the pass must stop.
            waitUntil(context, server, impactAt[0] + 275);
            context.runOnClient(client -> check(!screenPass(), "Planetfall's screen pass outlived the spell"));
            server.runOnServer(PlanetfallGameTest::verifyCrater);
        } finally {
            context.runOnClient(client -> {
                client.options.fov().set(previousFov[0]);
                client.options.renderDistance().set(previousRenderDistance[0]);
            });
        }
    }

    private static void terrainUnchanged(ClientGameTestContext context, TestServerContext server, String failure) {
        server.runCommand(CASTER);
        context.waitTicks(2);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            CasterState.set(player, CasterState.FULL);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, MARK.getBottomCenter().add(0, 1, 0));
            check(SpellCasting.cast(s.overworld(), player, ModSpells.PLANETFALL, 1), "Permission-test Planetfall was refused");
        });
        context.waitTicks(STRIKE_TICKS + 5);
        server.runOnServer(s -> {
            check(s.overworld().getBlockState(MARK).is(Blocks.STONE), failure);
            check(s.overworld().getBlockState(MARK.below(16)).is(Blocks.STONE), failure);
            check(s.overworld().getBlockState(MARK.offset(36, 1, 0)).isAir(), failure + " (raised rim)");
        });
    }

    private static void verifyCrater(MinecraftServer server) {
        ServerLevel level = server.overworld();
        check(level.getBlockState(MARK.offset(-10, -18, 0)).isAir(), "The expanded Planetfall bowl did not cut deeply enough");
        check(level.getBlockState(OBSIDIAN).is(Blocks.OBSIDIAN), "Planetfall destroyed obsidian");
        check(level.getBlockState(BEDROCK).is(Blocks.BEDROCK), "Planetfall destroyed an unbreakable block");
        check(level.getBlockState(MARK.offset(74, 0, 0)).is(Blocks.STONE), "Planetfall escaped its terrain footprint");
        check(level.getBlockState(CHEST).isAir(), "The breakable chest did not get excavated");
        check(!level.getEntitiesOfClass(ItemEntity.class, new AABB(MARK).inflate(35), item -> item.getItem().is(Items.DIAMOND)).isEmpty(),
                "Planetfall silently deleted the excavated chest's contents");
        int hot = 0, lining = 0, raised = 0, fissures = 0, glow = 0;
        for (BlockPos pos : BlockPos.betweenClosed(-71, -53, -47, 71, -8, 95)) {
            BlockState state = level.getBlockState(pos);
            double distance = Math.hypot(pos.getX() - MARK.getX(), pos.getZ() - MARK.getZ());
            if (state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CRYING_OBSIDIAN)) hot++;
            if (state.is(Blocks.BLACKSTONE) || state.is(Blocks.BASALT) || state.is(Blocks.COBBLED_DEEPSLATE)) lining++;
            if (pos.getY() >= -19 && distance > 36 && !state.isAir()) raised++;
            if (pos.getY() == -22 && distance > 50 && state.isAir()) fissures++;
            if (state.is(Blocks.SHROOMLIGHT)) glow++;
        }
        check(hot > 250 && lining > 1200, "Missing scorched strata or connected molten seams: " + hot + "/" + lining);
        check(raised > 300, "Planetfall raised too little rim and ejecta: " + raised);
        check(fissures > 50, "Planetfall left too little branching terrain destruction beyond the rim: " + fissures);
        check(glow > 15, "Planetfall left no glowing, split planetary core: " + glow);
        check(level.getEntities(EntityType.PIG, pig -> pig.isAlive() && pig.position().distanceTo(MARK.getCenter()) < 25).isEmpty(),
                "A pig at the impact survived Planetfall");
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static Pig waveProbe(ServerLevel level, String tag) {
        return level.getEntities(EntityType.PIG, pig -> pig.getTags().contains(tag)).stream().findFirst()
                .orElseThrow(() -> new AssertionError("Missing pressure-wave probe " + tag));
    }

    private static void waitUntil(ClientGameTestContext context, TestServerContext server, long tick) {
        long[] now = new long[1];
        server.runOnServer(s -> now[0] = s.overworld().getGameTime());
        if (tick > now[0]) context.waitTicks((int) (tick - now[0]));
    }

    /** Server teleport rotations can be overwritten by a queued client look packet; pose the actual render camera. */
    private static void capture(ClientGameTestContext context, String name, Vec3 target) {
        context.runOnClient(client -> {
            Vec3 look = target.subtract(client.player.getEyePosition());
            float yaw = (float) Math.toDegrees(Math.atan2(-look.x, look.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(look.y, Math.hypot(look.x, look.z)));
            client.player.setYRot(yaw);
            client.player.setXRot(pitch);
            client.player.yRotO = yaw;
            client.player.xRotO = pitch;
            client.player.setYHeadRot(yaw);
            client.player.yHeadRotO = yaw;
            client.gui.getChat().clearMessages(true);
        });
        context.waitTicks(2);
        context.takeScreenshot(name);
    }

    private static boolean screenPass() {
        return NebulonPostProcessing.registeredPasses().contains(ArcaneSpells.id("planetfall"));
    }

    private static int planetDisplays(ClientLevel level) {
        int displays = 0;
        for (var entity : level.entitiesForRendering()) {
            if (entity.getType() == EntityType.BLOCK_DISPLAY && entity.getId() < 0) displays++;
        }
        return displays;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
