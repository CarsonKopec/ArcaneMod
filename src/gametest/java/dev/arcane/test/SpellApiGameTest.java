package dev.arcane.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.spell.Spell;
import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellTuning;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The API as another mod sees it, through {@link TestAddon}: its spell on the wheel, cast through the normal checks,
 * its events, its effect reaching a client renderer, the mana API, and saved data outliving a removed mod.
 */
public final class SpellApiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        AtomicInteger pings = new AtomicInteger();
        context.runOnClient(client -> {
            // The first renderer fails; the second must still run, and the player must stay connected.
            SpellEffectRenderers.register(TestAddon.PING_EFFECT, (effect, level) -> {
                throw new IllegalStateException("Deliberate failure from SpellApiGameTest");
            });
            SpellEffectRenderers.register(TestAddon.PING_EFFECT, (effect, level) -> pings.incrementAndGet());
        });

        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("gamemode survival @a");
            game.getClientWorld().waitForChunksRender();

            // Registration: built-in spells first, then the addon's.
            List<Spell> spells = ArcaneSpellsApi.spells();
            check(spells.size() == 6 && spells.getFirst() == ModSpells.ARCANE_BOLT && spells.getLast() == TestAddon.PING,
                    "Unexpected wheel order " + spells);

            // Casting: the cost listener halves the price, and the after-cast listener sees what was charged.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.setAttached(ModAttachments.CASTER, CasterState.FULL);
                check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping was refused");
                check(TestAddon.lastCast == TestAddon.PING, "AFTER_CAST did not fire");
                float expected = TestAddon.PING_MANA / 2;
                check(Math.abs(TestAddon.lastManaSpent - expected) < 0.01, "AFTER_CAST saw " + TestAddon.lastManaSpent + " mana spent");
                // Same tick as the cast, so no mana has regenerated yet.
                float mana = ArcaneSpellsApi.mana(player);
                check(Math.abs(mana - (SpellTuning.MAX_MANA - expected)) < 0.01, "Ping left " + mana + " mana");
                check(ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING) == TestAddon.PING_COOLDOWN_TICKS, "Ping started no cooldown");
                check(!ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping ignored its cooldown");
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(pings.get() == 1, "Client drew " + pings.get() + " ping effects, expected 1"));

            // A refusal from ALLOW_CAST: reason given, nothing spent, other spells unaffected.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.setAttached(ModAttachments.CASTER, CasterState.FULL);
                TestAddon.silenced = true;
                try {
                    Component reason = ArcaneSpellsApi.castBlocker(player, TestAddon.PING);
                    check(reason != null && reason.getString().equals("Silenced"), "ALLOW_CAST gave reason " + reason);
                    check(!ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Silenced Ping was cast");
                    check(ArcaneSpellsApi.mana(player) == SpellTuning.MAX_MANA, "A refused cast spent mana");
                    check(ArcaneSpellsApi.castBlocker(player, ModSpells.BLINK) == null, "Silence refused another spell");
                } finally {
                    TestAddon.silenced = false;
                }
            });

            // Mana: kept within the pool, and synced to the client.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                ArcaneSpellsApi.addMana(player, -1000);
                check(ArcaneSpellsApi.mana(player) == 0, "Mana went below zero");
                ArcaneSpellsApi.addMana(player, 30);
                check(Math.abs(ArcaneSpellsApi.mana(player) - 30) < 0.01, "Adding 30 mana gave " + ArcaneSpellsApi.mana(player));
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                // A little has regenerated since.
                float mana = ArcaneSpellsApi.mana(client.player);
                check(mana >= 30 && mana < 32, "Client sees " + mana + " mana, expected about 30");
            });

            // Removing a mod: a staff's unknown spell reads as Arcane Bolt, and unknown cooldowns are dropped.
            server.runOnServer(s -> {
                check(Spell.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("removed_mod:spell")).getOrThrow() == ModSpells.ARCANE_BOLT,
                        "An unknown selected spell did not fall back to Arcane Bolt");
                Map<Spell, Long> cooldowns = Map.of(TestAddon.PING, 100L, ModSpells.BLINK, 120L);
                JsonElement saved = CasterState.CODEC.encodeStart(JsonOps.INSTANCE, new CasterState(50, 0, cooldowns)).getOrThrow();
                saved.getAsJsonObject().getAsJsonObject("ready_at").addProperty("removed_mod:spell", 140L);
                CasterState loaded = CasterState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
                check(loaded.readyAt().equals(cooldowns), "Loaded cooldowns " + loaded.readyAt());
            });
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
