package dev.arcane.client.gui;

import dev.arcane.ArcaneSpells;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.Spell;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudStatusBarHeightRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The mana bar, a status bar directly above the food bar (vanilla bars above it move up to make room), and the active
 * spell in a slot beside the hotbar, on the side the offhand slot isn't.
 */
public final class SpellHud {
    private static final Identifier MANA_BAR = ArcaneSpells.id("mana_bar");
    private static final Identifier SPELL_SLOT = ArcaneSpells.id("spell_slot");
    private static final Identifier SLOT_LEFT = Identifier.withDefaultNamespace("hud/hotbar_offhand_left");
    private static final Identifier SLOT_RIGHT = Identifier.withDefaultNamespace("hud/hotbar_offhand_right");
    /** Same width as the food bar it sits on. */
    private static final int BAR_WIDTH = 81;

    private SpellHud() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, MANA_BAR, SpellHud::renderManaBar);
        HudStatusBarHeightRegistry.addRight(MANA_BAR, player -> showsMana(player) ? 10 : 0);
        HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, SPELL_SLOT, SpellHud::renderSpellSlot);
    }

    /** With the staff in hand, or while mana is refilling, and only in modes that show health and food. */
    private static boolean showsMana(Player player) {
        Minecraft client = Minecraft.getInstance();
        if (client.gameMode == null || !client.gameMode.canHurtPlayer()) return false;
        return !SpellCasting.heldStaff(player).isEmpty()
                || CasterState.of(player).mana(player.level().getGameTime()) < SpellTuning.MAX_MANA;
    }

    private static void renderManaBar(GuiGraphics graphics, DeltaTracker delta) {
        Player player = Minecraft.getInstance().player;
        if (player == null || !showsMana(player)) return;
        float mana = CasterState.of(player).mana(player.level().getGameTime());
        int right = graphics.guiWidth() / 2 + 91, left = right - BAR_WIDTH;
        int top = graphics.guiHeight() - HudStatusBarHeightRegistry.getHeight(MANA_BAR);
        graphics.fill(left, top + 1, right, top + 8, 0xFF2A1745);
        graphics.fill(left + 1, top + 2, right - 1, top + 7, 0xFF0F0819);
        int inner = BAR_WIDTH - 2;
        int filled = Math.round(inner * mana / SpellTuning.MAX_MANA);
        if (filled > 0) graphics.fillGradient(left + 1, top + 2, left + 1 + filled, top + 7, 0xFFD3A6FF, 0xFF7B35E0);
        // Notches every fifth of the pool make costs easy to judge at a glance.
        for (int i = 1; i < 5; i++) {
            int x = left + 1 + inner * i / 5;
            graphics.fill(x, top + 2, x + 1, top + 7, 0x60000000);
        }
    }

    private static void renderSpellSlot(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || player.isSpectator()) return;
        ItemStack staff = SpellCasting.heldStaff(player);
        if (staff.isEmpty()) return;
        Spell spell = SpellCasting.selected(staff);
        // Vanilla puts the offhand slot on the offhand's side; take the other.
        boolean right = player.getMainArm().getOpposite() == HumanoidArm.LEFT;
        int x = right ? graphics.guiWidth() / 2 + 91 : graphics.guiWidth() / 2 - 91 - 29;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, right ? SLOT_RIGHT : SLOT_LEFT, x, graphics.guiHeight() - 23, 29, 24);
        int itemX = right ? x + 10 : x + 3, itemY = graphics.guiHeight() - 19;
        graphics.renderItem(SpellIcons.icon(spell), itemX, itemY);

        CasterState state = CasterState.of(player);
        long now = player.level().getGameTime();
        float cooldown = state.cooldownLeft(spell, now) / (float) spell.cooldownTicks();
        if (cooldown > 0) {
            int top = itemY + Mth.floor(16 * (1 - cooldown));
            graphics.fill(itemX, top, itemX + 16, itemY + 16, 0x7FFFFFFF);
        }
        if (state.mana(now) < SpellCasting.manaCost(player, spell)) {
            graphics.fill(itemX, itemY, itemX + 16, itemY + 16, 0x80200838);
        }
    }
}
