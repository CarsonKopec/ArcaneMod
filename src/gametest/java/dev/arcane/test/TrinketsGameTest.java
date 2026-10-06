package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellAttributes;
import dev.arcane.registry.ModItems;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketComponent;
import dev.emi.trinkets.api.TrinketInventory;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * The trinkets in Trinkets' slots: each fits only its own slots, its bonus applies while worn (on both sides) and is
 * gone once it comes off, and the inventory shows the slots and the trinkets' tooltips.
 */
public final class TrinketsGameTest implements FabricClientGameTest {
    private static final List<String> SLOTS = List.of("hand/ring", "offhand/ring", "chest/necklace", "legs/belt");

    @Override public void runTest(ClientGameTestContext context) {
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 -60 0 0 0");
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(5);

            int[] baseCooldown = new int[1];
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                TrinketComponent trinkets = trinkets(player);
                for (String slot : SLOTS) check(inventory(trinkets, slot) != null, "Players have no " + slot + " slot");
                // Each trinket fits its own slots and no other.
                checkFits(player, trinkets, ModItems.ARCANE_RING, "hand/ring", "offhand/ring");
                checkFits(player, trinkets, ModItems.ASTRAL_AMULET, "chest/necklace");
                checkFits(player, trinkets, ModItems.CHRONO_CHARM, "legs/belt");
                checkFits(player, trinkets, ModItems.ARCANE_STAFF);
                for (String name : List.of("arcane_ring", "astral_amulet", "chrono_charm")) {
                    check(s.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, ArcaneSpells.id(name))).isPresent(),
                            "No recipe for the " + name);
                }
                baseCooldown[0] = SpellCasting.cooldown(player, ModSpells.BLINK);

                // Worn: a ring on each hand, the amulet and the charm. A third ring has nowhere to go.
                check(TrinketItem.equipItem(player, new ItemStack(ModItems.ARCANE_RING)), "The first ring wasn't worn");
                check(TrinketItem.equipItem(player, new ItemStack(ModItems.ARCANE_RING)), "The second ring wasn't worn");
                check(!TrinketItem.equipItem(player, new ItemStack(ModItems.ARCANE_RING)), "A third ring was worn");
                check(TrinketItem.equipItem(player, new ItemStack(ModItems.ASTRAL_AMULET)), "The amulet wasn't worn");
                check(TrinketItem.equipItem(player, new ItemStack(ModItems.CHRONO_CHARM)), "The charm wasn't worn");
                // A spare ring in the inventory, for its tooltip.
                player.getInventory().setItem(9, new ItemStack(ModItems.ARCANE_RING));
            });
            context.waitTicks(3);

            float maxMana = SpellTuning.BASE_MAX_MANA + 2 * SpellTuning.ARCANE_RING_MAX_MANA;
            float regen = SpellTuning.BASE_MANA_REGEN + SpellTuning.ASTRAL_AMULET_MANA_REGEN;
            int shortened = Math.round(baseCooldown[0] * (1 - SpellTuning.CHRONO_CHARM_COOLDOWN_CUT));
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(player.getAttributeValue(SpellAttributes.MAX_MANA) == maxMana,
                        "Two rings did not add their max mana: " + player.getAttributeValue(SpellAttributes.MAX_MANA));
                check(player.getAttributeValue(SpellAttributes.MANA_REGEN) == regen,
                        "The amulet did not add its regeneration: " + player.getAttributeValue(SpellAttributes.MANA_REGEN));
                check(SpellCasting.cooldown(player, ModSpells.BLINK) == shortened,
                        "The charm did not shorten cooldowns: " + SpellCasting.cooldown(player, ModSpells.BLINK) + " of " + baseCooldown[0]);
                check(CasterState.of(player).maxMana() == maxMana, "The mana pool did not follow the rings");
            });
            // The client sees the same: its HUD reads the synced pool, and its wheel the charm's cooldowns.
            context.waitFor(client -> client.player != null && CasterState.of(client.player).maxMana() == maxMana, 100);
            context.runOnClient(client -> {
                check(SpellCasting.cooldown(client.player, ModSpells.BLINK) == shortened, "The client's cooldowns ignore the charm");
                List<Component> lines = new ItemStack(ModItems.ARCANE_RING).getTooltipLines(Item.TooltipContext.of(client.level),
                        client.player, TooltipFlag.NORMAL);
                String tooltip = String.join("\n", lines.stream().map(Component::getString).toList());
                check(tooltip.contains("Max Mana") && tooltip.contains("one on each hand"), "The ring's tooltip lacks its bonus:\n" + tooltip);
            });

            // Worn, the amulet and the charm show on the player.
            server.runCommand("tp @a 0 -60 0 180 10");
            context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
            context.waitTicks(10);
            context.takeScreenshot("trinkets-worn");
            context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));

            // The inventory: the chest group's slots open from the chestplate slot, showing the amulet worn.
            context.getInput().pressKey(InputConstants.KEY_E);
            context.waitForScreen(InventoryScreen.class);
            hoverSlot(context, 6);
            context.takeScreenshot("trinkets-necklace-slot");
            hoverSlot(context, 9);
            context.takeScreenshot("trinkets-ring-tooltip");
            context.getInput().pressKey(InputConstants.KEY_ESCAPE);
            context.waitForScreen(null);

            // Taken off, every bonus is gone.
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                for (String slot : SLOTS) inventory(trinkets(player), slot).clearContent();
            });
            context.waitTicks(3);
            server.runOnServer(s -> {
                ServerPlayer player = player(s);
                check(player.getAttributeValue(SpellAttributes.MAX_MANA) == SpellTuning.BASE_MAX_MANA
                        && player.getAttributeValue(SpellAttributes.MANA_REGEN) == SpellTuning.BASE_MANA_REGEN,
                        "Bonuses outlived their trinkets");
                check(SpellCasting.cooldown(player, ModSpells.BLINK) == baseCooldown[0], "The charm's cut outlived it");
            });
        }
    }

    /** Moves the cursor over the inventory screen's slot {@code index}: the menu's own slot positions. */
    private static void hoverSlot(ClientGameTestContext context, int index) {
        double[] cursor = context.computeOnClient(client -> {
            InventoryScreen screen = (InventoryScreen) client.screen;
            var slot = screen.getMenu().slots.get(index);
            // The inventory is centred: 176 by 166, with the recipe book closed.
            double x = (screen.width - 176) / 2.0 + slot.x + 8, y = (screen.height - 166) / 2.0 + slot.y + 8;
            // The cursor is set in window pixels; screens lay out in scaled ones.
            return new double[] {x * client.getWindow().getScreenWidth() / screen.width,
                    y * client.getWindow().getScreenHeight() / screen.height};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(6);
    }

    private static void checkFits(ServerPlayer player, TrinketComponent trinkets, Item item, String... fits) {
        ItemStack stack = new ItemStack(item);
        List<String> expected = List.of(fits);
        for (String slot : SLOTS) {
            TrinketInventory inventory = inventory(trinkets, slot);
            boolean fit = TrinketsApi.evaluatePredicateSet(inventory.getSlotType().getValidatorPredicates(), stack,
                    new SlotReference(inventory, 0), player);
            check(fit == expected.contains(slot), item + (fit ? " fits " : " doesn't fit ") + slot);
        }
    }

    private static TrinketInventory inventory(TrinketComponent trinkets, String slot) {
        String[] parts = slot.split("/");
        var group = trinkets.getInventory().get(parts[0]);
        return group == null ? null : group.get(parts[1]);
    }

    private static TrinketComponent trinkets(ServerPlayer player) {
        return TrinketsApi.getTrinketComponent(player).orElseThrow(() -> new AssertionError("Players have no trinkets"));
    }

    private static ServerPlayer player(MinecraftServer server) {
        return server.getPlayerList().getPlayers().getFirst();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
