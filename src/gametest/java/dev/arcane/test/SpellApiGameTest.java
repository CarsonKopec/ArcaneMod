package dev.arcane.test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.SpellAttributes;
import dev.arcane.api.spell.Spell;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.CasterState.Cooldown;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.effect.EffectRenderers;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * The API as another mod sees it, through {@link TestAddon}: its spell on the wheel, cast through the normal checks,
 * its events, its effect reaching a client renderer, the mana and cooldown APIs, the mana attributes, and saved data
 * outliving a removed mod.
 */
public final class SpellApiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        AtomicInteger pings = new AtomicInteger();
        context.runOnClient(client -> {
            // The first renderer fails; the second must still run, and the player must stay connected.
            EffectRenderers.register(TestAddon.PING_EFFECT, (effect, level) -> {
                throw new IllegalStateException("Deliberate failure from SpellApiGameTest");
            });
            EffectRenderers.register(TestAddon.PING_EFFECT, (effect, level) -> pings.incrementAndGet());
        });

        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("gamemode survival @a");
            game.getClientWorld().waitForChunksRender();

            // Registration: built-in spells first, then the addon's. The addon's data adds Ping to the Arcane Staff's tag.
            List<Spell> spells = ArcaneSpellsApi.spells();
            check(spells.size() == 12 && spells.getFirst() == ModSpells.ARCANE_BOLT && spells.getLast() == TestAddon.PING,
                    "Unexpected spell order " + spells);
            List<Spell> onStaff = ArcaneSpellsApi.spells(new ItemStack(ModItems.ARCANE_STAFF));
            check(onStaff.size() == 6 && onStaff.getLast() == TestAddon.PING && !onStaff.contains(ModSpells.CELESTIAL_JUDGMENT),
                    "Unexpected Arcane Staff spells " + onStaff);

            // Casting: the cost listener halves the price, and the after-cast listener sees what was charged.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping was refused");
                check(TestAddon.lastCast == TestAddon.PING, "AFTER_CAST did not fire");
                float expected = TestAddon.PING_MANA / 2;
                check(Math.abs(TestAddon.lastManaSpent - expected) < 0.01, "AFTER_CAST saw " + TestAddon.lastManaSpent + " mana spent");
                // Same tick as the cast, so no mana has regenerated yet.
                float mana = ArcaneSpellsApi.mana(player);
                check(Math.abs(mana - (SpellTuning.BASE_MAX_MANA - expected)) < 0.01, "Ping left " + mana + " mana");
                check(ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING) == TestAddon.PING_COOLDOWN_TICKS, "Ping started no cooldown");
                check(!ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping ignored its cooldown");
            });
            context.waitTicks(3);
            context.runOnClient(client -> check(pings.get() == 1, "Client drew " + pings.get() + " ping effects, expected 1"));

            // A refusal from ALLOW_CAST: reason given, nothing spent, other spells unaffected.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                TestAddon.silenced = true;
                try {
                    Component reason = ArcaneSpellsApi.castBlocker(player, TestAddon.PING);
                    check(reason != null && reason.getString().equals("Silenced"), "ALLOW_CAST gave reason " + reason);
                    check(!ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Silenced Ping was cast");
                    check(ArcaneSpellsApi.mana(player) == SpellTuning.BASE_MAX_MANA, "A refused cast spent mana");
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

            // Attributes: max mana follows the player's attribute, here set by command. Raising it doesn't refill.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(player.getAttribute(SpellAttributes.MAX_MANA) != null && player.getAttribute(SpellAttributes.MANA_REGEN) != null,
                        "Players have no mana attributes");
                CasterState.set(player, CasterState.FULL);
            });
            server.runCommand("attribute @p arcane_spells:max_mana base set 150");
            context.waitTicks(3);
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(ArcaneSpellsApi.maxMana(player) == 150, "Max mana is " + ArcaneSpellsApi.maxMana(player) + ", expected 150");
                float mana = ArcaneSpellsApi.mana(player);
                check(mana >= 100 && mana < 102, "Raising max mana left " + mana + " mana, expected 100 and regenerating");
            });
            context.runOnClient(client -> check(ArcaneSpellsApi.maxMana(client.player) == 150,
                    "Client sees max mana " + ArcaneSpellsApi.maxMana(client.player)));

            // Regeneration, here from a modifier as gear would add, is settled at each change: never back-dated.
            Identifier noRegen = Identifier.fromNamespaceAndPath("arcane_spells_test", "no_regen");
            server.runOnServer(s -> player(s).getAttribute(SpellAttributes.MANA_REGEN)
                    .addTransientModifier(new AttributeModifier(noRegen, -1, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL)));
            context.waitTicks(2);
            server.runOnServer(s -> {
                check(ArcaneSpellsApi.manaRegen(player(s)) == 0, "Mana regen is " + ArcaneSpellsApi.manaRegen(player(s)) + ", expected 0");
                ArcaneSpellsApi.addMana(player(s), -1000);
            });
            context.waitTicks(20);
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(ArcaneSpellsApi.mana(player) == 0, "Mana regenerated to " + ArcaneSpellsApi.mana(player) + " with no regeneration");
                player.getAttribute(SpellAttributes.MANA_REGEN).removeModifier(noRegen);
            });
            context.waitTicks(2);
            server.runOnServer(s -> {
                // A tick or two at the restored 5 per second, not the second spent at zero.
                float mana = ArcaneSpellsApi.mana(player(s));
                check(mana > 0 && mana < 1, "Restoring regeneration gave " + mana + " mana, expected a fraction of one");
                // Lowering the maximum trims mana above it.
                ArcaneSpellsApi.addMana(player(s), 1000);
            });
            server.runCommand("attribute @p arcane_spells:max_mana base set 100");
            context.waitTicks(2);
            server.runOnServer(s -> check(ArcaneSpellsApi.mana(player(s)) == 100, "Lowering max mana left " + ArcaneSpellsApi.mana(player(s))));

            // Cooldown modifiers apply to every spell, at cast time; 0 means none at all.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                CasterState.set(player, CasterState.FULL);
                TestAddon.cooldownScale = 0.5f;
                try {
                    int halved = TestAddon.PING_COOLDOWN_TICKS / 2;
                    check(ArcaneSpellsApi.cooldown(player, TestAddon.PING) == halved,
                            "Ping's cooldown is " + ArcaneSpellsApi.cooldown(player, TestAddon.PING));
                    check(ArcaneSpellsApi.cooldown(player, ModSpells.FROST_NOVA) == SpellTuning.NOVA_COOLDOWN_TICKS / 2,
                            "Frost Nova's cooldown is " + ArcaneSpellsApi.cooldown(player, ModSpells.FROST_NOVA));
                    check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping was refused");
                    check(ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING) == halved,
                            "Ping started " + ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING));
                    // The full length is kept for the sweep, longer than the base cooldown or not.
                    long now = s.overworld().getGameTime();
                    check(CasterState.of(player).cooldownFraction(TestAddon.PING, now) == 1, "A fresh cooldown's sweep isn't full");

                    TestAddon.cooldownScale = 0;
                    CasterState.set(player, CasterState.FULL);
                    check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping was refused");
                    check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "A zero cooldown still blocked a recast");
                    check(!CasterState.of(player).cooldowns().containsKey(TestAddon.PING), "A zero cooldown was stored");

                    // Lengthened past the base: the old clamp would have cut this short.
                    TestAddon.cooldownScale = 3;
                    CasterState.set(player, CasterState.FULL);
                    check(ArcaneSpellsApi.cast(player, TestAddon.PING, 1), "Ping was refused");
                    int tripled = TestAddon.PING_COOLDOWN_TICKS * 3;
                    check(ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING) == tripled,
                            "Ping started " + ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING));
                } finally {
                    TestAddon.cooldownScale = 1;
                }
            });

            // Setting a running cooldown: shorter keeps the full length (the sweep shows progress), 0 clears it.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                long now = s.overworld().getGameTime();
                int length = TestAddon.PING_COOLDOWN_TICKS * 3;
                ArcaneSpellsApi.setCooldown(player, TestAddon.PING, length / 4);
                check(ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING) == length / 4,
                        "setCooldown left " + ArcaneSpellsApi.cooldownLeft(player, TestAddon.PING));
                check(CasterState.of(player).cooldownFraction(TestAddon.PING, now) == 0.25f,
                        "Shortened sweep at " + CasterState.of(player).cooldownFraction(TestAddon.PING, now));
                ArcaneSpellsApi.setCooldown(player, TestAddon.PING, 0);
                check(ArcaneSpellsApi.castBlocker(player, TestAddon.PING) == null, "setCooldown(0) left Ping blocked");
                ArcaneSpellsApi.setCooldown(player, ModSpells.BLINK, 30);
            });
            context.waitTicks(2);
            context.runOnClient(client -> {
                int left = ArcaneSpellsApi.cooldownLeft(client.player, ModSpells.BLINK);
                check(left > 25 && left <= 30, "Client sees " + left + " ticks of Blink cooldown, expected about 28");
            });

            // Removing a mod: a staff's unknown spell reads as Arcane Bolt, and unknown cooldowns are dropped.
            server.runOnServer(s -> {
                check(Spell.CODEC.parse(JsonOps.INSTANCE, new JsonPrimitive("removed_mod:spell")).getOrThrow() == ModSpells.ARCANE_BOLT,
                        "An unknown selected spell did not fall back to Arcane Bolt");
                Map<Spell, Cooldown> cooldowns = Map.of(TestAddon.PING, new Cooldown(100, 40), ModSpells.BLINK, new Cooldown(120, 60));
                JsonElement saved = CasterState.CODEC.encodeStart(JsonOps.INSTANCE, new CasterState(50, 0, 150, 5, cooldowns)).getOrThrow();
                JsonObject removed = new JsonObject();
                removed.addProperty("ready_at", 140L);
                removed.addProperty("length", 80);
                saved.getAsJsonObject().getAsJsonObject("cooldowns").add("removed_mod:spell", removed);
                CasterState loaded = CasterState.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
                check(loaded.cooldowns().equals(cooldowns), "Loaded cooldowns " + loaded.cooldowns());
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
