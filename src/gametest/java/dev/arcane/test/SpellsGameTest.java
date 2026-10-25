package dev.arcane.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.spell.Spell;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.ArcaneWard;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;

/**
 * Every spell end to end in a real client: picking a spell on the wheel with simulated input, casting it with a real
 * right-click, and each spell's effect on the world, mana and cooldowns. Screenshots record the visuals.
 */
public final class SpellsGameTest implements FabricClientGameTest {
    private static final int GLFW_MOUSE_BUTTON_RIGHT = 1;

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set midnight");
            server.runCommand("gamemode survival @a");
            server.runCommand("give @a arcane_spells:arcane_staff");
            // Three pigs in a zigzag, each within leaping range of the next; the player looks down at the first.
            server.runCommand("summon minecraft:pig 0 -60 -1 {NoAI:1b}");
            server.runCommand("summon minecraft:pig 3 -60 1 {NoAI:1b}");
            server.runCommand("summon minecraft:pig 6 -60 3 {NoAI:1b}");
            server.runCommand("tp @a 0 -60 -6 0 10");
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(10);

            // Assets: every mod sound resolves to a file, and every spell icon made it into the GUI atlas. Either
            // mistake is otherwise silent: a missing sound plays nothing, a missing icon draws the magenta square.
            context.runOnClient(client -> {
                int sounds = 0;
                for (Identifier id : BuiltInRegistries.SOUND_EVENT.keySet()) {
                    if (!id.getNamespace().equals(ArcaneSpells.ID)) continue;
                    WeighedSoundEvents event = client.getSoundManager().getSoundEvent(id);
                    check(event != null, "No sounds.json entry for " + id);
                    Identifier file = event.getSound(RandomSource.create()).getPath();
                    check(client.getResourceManager().getResource(file).isPresent(), "Missing sound file " + file);
                    sounds++;
                }
                check(sounds > 0, "No spell sounds registered");
                // Every take sounds.json lists, not just one drawn at random: the commonest sounds have three.
                try (var reader = client.getResourceManager().getResourceOrThrow(ArcaneSpells.id("sounds.json")).openAsReader()) {
                    for (var event : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                        for (JsonElement take : event.getValue().getAsJsonObject().getAsJsonArray("sounds")) {
                            Identifier file = Identifier.parse(take.getAsString()).withPath(path -> "sounds/" + path + ".ogg");
                            check(client.getResourceManager().getResource(file).isPresent(), "Missing sound file " + file + " for " + event.getKey());
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                TextureAtlas gui = client.getAtlasManager().getAtlasOrThrow(AtlasIds.GUI);
                for (Spell spell : SpellRegistries.SPELLS) {
                    check(gui.getSprite(spell.icon()) != gui.missingSprite(), "Missing icon for " + spell);
                }
            });

            // Spell wheel: hold the key, point towards Chain Lightning (second slot, up and to the right), release.
            // The test mod's spell makes six slots, so the second is centred 60° clockwise from straight up.
            context.getInput().holdKey(InputConstants.KEY_R);
            context.waitForScreen(SpellWheelScreen.class);
            context.getInput().moveCursor(190, -60);
            context.waitTicks(2);
            context.takeScreenshot("spells-wheel");
            context.getInput().releaseKey(InputConstants.KEY_R);
            context.waitForScreen(null);
            context.waitTicks(5);
            server.runOnServer(s -> check(SpellCasting.selected(SpellCasting.heldStaff(player(s))) == ModSpells.CHAIN_LIGHTNING,
                    "Spell wheel did not select Chain Lightning"));

            // Chain Lightning through the real item: a right-click strikes all three pigs.
            context.getInput().pressMouse(GLFW_MOUSE_BUTTON_RIGHT);
            context.waitTicks(3);
            context.takeScreenshot("spells-chain");
            server.runOnServer(s -> {
                var pigs = s.overworld().getEntities(EntityType.PIG, pig -> true);
                check(pigs.size() == 3, "Expected 3 pigs, found " + pigs.size());
                for (var pig : pigs) check(pig.getHealth() < pig.getMaxHealth(), "Chain Lightning missed a pig at " + pig.blockPosition());
                // Mana as of the cast, before regeneration since: a full pool minus the cost.
                float spent = SpellTuning.BASE_MAX_MANA - CasterState.of(player(s)).manaAt();
                check(Math.abs(spent - SpellTuning.CHAIN_MANA) < 0.01, "Chain Lightning spent " + spent + " mana");
            });

            // Frost Nova: a fresh pig beside the player is hurt, slowed and frozen solid.
            server.runCommand("kill @e[type=minecraft:pig]");
            server.runCommand("summon minecraft:pig 2 -60 -4 {NoAI:1b}");
            server.runCommand("tp @a 0 -60 -6 0 30");
            context.waitTicks(3);
            server.runOnServer(s -> check(cast(s, ModSpells.FROST_NOVA), "Frost Nova was refused"));
            context.waitTicks(4);
            context.takeScreenshot("spells-nova");
            server.runOnServer(s -> {
                // Pigs killed above linger through their death animation; only the new one is alive.
                var pig = s.overworld().getEntities(EntityType.PIG, p -> p.isAlive()).getFirst();
                check(pig.getHealth() < pig.getMaxHealth(), "Frost Nova did not hurt the pig");
                check(pig.isFullyFrozen(), "Frost Nova did not freeze the pig");
                check(pig.hasEffect(MobEffects.SLOWNESS), "Frost Nova did not slow the pig");
            });

            // Blink: straight ahead over open ground lands 16 blocks on, on the ground.
            server.runCommand("kill @e[type=minecraft:pig]");
            server.runCommand("tp @a 0 -60 -6 0 0");
            context.waitTicks(3);
            server.runOnServer(s -> check(cast(s, ModSpells.BLINK), "Blink was refused"));
            context.waitTicks(3);
            context.takeScreenshot("spells-blink");
            server.runOnServer(s -> {
                Vec3 at = player(s).position();
                check(at.z > 8 && Math.abs(at.y + 60) < 0.01, "Blink landed at " + at);
            });

            // Arcane Ward: absorbs a hit from outside, then can't be recast while on cooldown.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(cast(s, ModSpells.ARCANE_WARD), "Arcane Ward was refused");
                var generic = s.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.GENERIC);
                player.hurtServer(s.overworld(), new DamageSource(generic, player.position().add(3, 0, 0)), 4);
                check(player.getHealth() == player.getMaxHealth(), "Arcane Ward let a hit through");
                check(Math.abs(ArcaneWard.strength(player) - (SpellTuning.WARD_STRENGTH - 4)) < 0.01, "Arcane Ward did not absorb the hit");
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.ARCANE_WARD, 1), "Arcane Ward ignored its cooldown");
            });
            context.waitTicks(10);
            context.takeScreenshot("spells-ward");
            // Third person shows the ward as other players see it.
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_BACK));
            context.waitTicks(3);
            context.takeScreenshot("spells-ward-third-person");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            // Out of mana: the cast is refused and says why.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, new CasterState(0, s.overworld().getGameTime(), SpellTuning.BASE_MAX_MANA, SpellTuning.BASE_MANA_REGEN, Map.of()));
                check(SpellCasting.blocker(player, ModSpells.ARCANE_BOLT) != null, "No reason given for an unaffordable cast");
                check(!SpellCasting.cast(s.overworld(), player, ModSpells.ARCANE_BOLT, 1), "Cast without mana");
            });
            context.waitTicks(40);
            context.takeScreenshot("spells-hud");
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    /** Casts with a full pool and no cooldowns, so each step stands alone. */
    private static boolean cast(MinecraftServer server, Spell spell) {
        ServerPlayer player = player(server);
        CasterState.set(player, CasterState.FULL);
        return SpellCasting.cast(server.overworld(), player, spell, 1);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
