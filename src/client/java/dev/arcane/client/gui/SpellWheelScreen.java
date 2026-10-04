package dev.arcane.client.gui;

import dev.arcane.network.SelectSpellPayload;
import dev.arcane.registry.ModSounds;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.Spell;
import dev.arcane.spell.SpellCasting;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Hold the spell key to open, point at a spell, release to pick it. A click also picks, Escape cancels. Opening a
 * screen frees the mouse at the screen centre, so the pointer's direction from the centre chooses the spell.
 */
public final class SpellWheelScreen extends Screen {
    private static final Spell[] SPELLS = Spell.values();
    private static final double SECTOR = 2 * Math.PI / SPELLS.length;
    private static final int RADIUS = 62;
    /** Pointer this close to the centre picks nothing, so a quick tap keeps the current spell. */
    private static final int DEAD_ZONE = 16;
    /** Wide enough that every description fits in two lines above the wheel, even at small window sizes. */
    private static final int DESCRIPTION_WIDTH = 300;

    private final KeyMapping key;
    private final Spell current;
    private Spell hovered;

    public SpellWheelScreen(KeyMapping key, Spell current) {
        super(Component.translatable("screen.arcane_spells.spell_wheel"));
        this.key = key;
        this.current = current;
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    @Override public void tick() {
        // Nothing left to choose for if the staff was dropped, swapped away or the player died.
        if (minecraft.player == null || SpellCasting.heldStaff(minecraft.player).isEmpty()) onClose();
    }

    @Override public boolean keyReleased(KeyEvent event) {
        if (key.matches(event)) {
            choose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (key.matchesMouse(event)) {
            choose();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        choose();
        return true;
    }

    private void choose() {
        if (hovered != null && hovered != current && ClientPlayNetworking.canSend(SelectSpellPayload.TYPE)) {
            ClientPlayNetworking.send(new SelectSpellPayload(hovered));
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.WHEEL_SELECT, 1.0f));
        }
        onClose();
    }

    @Override public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // A light dim rather than the usual blur, so the world stays readable while choosing.
        graphics.fill(0, 0, width, height, 0x50000000);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Player player = minecraft.player;
        if (player == null) return;
        int cx = width / 2, cy = height / 2;
        hovered = spellAt(mouseX - cx, mouseY - cy);
        CasterState state = CasterState.of(player);
        long now = player.level().getGameTime();

        dottedCircle(graphics, cx, cy, RADIUS + 20, 0, 2 * Math.PI, 0x50C9A6FF);
        dottedCircle(graphics, cx, cy, RADIUS - 16, 0, 2 * Math.PI, 0x40C9A6FF);
        if (hovered != null) {
            int accent = SpellIcons.color(hovered);
            double middle = hovered.ordinal() * SECTOR;
            dottedCircle(graphics, cx, cy, RADIUS + 20, middle - SECTOR / 2, middle + SECTOR / 2, accent);
            dottedCircle(graphics, cx, cy, RADIUS + 21, middle - SECTOR / 2, middle + SECTOR / 2, accent);
            pointer(graphics, cx, cy, mouseX, mouseY);
        }
        for (Spell spell : SPELLS) {
            double angle = spell.ordinal() * SECTOR;
            int x = cx + (int) Math.round(Math.sin(angle) * RADIUS);
            int y = cy - (int) Math.round(Math.cos(angle) * RADIUS);
            float cooldown = state.cooldownLeft(spell, now) / (float) spell.cooldownTicks();
            boolean affordable = state.mana(now) >= SpellCasting.manaCost(player, spell);
            slot(graphics, x, y, spell, cooldown, affordable);
        }

        Spell shown = hovered != null ? hovered : current;
        summary(graphics, cx, cy, shown);
        description(graphics, cx, cy - RADIUS - 24, shown);
    }

