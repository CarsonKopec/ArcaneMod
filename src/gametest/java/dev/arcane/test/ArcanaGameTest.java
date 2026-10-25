package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.SpellAttributes;
import dev.arcane.api.event.SpellEvents;
import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.client.gui.SpellBookScreen;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.progression.ArcaneTalent;
import dev.arcane.progression.Corruption;
import dev.arcane.progression.PlayerProgressionComponent;
import dev.arcane.registry.ModEffects;
import dev.arcane.registry.ModEnchantments;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModPotions;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.ArcaneWard;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * The mechanics around the spells: spell power and the staff enchantments, the Shatter and Conduction reactions,
 * corruption's power and backlash, Clarity, the potions and their brewing, {@code /arcane}, and the advancements. It
 * photographs the corruption bar and haze, the grimoire's corruption card and an Astral spell's page, the new effects
 * in the inventory, and the advancement tab.
 */
public final class ArcanaGameTest implements FabricClientGameTest {
    private static final float EPSILON = 0.01f;
    /** What Chain Lightning's overload deals a lone target, before spell power: every leap but the first. */
    private static final float LONE_OVERLOAD = SpellTuning.CHAIN_OVERLOAD_DAMAGE * (SpellTuning.CHAIN_MAX_TARGETS - 1);

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set noon");
            server.runCommand("weather clear 1000000");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0.5 -60 0.5 0 0");
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(5);

            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(player.getAttributeValue(SpellAttributes.SPELL_POWER) == 1, "Spell power doesn't start at 1");
                check(SpellCasting.spellPower(player, ModSpells.ARCANE_BOLT) == 1, "An unenchanted caster has extra spell power");
                checkEnchantingTable(s);
                player.setItemInHand(InteractionHand.MAIN_HAND, enchantedStaff(s, 3, 3, 2));
                checkEnchantments(player);
            });

            // Spell power reaches the damage: Frost Nova from a Potency III staff, 30% harder.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                Pig pig = pig(s.overworld(), 2.5, -60, 0.5);
                ready(player);
                check(ArcaneSpellsApi.cast(player, ModSpells.FROST_NOVA, 1), "Frost Nova was refused");
                checkNear(pig.getHealth(), 100 - SpellTuning.NOVA_DAMAGE * 1.3f, "Potency III's Frost Nova");
                pig.discard();
            });

            // Shatter: Chain Lightning on a frozen pig hits half again as hard, and breaks the ice.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                Pig pig = pig(s.overworld(), 0.5, -60, 8.5);
                pig.setTicksFrozen(pig.getTicksRequiredToFreeze() + 100);
                check(pig.isFullyFrozen(), "The pig didn't freeze");
                player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
                ready(player);
                check(ArcaneSpellsApi.cast(player, ModSpells.CHAIN_LIGHTNING, 1), "Chain Lightning was refused");
                // Alone, the pig takes the chain's four unspent leaps too, as its overload.
                checkNear(pig.getHealth(), 100 - SpellTuning.CHAIN_DAMAGE * 1.3f * SpellTuning.SHATTER_MULTIPLIER - LONE_OVERLOAD * 1.3f,
                        "A shattering bolt");
                check(pig.getTicksFrozen() == 0, "Shatter left the pig frozen");
                pig.discard();
            });

            // Conduction: a pig standing in water conducts it.
            BlockPos pool = new BlockPos(-3, -61, 8);
            server.runOnServer(s -> {
                s.overworld().setBlockAndUpdate(pool, Blocks.WATER.defaultBlockState());
                // A still pig doesn't fall: put it in the water.
                pig(s.overworld(), -2.5, -61, 8.5).addTag("conductor");
            });
            context.waitTicks(3);
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                Pig pig = (Pig) s.overworld().getEntities(EntityType.PIG, p -> p.getTags().contains("conductor")).getFirst();
                check(pig.isInWater(), "The pig isn't in the water");
                // Its middle is below the ground around the pool: aim over the top, which the aim assist still catches.
                player.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter().with(Direction.Axis.Y, pig.getBoundingBox().maxY + 0.2));
                ready(player);
                check(ArcaneSpellsApi.cast(player, ModSpells.CHAIN_LIGHTNING, 1), "Chain Lightning was refused");
                checkNear(pig.getHealth(), 100 - SpellTuning.CHAIN_DAMAGE * 1.3f * SpellTuning.CONDUCTION_MULTIPLIER - LONE_OVERLOAD * 1.3f,
                        "A conducted bolt");
                pig.discard();
                s.overworld().setBlockAndUpdate(pool, Blocks.GRASS_BLOCK.defaultBlockState());
            });
            checkArcaneUpgrades(context, server);

            server.runOnServer(s -> {
                checkCorruption(s, player(s));
                checkPotions(s, player(s));
            });
            checkDecay(context, server);
            checkCommand(context, server);

            // What a corrupt caster sees: the corruption bar over the mana bar, and the haze at the edges.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ASTRAL_STAFF));
                ArcaneProgression.get(player).setCorruption(85);
                ArcaneSpellsApi.addMana(player, -40);
                player.removeAllEffects();
            });
            context.waitTicks(5);
            context.runOnClient(client -> check(ArcaneProgression.get(client.player).corruption() >= 80, "Corruption didn't sync"));
            quiet(context);
            context.takeScreenshot("arcana-hud-corruption");

            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitForScreen(SpellBookScreen.class);
            context.waitTicks(3);
            click(context, "spell-arcane_spells-planetfall");
            reveal(context, "book-spell-power");
            quiet(context);
            context.takeScreenshot("arcana-book-astral-spell");
            click(context, "book-tab-progression");
            reveal(context, "corruption-card");
            quiet(context);
            context.takeScreenshot("arcana-book-corruption");
            context.runOnClient(client -> client.setScreen(null));

            // A real Astral cast: power from corruption below the threshold, then the corruption it builds.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ASTRAL_STAFF));
                PlayerProgressionComponent progression = ArcaneProgression.get(player);
                progression.setCorruption(40);
                ready(player);
                float cost = SpellCasting.manaCost(player, ModSpells.CHRONO_FIELD);
                check(ArcaneSpellsApi.cast(player, ModSpells.CHRONO_FIELD, 1), "Chrono Field was refused");
                SpellContext cast = TestAddon.lastContext;
                check(cast != null && cast.spellPower() == 1 + 40 * SpellTuning.CORRUPTION_POWER_PER_POINT,
                        "Chrono Field wasn't cast at corruption's power: " + (cast == null ? null : cast.spellPower()));
                check(progression.corruption() == 40 + Corruption.buildup(cost),
                        "Chrono Field built the wrong corruption: " + progression.corruption());
                checkAdvancements(s, player);
            });
            context.waitTicks(5);

            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                player.addEffect(new MobEffectInstance(ModEffects.ARCANE_FLOW, 2400, 1));
                player.addEffect(new MobEffectInstance(ModEffects.CLARITY, 2400));
            });
            context.waitTicks(3);
            context.runOnClient(client -> client.setScreen(new InventoryScreen(client.player)));
            context.waitTicks(3);
            quiet(context);
            context.takeScreenshot("arcana-effects");

            context.runOnClient(client -> {
                var advancements = client.player.connection.getAdvancements();
                var root = advancements.getTree().get(ArcaneSpells.id("root"));
                check(root != null, "The client has no Arcane Spells advancement tab");
                client.setScreen(new AdvancementsScreen(advancements));
                advancements.setSelectedTab(root.holder(), true);
            });
            context.getInput().setCursorPos(0, 0);
            context.waitTicks(5);
            quiet(context);
            context.takeScreenshot("arcana-advancements");
            context.runOnClient(client -> client.setScreen(null));
        }
    }

    /** The enchanting table offers a staff the staff enchantments, and nothing else; and they fit only staffs. */
    private static void checkEnchantingTable(MinecraftServer s) {
        Registry<Enchantment> enchantments = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        Set<ResourceKey<Enchantment>> ours = Set.of(ModEnchantments.POTENCY, ModEnchantments.THRIFT, ModEnchantments.CELERITY);
        for (ResourceKey<Enchantment> key : ours) {
            Enchantment enchantment = enchantments.getValueOrThrow(key);
            check(enchantment.canEnchant(new ItemStack(ModItems.ARCANE_STAFF)) && enchantment.canEnchant(new ItemStack(ModItems.ASTRAL_STAFF)),
                    key + " doesn't fit the staffs");
            check(!enchantment.canEnchant(new ItemStack(Items.DIAMOND_SWORD)), key + " fits a sword");
        }
        var table = enchantments.getTagOrEmpty(EnchantmentTags.IN_ENCHANTING_TABLE);
        Set<ResourceKey<Enchantment>> offered = EnchantmentHelper.getAvailableEnchantmentResults(30, new ItemStack(ModItems.ARCANE_STAFF),
                        java.util.stream.StreamSupport.stream(table.spliterator(), false)).stream()
                .map(EnchantmentInstance::enchantment).map(holder -> holder.unwrapKey().orElseThrow()).collect(Collectors.toSet());
        check(offered.equals(ours), "The enchanting table offers a staff " + offered);
    }

    /** Potency III, Thrift III and Celerity II on the Arcane Staff in hand: only its own spells feel them. */
    private static void checkEnchantments(ServerPlayer player) {
        checkNear(SpellCasting.spellPower(player, ModSpells.ARCANE_BOLT), 1.3f, "Potency III's spell power");
        checkNear(SpellCasting.manaCost(player, ModSpells.ARCANE_BOLT), SpellTuning.BOLT_MANA * (1 - 3 * SpellTuning.THRIFT_COST_CUT),
                "Thrift III's mana cost");
        check(SpellCasting.cooldown(player, ModSpells.BLINK) == Math.round(SpellTuning.BLINK_COOLDOWN_TICKS * (1 - 2 * SpellTuning.CELERITY_COOLDOWN_CUT)),
                "Celerity II's cooldown: " + SpellCasting.cooldown(player, ModSpells.BLINK));
        check(SpellCasting.manaCost(player, ModSpells.CELESTIAL_JUDGMENT) == SpellTuning.JUDGMENT_MANA
                        && SpellCasting.spellPower(player, ModSpells.CELESTIAL_JUDGMENT) == 1,
                "The Arcane Staff's enchantments reached an Astral spell");
    }

    /** Corruption's power, its backlash chance, a backlash itself, and how often casting at the maximum backlashes. */
    private static void checkCorruption(MinecraftServer s, ServerPlayer player) {
        ServerLevel level = s.overworld();
        PlayerProgressionComponent progression = ArcaneProgression.get(player);
        progression.setCorruption(80);
        checkNear(SpellCasting.spellPower(player, ModSpells.CELESTIAL_JUDGMENT), 1.4f, "Corruption 80's Astral spell power");
        checkNear(SpellCasting.spellPower(player, ModSpells.ARCANE_BOLT), 1.3f, "Corruption's power on an Arcane spell");
        checkNear(Corruption.backlashChance(SpellTuning.BACKLASH_THRESHOLD), 0, "The backlash chance at the threshold");
        checkNear(Corruption.backlashChance(player), 0.3f, "The backlash chance at 80");
        checkNear(Corruption.backlashChance(SpellTuning.CORRUPTION_MAX), 0.5f, "The backlash chance at the maximum");

        // A backlash at the maximum: 12 damage through diamond armour, darkness, and 30 corruption burned off.
        player.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.DIAMOND_CHESTPLATE));
        progression.setCorruption(SpellTuning.CORRUPTION_MAX);
        player.setHealth(20);
        Corruption.backlash(level, player);
        checkNear(player.getHealth(), 20 - Corruption.backlashDamage(SpellTuning.CORRUPTION_MAX), "Health after a backlash through armour");
        check(progression.corruption() == SpellTuning.CORRUPTION_MAX - SpellTuning.BACKLASH_VENT, "A backlash didn't vent corruption");
        check(player.hasEffect(MobEffects.DARKNESS), "A backlash didn't darken the caster's sight");
        player.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

        // Paid Astral casts at the maximum backlash about half the time; under Clarity, or free, or below the threshold, never.
        check(backlashes(level, player, SpellTuning.CORRUPTION_MAX, 60, 40) >= 8, "Too few backlashes at the maximum");
        check(backlashes(level, player, SpellTuning.CORRUPTION_MAX, 60, 40) <= 32, "Too many backlashes at the maximum");
        check(backlashes(level, player, SpellTuning.BACKLASH_THRESHOLD, 60, 20) == 0, "A cast backlashed at the threshold");
        check(backlashes(level, player, SpellTuning.CORRUPTION_MAX, 0, 20) == 0, "A free cast backlashed");
        player.addEffect(new MobEffectInstance(ModEffects.CLARITY, 600));
        check(backlashes(level, player, SpellTuning.CORRUPTION_MAX, 60, 20) == 0, "A cast backlashed under Clarity");
        player.removeEffect(ModEffects.CLARITY);
        player.removeEffect(MobEffects.DARKNESS);
        player.setHealth(player.getMaxHealth());
    }

    /**
     * How many of {@code casts} Chrono Field casts, each spending {@code mana} at {@code corruption}, backlashed. Counted
     * by the corruption a backlash burns off, which is always more than a cast builds: health won't do, since hits in the
     * same tick are absorbed by the first one's invulnerability.
     */
    private static int backlashes(ServerLevel level, ServerPlayer player, int corruption, float mana, int casts) {
        PlayerProgressionComponent progression = ArcaneProgression.get(player);
        int backlashes = 0;
        for (int i = 0; i < casts; i++) {
            progression.setCorruption(corruption);
            player.setHealth(player.getMaxHealth());
            SpellEvents.AFTER_CAST.invoker().afterCast(ModSpells.CHRONO_FIELD, new SpellContext(level, player, 1), mana);
            if (progression.corruption() < corruption) backlashes++;
        }
        return backlashes;
    }

    private static void checkPotions(MinecraftServer s, ServerPlayer player) {
        ServerLevel level = s.overworld();
        // Brewing: from an Awkward Potion, then lengthened and strengthened as vanilla's are.
        checkBrew(s, Potions.AWKWARD, Items.AMETHYST_SHARD, ModPotions.MANA);
        checkBrew(s, ModPotions.MANA, Items.GLOWSTONE_DUST, ModPotions.STRONG_MANA);
        checkBrew(s, Potions.AWKWARD, Items.LAPIS_LAZULI, ModPotions.ARCANE_FLOW);
        checkBrew(s, ModPotions.ARCANE_FLOW, Items.REDSTONE, ModPotions.LONG_ARCANE_FLOW);
        checkBrew(s, ModPotions.ARCANE_FLOW, Items.GLOWSTONE_DUST, ModPotions.STRONG_ARCANE_FLOW);
        checkBrew(s, Potions.AWKWARD, Items.GLOW_INK_SAC, ModPotions.CLARITY);
        checkBrew(s, ModPotions.CLARITY, Items.REDSTONE, ModPotions.LONG_CLARITY);

        // Drinking a Potion of Mana restores 40, and a strong one 80.
        ArcaneSpellsApi.addMana(player, 10 - ArcaneSpellsApi.mana(player));
        PotionContents.createItemStack(Items.POTION, ModPotions.MANA).finishUsingItem(level, player);
        checkNear(ArcaneSpellsApi.mana(player), 10 + SpellTuning.INSTANT_MANA, "Mana after a Potion of Mana");
        ArcaneSpellsApi.addMana(player, -ArcaneSpellsApi.mana(player));
        PotionContents.createItemStack(Items.POTION, ModPotions.STRONG_MANA).finishUsingItem(level, player);
        checkNear(ArcaneSpellsApi.mana(player), 2 * SpellTuning.INSTANT_MANA, "Mana after a strong Potion of Mana");

        // Arcane Flow II: 5 more mana a second.
        player.addEffect(new MobEffectInstance(ModEffects.ARCANE_FLOW, 200, 1));
        checkNear((float) player.getAttributeValue(SpellAttributes.MANA_REGEN),
                SpellTuning.BASE_MANA_REGEN + 2 * SpellTuning.ARCANE_FLOW_MANA_REGEN, "Arcane Flow II's regeneration");
        player.removeEffect(ModEffects.ARCANE_FLOW);
    }

    private static void checkBrew(MinecraftServer s, Holder<Potion> from, net.minecraft.world.item.Item ingredient, Holder<Potion> to) {
        ItemStack brewed = s.potionBrewing().mix(new ItemStack(ingredient), PotionContents.createItemStack(Items.POTION, from));
        var potion = brewed.getOrDefault(net.minecraft.core.component.DataComponents.POTION_CONTENTS, PotionContents.EMPTY).potion();
        check(potion.isPresent() && potion.get().is(to.unwrapKey().orElseThrow()),
                "Brewing " + from.getRegisteredName() + " with " + ingredient + " gave " + potion.map(Holder::getRegisteredName).orElse("nothing"));
    }

    /** Corruption fades a point every two seconds at rest, and five a second under Clarity. */
    private static void checkDecay(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runOnServer(s -> ArcaneProgression.get(player(s)).setCorruption(SpellTuning.CORRUPTION_MAX));
        context.waitTicks(40);
        server.runOnServer(s -> {
            int corruption = ArcaneProgression.get(player(s)).corruption();
            check(corruption >= 98 && corruption < SpellTuning.CORRUPTION_MAX, "Corruption at rest faded to " + corruption);
            ArcaneProgression.get(player(s)).setCorruption(SpellTuning.CORRUPTION_MAX);
            player(s).addEffect(new MobEffectInstance(ModEffects.CLARITY, 200));
        });
        context.waitTicks(40);
        server.runOnServer(s -> {
            int corruption = ArcaneProgression.get(player(s)).corruption();
            check(corruption >= 88 && corruption <= 92, "Corruption under Clarity faded to " + corruption);
            player(s).removeEffect(ModEffects.CLARITY);
        });
    }

    private static void checkCommand(ClientGameTestContext context, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
        server.runCommand("arcane mana set @a 33");
        server.runOnServer(s -> check(Math.abs(ArcaneSpellsApi.mana(player(s)) - 33) < 1, "/arcane mana set gave " + ArcaneSpellsApi.mana(player(s))));
        server.runCommand("arcane mana fill @a");
        server.runOnServer(s -> checkNear(ArcaneSpellsApi.mana(player(s)), ArcaneSpellsApi.maxMana(player(s)), "/arcane mana fill"));
        server.runCommand("arcane corruption set @a 64");
        server.runOnServer(s -> check(ArcaneProgression.get(player(s)).corruption() == 64, "/arcane corruption set"));
        server.runCommand("arcane corruption add @a 100");
        server.runOnServer(s -> check(ArcaneProgression.get(player(s)).corruption() == SpellTuning.CORRUPTION_MAX, "/arcane corruption add"));
        int[] points = new int[1];
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            PlayerProgressionComponent progression = ArcaneProgression.get(player);
            ArcaneSpellsApi.setCooldown(player, ModSpells.BLINK, 200);
            // Fifty practice casts: ten more points, three of them then spent.
            for (int i = 0; i < 50; i++) progression.recordCast(TestAddon.PING, 1);
            points[0] = progression.skillPoints();
            check(progression.learnTalent(ArcaneTalent.RESERVOIR) && progression.learnTalent(ArcaneTalent.RESERVOIR), "Reservoir was refused");
            check(progression.skillPoints() == points[0] - 3, "Reservoir's first two ranks didn't cost three points");
        });
        server.runCommand("arcane cooldowns reset @a");
        server.runCommand("arcane talents reset @a");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            check(ArcaneSpellsApi.cooldownLeft(player, ModSpells.BLINK) == 0, "/arcane cooldowns reset left Blink recharging");
            PlayerProgressionComponent progression = ArcaneProgression.get(player);
            check(progression.rank(ArcaneTalent.RESERVOIR) == 0 && progression.skillPoints() == points[0],
                    "/arcane talents reset didn't refund: " + progression.skillPoints() + " of " + points[0]);
        });
        context.waitTicks(2);
        server.runOnServer(s -> check(ArcaneSpellsApi.maxMana(player(s)) == SpellTuning.BASE_MAX_MANA,
                "Reservoir's mana outlived its reset: " + ArcaneSpellsApi.maxMana(player(s))));
    }

    /** The tab loaded whole, and what was done above earned its advancements. */
    private static void checkAdvancements(MinecraftServer s, ServerPlayer player) {
        List<AdvancementHolder> ours = s.getAdvancements().getAllAdvancements().stream()
                .filter(holder -> holder.id().getNamespace().equals(ArcaneSpells.ID)).toList();
        check(ours.size() == 12, "Expected 12 advancements, loaded " + ours.size());
        for (String name : List.of("first_spell", "first_talent", "backlash", "clarity")) {
            AdvancementHolder holder = s.getAdvancements().get(ArcaneSpells.id(name));
            check(holder != null && player.getAdvancements().getOrStartProgress(holder).isDone(), "Not earned: " + name);
        }
        AdvancementHolder planetfall = s.getAdvancements().get(ArcaneSpells.id("planetfall"));
        check(!player.getAdvancements().getOrStartProgress(planetfall).isDone(), "Planetfall was earned without casting it");
    }

    private static ItemStack enchantedStaff(MinecraftServer s, int potency, int thrift, int celerity) {
        Registry<Enchantment> enchantments = s.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        ItemStack staff = new ItemStack(ModItems.ARCANE_STAFF);
        staff.enchant(enchantments.getOrThrow(ModEnchantments.POTENCY), potency);
        staff.enchant(enchantments.getOrThrow(ModEnchantments.THRIFT), thrift);
        staff.enchant(enchantments.getOrThrow(ModEnchantments.CELERITY), celerity);
        return staff;
    }

    /**
     * The Arcane Staff's spells' extras, cast from a plain staff at their own power: Shardburst, Overload, Afterimage,
     * Rebound and Rime.
     */
    private static void checkArcaneUpgrades(ClientGameTestContext context, TestServerContext server) {
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        context.waitTicks(2);
        // The enchanted staff the rest of the test casts with, put back at the end.
        ItemStack[] held = new ItemStack[1];
        server.runOnServer(s -> held[0] = player(s).getMainHandItem().copy());

        // Shardburst: a fully charged bolt strikes a pig; five more stand at one distance from the blast, so they all
        // take the same splash, and four of them a shard as well. The blast mustn't dig the ground out from under them.
        server.runCommand("gamerule arcane_spells:terrain_damage false");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ARCANE_STAFF));
            Pig target = pig(s.overworld(), 0.5, -60, 12.5);
            target.addTag("shard_target");
            Vec3 center = target.getBoundingBox().getCenter();
            player.lookAt(EntityAnchorArgument.Anchor.EYES, center);
            Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
            Vec3 hit = target.getBoundingBox().inflate(0.25).clip(eye, eye.add(look.scale(20))).orElseThrow();
            Vec3 blast = hit.subtract(look.scale(0.3));
            for (int i = 0; i < 5; i++) {
                double angle = Math.toRadians(i * 45);
                pig(s.overworld(), blast.x + Math.cos(angle) * 5, -60, blast.z + Math.sin(angle) * 5).addTag("shard_seeker");
            }
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.ARCANE_BOLT, 1), "The bolt was refused");
        });
        context.waitTicks(10);
        server.runOnServer(s -> {
            List<? extends Pig> pigs = s.overworld().getEntities(EntityType.PIG, p -> p.getTags().contains("shard_seeker"));
            check(pigs.size() == 5, "Expected five pigs round the blast, found " + pigs.size());
            float highest = (float) pigs.stream().mapToDouble(Pig::getHealth).max().orElseThrow();
            check(highest < 100, "The blast's splash missed the pigs round it");
            long shardHit = pigs.stream().filter(p -> Math.abs(p.getHealth() - (highest - SpellTuning.BOLT_SHARD_DAMAGE)) < 0.3).count();
            check(shardHit == SpellTuning.BOLT_SHARDS, "Expected " + SpellTuning.BOLT_SHARDS + " pigs hit by shards, found " + shardHit
                    + ": " + pigs.stream().map(Pig::getHealth).toList());
            s.overworld().getEntities(EntityType.PIG, p -> true).forEach(Pig::discard);
        });
        server.runCommand("gamerule arcane_spells:terrain_damage true");

        // Overload: the chain leaps from one pig to a second and runs out, so its three unspent leaps discharge round
        // the second, catching the first two blocks away as well.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            Pig first = pig(s.overworld(), 0.5, -60, 8.5);
            Pig second = pig(s.overworld(), 2.5, -60, 8.5);
            player.lookAt(EntityAnchorArgument.Anchor.EYES, first.getBoundingBox().getCenter());
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.CHAIN_LIGHTNING, 1), "Chain Lightning was refused");
            float overload = SpellTuning.CHAIN_OVERLOAD_DAMAGE * (SpellTuning.CHAIN_MAX_TARGETS - 2);
            checkNear(first.getHealth(), 100 - SpellTuning.CHAIN_DAMAGE - overload, "The first pig after an overload");
            checkNear(second.getHealth(), 100 - SpellTuning.CHAIN_DAMAGE * SpellTuning.CHAIN_DAMAGE_FALLOFF - overload,
                    "The second pig after an overload");
            first.discard();
            second.discard();
        });

        // Afterimage: a pig beside the caster as they blink away is caught when their afterimage bursts.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            pig(s.overworld(), 2.5, -60, 0.5).addTag("beside");
            player.setYRot(0);
            player.setXRot(0);
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.BLINK, 1), "Blink was refused");
            check(player.position().z > 8, "Blink didn't move the caster: " + player.position());
        });
        context.waitTicks(SpellTuning.BLINK_AFTERIMAGE_TICKS / 2);
        server.runOnServer(s -> {
            Pig pig = (Pig) s.overworld().getEntities(EntityType.PIG, p -> p.getTags().contains("beside")).getFirst();
            check(pig.getHealth() == 100, "The afterimage burst early");
        });
        context.waitTicks(SpellTuning.BLINK_AFTERIMAGE_TICKS / 2 + 3);
        server.runOnServer(s -> {
            Pig pig = (Pig) s.overworld().getEntities(EntityType.PIG, p -> p.getTags().contains("beside")).getFirst();
            checkNear(pig.getHealth(), 100 - SpellTuning.BLINK_AFTERIMAGE_DAMAGE, "The pig beside the afterimage");
            pig.discard();
        });
        server.runCommand("tp @a 0.5 -60 0.5 0 0");
        context.waitTicks(2);

        // Rebound: a blow too hard for the ward breaks it, and it bursts outwards into the pig that struck.
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            Pig attacker = pig(s.overworld(), 2.5, -60, 0.5);
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.ARCANE_WARD, 1), "Arcane Ward was refused");
            player.hurtServer(s.overworld(), s.overworld().damageSources().mobAttack(attacker), SpellTuning.WARD_STRENGTH + 5);
            check(player.getHealth() == player.getMaxHealth(), "The blow that broke the ward got through");
            check(ArcaneWard.strength(player) == 0, "The ward survived a blow harder than its strength");
            checkNear(attacker.getHealth(), 100 - SpellTuning.WARD_REBOUND_DAMAGE, "The pig the ward rebounded into");
            attacker.discard();
        });

        // Rime: still water nearby freezes, a fire goes out, and so does a burning pig; with terrain_damage off,
        // only the pig.
        BlockPos water = new BlockPos(2, -61, 2), fire = new BlockPos(-2, -60, 2);
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            s.overworld().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            s.overworld().setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
            Pig burning = pig(s.overworld(), 0.5, -60, 3.5);
            burning.igniteForSeconds(30);
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.FROST_NOVA, 1), "Frost Nova was refused");
            check(s.overworld().getBlockState(water).is(Blocks.FROSTED_ICE), "The water didn't freeze");
            check(s.overworld().getBlockState(fire).isAir(), "The fire didn't go out");
            check(!burning.isOnFire(), "The pig is still burning");
            burning.discard();
            s.overworld().setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
            s.overworld().setBlockAndUpdate(fire, Blocks.FIRE.defaultBlockState());
        });
        server.runCommand("gamerule arcane_spells:terrain_damage false");
        server.runOnServer(s -> {
            ServerPlayer player = player(s);
            ready(player);
            check(ArcaneSpellsApi.cast(player, ModSpells.FROST_NOVA, 1), "Frost Nova was refused");
            check(s.overworld().getBlockState(water).is(Blocks.WATER), "Water froze with terrain_damage off");
            check(s.overworld().getBlockState(fire).is(Blocks.FIRE), "A fire went out with terrain_damage off");
            s.overworld().setBlockAndUpdate(water, Blocks.GRASS_BLOCK.defaultBlockState());
            s.overworld().setBlockAndUpdate(fire, Blocks.AIR.defaultBlockState());
            player.setTicksFrozen(0);
            player.setItemInHand(InteractionHand.MAIN_HAND, held[0]);
        });
        server.runCommand("gamerule arcane_spells:terrain_damage true");
    }

    /** A still pig with 100 health, its feet at {@code x}, {@code y}, {@code z}: on the ground at y -60. */
    private static Pig pig(ServerLevel level, double x, double y, double z) {
        Pig pig = EntityType.PIG.create(level, EntitySpawnReason.COMMAND);
        check(pig != null, "No pig");
        pig.setNoAi(true);
        pig.snapTo(x, y, z, 0, 0);
        pig.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        pig.setHealth(100);
        level.addFreshEntity(pig);
        return pig;
    }

    /** Full mana and nothing recharging. */
    private static void ready(ServerPlayer player) {
        ArcaneSpellsApi.addMana(player, ArcaneSpellsApi.maxMana(player));
        for (Spell spell : SpellCasting.spells()) ArcaneSpellsApi.setCooldown(player, spell, 0);
    }

    /** Clears the advancement toasts and chat that would cover a screenshot. */
    private static void quiet(ClientGameTestContext context) {
        context.runOnClient(client -> {
            client.getToastManager().clear();
            client.gui.getChat().clearMessages(false);
        });
        context.waitTicks(2);
    }

    private static void click(ClientGameTestContext context, String id) {
        reveal(context, id);
        context.runOnClient(client -> {
            UIComponent component = root(client.screen).childById(UIComponent.class, id);
            MouseButtonEvent click = new MouseButtonEvent(component.x() + component.width() / 2.0,
                    component.y() + component.height() / 2.0, new MouseButtonInfo(0, 0));
            check(client.screen.mouseClicked(click, false), "owo component ignored the click: " + id);
            client.screen.mouseReleased(click);
        });
        context.waitTicks(3);
    }

    private static void reveal(ClientGameTestContext context, String id) {
        context.runOnClient(client -> {
            UIComponent component = root(client.screen).childById(UIComponent.class, id);
            check(component != null, "Missing owo component " + id);
            for (ParentUIComponent parent = component.parent(); parent != null; parent = parent.parent()) {
                if (parent instanceof ScrollContainer<?> scroll) scroll.scrollTo(component);
            }
        });
        context.waitTicks(12);
    }

    private static ParentUIComponent root(Screen screen) {
        check(screen != null, "No screen is open");
        return screen.children().stream().filter(OwoUIAdapter.class::isInstance).map(OwoUIAdapter.class::cast)
                .map(adapter -> (ParentUIComponent) adapter.rootComponent).findFirst()
                .orElseThrow(() -> new AssertionError("Screen has no owo adapter: " + screen.getClass().getSimpleName()));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void checkNear(float actual, float expected, String what) {
        check(Math.abs(actual - expected) < EPSILON, what + ": expected " + expected + ", got " + actual);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
