package dev.arcane.test;

import com.mojang.authlib.GameProfile;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.network.LearnTalentPayload;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.progression.ArcaneTalent;
import dev.arcane.progression.PlayerProgressionComponent;
import dev.arcane.progression.SpellSchool;
import dev.arcane.registry.ModAttachments;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

/** CCA save migration, paid-cast progression and talent requests over an actual encoded client/server connection. */
public final class ProgressionGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        AtomicBoolean freeCast = new AtomicBoolean();
        SpellEvents.MODIFY_MANA_COST.register((player, spell, cost) -> freeCast.get() && spell == TestAddon.PING ? 0 : cost);
        try (var server = context.worldBuilder().createServer(); var connection = server.connect()) {
            server.runCommand("gamemode survival @a");
            context.waitTicks(5);
            server.runOnServer(s -> {
                ServerPlayer owner = player(s);
                checkMigration(owner);
                PlayerProgressionComponent progression = ArcaneProgression.get(owner);
                ServerPlayer stranger = new ServerPlayer(s, s.overworld(),
                        new GameProfile(UUID.randomUUID(), "TestObserver"), ClientInformation.createDefault());
                check(progression.shouldSyncWith(owner) && !progression.shouldSyncWith(stranger),
                        "Progression synchronization is not restricted to its owner");
                check(!progression.learnTalent(ArcaneTalent.RESERVOIR), "A talent was bought without points");

                CasterState.set(owner, CasterState.FULL);
                TestAddon.silenced = true;
                try {
                    check(!ArcaneSpellsApi.cast(owner, TestAddon.PING, 1), "The silenced cast succeeded");
                } finally {
                    TestAddon.silenced = false;
                }
                freeCast.set(true);
                try {
                    paidCasts(owner, 1);
                } finally {
                    freeCast.set(false);
                }
                owner.setGameMode(GameType.CREATIVE);
                paidCasts(owner, 1);
                owner.setGameMode(GameType.SURVIVAL);
                progression.recordCast(TestAddon.PING, Float.NaN);
                progression.recordCast(TestAddon.PING, -1);
                check(progression.casts(TestAddon.PING) == 0 && progression.affinity(SpellSchool.ARCANE) == 0,
                        "Failed, free, creative or invalid casts awarded progress");

                paidCasts(owner, 5);
                check(progression.casts(TestAddon.PING) == 5 && progression.mastery(TestAddon.PING) == 1
                                && progression.skillPoints() == 1,
                        "Five successful paid casts did not earn exactly one mastery point");
                check(progression.affinity(SpellSchool.ARCANE) == 50,
                        "Affinity did not use the actual half-price mana spent");
                CasterState.set(owner, new CasterState(37, owner.level().getGameTime(), 100, 5, Map.of()));
            });
            context.waitFor(client -> client.player != null
                    && ArcaneProgression.get(client.player).skillPoints() == 1, 200);
            context.runOnClient(client -> {
                check(ArcaneProgression.get(client.player).casts(TestAddon.PING) == 5,
                        "CCA did not sync the owner's cast counts");
                // A duplicate request cannot use the same point twice; rank and price are server-owned.
                ClientPlayNetworking.send(new LearnTalentPayload(ArcaneTalent.RESERVOIR));
                ClientPlayNetworking.send(new LearnTalentPayload(ArcaneTalent.RESERVOIR));
            });
            context.waitFor(client -> client.player != null
                    && ArcaneProgression.get(client.player).rank(ArcaneTalent.RESERVOIR) == 1, 200);
            context.waitTicks(3);
            server.runOnServer(s -> {
                ServerPlayer owner = player(s);
                PlayerProgressionComponent progression = ArcaneProgression.get(owner);
                check(progression.rank(ArcaneTalent.RESERVOIR) == 1 && progression.skillPoints() == 0,
                        "The server accepted a duplicate/unaffordable talent request");
                check(ArcaneSpellsApi.maxMana(owner) == 120, "Reservoir did not add 20 maximum mana");
                check(ArcaneSpellsApi.mana(owner) < 100, "Increasing capacity refilled the mana pool");

                paidCasts(owner, 45);
                check(progression.mastery(TestAddon.PING) == 10 && progression.skillPoints() == 9,
                        "Mastery point accounting was incorrect before buying ranks");
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ARCANE_STAFF));
                owner.startUsingItem(InteractionHand.MAIN_HAND);
                check(!progression.learnTalent(ArcaneTalent.RESERVOIR), "A talent changed during an active channel");
                owner.stopUsingItem();
                owner.setGameMode(GameType.SPECTATOR);
                check(!progression.learnTalent(ArcaneTalent.RESERVOIR), "A spectator learned a talent");
                owner.setGameMode(GameType.SURVIVAL);

                check(progression.learnTalent(ArcaneTalent.RESERVOIR) && progression.skillPoints() == 7,
                        "The second Reservoir rank did not cost two points");
                check(progression.learnTalent(ArcaneTalent.RESERVOIR) && progression.skillPoints() == 4,
                        "The third Reservoir rank did not cost three points");
                check(!progression.learnTalent(ArcaneTalent.RESERVOIR), "Reservoir exceeded its rank cap");
                check(progression.learnTalent(ArcaneTalent.RECOVERY), "Recovery was refused");
                check(ArcaneSpellsApi.maxMana(owner) == 160 && ArcaneSpellsApi.manaRegen(owner) == 5.5f,
                        "Talent attributes were not applied at their learned ranks");

                int existingCooldown = ArcaneSpellsApi.cooldownLeft(owner, TestAddon.PING);
                check(existingCooldown == TestAddon.PING_COOLDOWN_TICKS, "The setup cast has an unexpected cooldown");
                check(progression.learnTalent(ArcaneTalent.FOCUS) && progression.learnTalent(ArcaneTalent.FOCUS),
                        "Affordable Focus ranks were refused");
                check(progression.skillPoints() == 0 && !progression.learnTalent(ArcaneTalent.FOCUS),
                        "Focus overspent the remaining points");
                check(ArcaneSpellsApi.cooldownLeft(owner, TestAddon.PING) == existingCooldown,
                        "Learning Focus rewrote a running cooldown");
                paidCasts(owner, 1);
                check(ArcaneSpellsApi.cooldownLeft(owner, TestAddon.PING) == 36,
                        "Two Focus ranks did not reduce a new cooldown by 10 percent");
                check(progression.casts(TestAddon.PING) == 51 && progression.mastery(TestAddon.PING) == 10
                                && progression.skillPoints() == 0,
                        "Casts above maximum mastery created extra points");
                checkPersistenceAndRespawn(owner, progression);
            });
            context.waitFor(client -> client.player != null
                    && ArcaneProgression.get(client.player).rank(ArcaneTalent.FOCUS) == 2
                    && ArcaneProgression.get(client.player).casts(TestAddon.PING) == 51
                    && ArcaneSpellsApi.maxMana(client.player) == 160, 200);
            context.runOnClient(client -> {
                PlayerProgressionComponent progression = ArcaneProgression.get(client.player);
                check(progression.rank(ArcaneTalent.RESERVOIR) == 3 && progression.rank(ArcaneTalent.RECOVERY) == 1
                                && progression.casts(TestAddon.PING) == 51 && progression.skillPoints() == 0,
                        "Final progression state did not reach the client");
                check(ArcaneSpellsApi.maxMana(client.player) == 160 && ArcaneSpellsApi.manaRegen(client.player) == 5.5f,
                        "Talent attribute changes did not sync to the client");
                CasterState before = CasterState.of(client.player);
                progression.setCasterState(CasterState.FULL);
                progression.recordCast(TestAddon.PING, 20);
                check(!progression.learnTalent(ArcaneTalent.RECOVERY)
                                && CasterState.of(client.player).equals(before) && progression.casts(TestAddon.PING) == 51,
                        "Client-only writes changed authoritative progression");
            });
        } finally {
            freeCast.set(false);
            TestAddon.silenced = false;
        }
    }

    private static void checkMigration(ServerPlayer owner) {
        long now = owner.level().getGameTime();
        CasterState legacy = new CasterState(23, now, 140, 7,
                Map.of(ModSpells.BLINK, new CasterState.Cooldown(now + 77, 100)));
        PlayerProgressionComponent imported = new PlayerProgressionComponent(owner);
        imported.readData(TagValueInput.create(ProblemReporter.DISCARDING, owner.registryAccess(), new CompoundTag()));
        owner.setAttached(ModAttachments.CASTER, legacy);
        check(imported.casterState().equals(legacy), "The legacy attachment was not imported lazily after component load");
        check(owner.getAttached(ModAttachments.CASTER) == null, "The imported legacy attachment was retained");
        CompoundTag saved = save(owner, imported);
        PlayerProgressionComponent loaded = new PlayerProgressionComponent(owner);
        owner.setAttached(ModAttachments.CASTER, CasterState.FULL);
        loaded.readData(TagValueInput.create(ProblemReporter.DISCARDING, owner.registryAccess(), saved));
        check(loaded.casterState().equals(legacy), "Saved CCA state lost to the stale legacy attachment");
        check(owner.getAttached(ModAttachments.CASTER) == null, "An obsolete legacy attachment survived CCA precedence");
    }

    private static void checkPersistenceAndRespawn(ServerPlayer owner, PlayerProgressionComponent original) {
        PlayerProgressionComponent loaded = new PlayerProgressionComponent(owner);
        loaded.readData(TagValueInput.create(ProblemReporter.DISCARDING, owner.registryAccess(), save(owner, original)));
        check(loaded.casterState().equals(original.casterState()) && loaded.casts(TestAddon.PING) == 51
                        && loaded.rank(ArcaneTalent.RESERVOIR) == 3 && loaded.rank(ArcaneTalent.FOCUS) == 2
                        && loaded.affinity(SpellSchool.ARCANE) == original.affinity(SpellSchool.ARCANE),
                "CCA save/load lost caster state or permanent knowledge");
        PlayerProgressionComponent death = new PlayerProgressionComponent(owner);
        check(death.shouldCopyForRespawn(false, false, true), "Death did not request progression copying");
        death.copyForRespawn(original, owner.registryAccess(), false, false, true);
        check(death.casterState().equals(CasterState.FULL) && death.casts(TestAddon.PING) == 51
                        && death.rank(ArcaneTalent.RESERVOIR) == 3 && death.skillPoints() == original.skillPoints(),
                "Death either lost knowledge or retained mana/cooldowns");
        PlayerProgressionComponent lossless = new PlayerProgressionComponent(owner);
        lossless.copyForRespawn(original, owner.registryAccess(), true, true, true);
        check(lossless.casterState().equals(original.casterState()) && lossless.rank(ArcaneTalent.FOCUS) == 2,
                "Lossless respawn did not preserve caster state and talents");
    }

    private static CompoundTag save(ServerPlayer owner, PlayerProgressionComponent component) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, owner.registryAccess());
        component.writeData(output);
        return output.buildResult();
    }

    private static void paidCasts(ServerPlayer owner, int count) {
        for (int i = 0; i < count; i++) {
            ArcaneSpellsApi.setCooldown(owner, TestAddon.PING, 0);
            ArcaneSpellsApi.addMana(owner, 1000);
            check(ArcaneSpellsApi.cast(owner, TestAddon.PING, 1), "A setup Ping cast was refused");
        }
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