    /** A spell's slot: icon, frame, and the same cooldown sweep vanilla draws over items. */
    private void slot(GuiGraphics graphics, int x, int y, Spell spell, float cooldown, boolean affordable) {
        boolean isHovered = spell == hovered;
        int half = isHovered ? 13 : 11;
        int border = spell == current ? 0xFFFFFFFF : isHovered ? SpellIcons.color(spell) : 0xFF4A2D6E;
        graphics.fill(x - half, y - half, x + half, y + half, isHovered ? 0xE0281640 : 0xC0140A22);
        frame(graphics, x - half, y - half, x + half, y + half, border);
        SpellIcons.draw(graphics, spell, x - 8, y - 8);
        if (cooldown > 0) {
            int top = y - 8 + Mth.floor(16 * (1 - cooldown));
            graphics.fill(x - 8, top, x + 8, y + 8, 0x7FFFFFFF);
        }
        if (!affordable) graphics.fill(x - 8, y - 8, x + 8, y + 8, 0x80200838);
    }

    /** Name, mana cost and cooldown, stacked inside the inner ring. */
    private void summary(GuiGraphics graphics, int cx, int cy, Spell spell) {
        String cooldown = String.format(Locale.ROOT, "%.1f", spell.cooldownTicks() / 20f);
        graphics.drawCenteredString(font, spell.displayName(), cx, cy - 14, SpellIcons.color(spell));
        graphics.drawCenteredString(font, Component.translatable("spell.arcane_spells.mana", Math.round(spell.manaCost())), cx, cy - 2, 0xFFA898C0);
        graphics.drawCenteredString(font, Component.translatable("spell.arcane_spells.cooldown", cooldown), cx, cy + 10, 0xFFA898C0);
    }

    /** What the spell does, ending just above the wheel. Left out on screens too short to fit it. */
    private void description(GuiGraphics graphics, int cx, int bottom, Spell spell) {
        List<FormattedCharSequence> lines = font.split(spell.description(), DESCRIPTION_WIDTH);
        int lineHeight = font.lineHeight + 1;
        int top = bottom - lines.size() * lineHeight;
        if (top < 2) return;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            graphics.drawString(font, line, cx - font.width(line) / 2, top + i * lineHeight, 0xFFE8E0F0, true);
        }
    }

    /** Dots from the centre towards the mouse, ending inside the wheel. */
    private static void pointer(GuiGraphics graphics, int cx, int cy, int mouseX, int mouseY) {
        double dx = mouseX - cx, dy = mouseY - cy;
        double length = Math.sqrt(dx * dx + dy * dy);
        for (int d = DEAD_ZONE; d < Math.min(length, RADIUS - 22); d += 4) {
            int x = cx + (int) Math.round(dx / length * d), y = cy + (int) Math.round(dy / length * d);
            graphics.fill(x, y, x + 1, y + 1, 0xB0FFFFFF);
        }
    }

    /** One-pixel dots around an arc; angles run clockwise from straight up, like the slots. */
    private static void dottedCircle(GuiGraphics graphics, int cx, int cy, int radius, double from, double to, int color) {
        int dots = (int) Math.ceil(radius * (to - from) / 3);
        for (int i = 0; i <= dots; i++) {
            double angle = from + (to - from) * i / dots;
            int x = cx + (int) Math.round(Math.sin(angle) * radius), y = cy - (int) Math.round(Math.cos(angle) * radius);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void frame(GuiGraphics graphics, int left, int top, int right, int bottom, int color) {
        graphics.fill(left, top, right, top + 1, color);
        graphics.fill(left, bottom - 1, right, bottom, color);
        graphics.fill(left, top, left + 1, bottom, color);
        graphics.fill(right - 1, top, right, bottom, color);
    }

    /** The spell whose sector the offset from the centre points into, or null inside the dead zone. */
    private static Spell spellAt(double dx, double dy) {
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return null;
        // Clockwise from straight up; screen y grows downwards.
        double angle = Math.atan2(dx, -dy);
        return SPELLS[Math.floorMod(Math.round(angle / SECTOR), SPELLS.length)];
    }
}
