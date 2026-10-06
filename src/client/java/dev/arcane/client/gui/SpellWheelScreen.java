package dev.arcane.client.gui;

import dev.arcane.api.spell.Spell;
import dev.arcane.network.SelectSpellPayload;
import dev.arcane.registry.ModSounds;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Hold the spell key to open, point at a spell, release to pick it. A click also picks, Escape cancels. Opening a
 * screen frees the mouse at the screen centre, so the pointer's direction from the centre chooses the spell.
 *
 * <p>Every spell the staff holds gets a slot, other mods' included; the ring widens past a dozen so slots never overlap.
 */
public final class SpellWheelScreen extends BaseOwoScreen<FlowLayout> {
    private static final int MIN_RADIUS = 62;
    /** Centre-to-centre distance between neighbouring slots: a hovered slot is 26 wide. */
    private static final int SLOT_SPACING = 30;
    /** Pointer this close to the centre picks nothing, so a quick tap keeps the current spell. */
    private static final int DEAD_ZONE = 16;
    /** Wide enough that every description fits in two lines above the wheel, even at small window sizes. */
    private static final int DESCRIPTION_WIDTH = 300;

    private final KeyMapping key;
    private final Spell current;
    private final List<Spell> spells;
    private final double sector;
    private final int radius;
    private Spell hovered;

    /** {@code staff} must hold at least one spell. */
    public SpellWheelScreen(KeyMapping key, ItemStack staff) {
        super(Component.translatable("screen.arcane_spells.spell_wheel"));
        this.key = key;
        this.current = SpellCasting.selected(staff);
        this.spells = SpellCasting.spells(staff);
        this.sector = 2 * Math.PI / spells.size();
        this.radius = Math.max(MIN_RADIUS, (int) Math.ceil(SLOT_SPACING / sector));
    }

    @Override protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override protected void build(FlowLayout root) {
        root.surface(Surface.flat(0x50000000));
        // Keep the wheel's established geometry and hold/release handling inside one owo component.
        // This avoids rectangular widget hitboxes changing which radial sector the player selects.
        BaseUIComponent wheel = new BaseUIComponent() {
            @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                renderWheel(graphics, mouseX, mouseY);
            }
        };
        wheel.sizing(Sizing.fill(100));
        root.child(wheel);
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

    private void renderWheel(GuiGraphics graphics, int mouseX, int mouseY) {
        Player player = minecraft.player;
        if (player == null) return;
        int cx = width / 2, cy = height / 2;
        hovered = spellAt(mouseX - cx, mouseY - cy);
        CasterState state = CasterState.of(player);
        long now = player.level().getGameTime();

        dottedCircle(graphics, cx, cy, radius + 20, 0, 2 * Math.PI, 0x50C9A6FF);
        dottedCircle(graphics, cx, cy, radius - 16, 0, 2 * Math.PI, 0x40C9A6FF);
        if (hovered != null) {
            int accent = hovered.color();
            double middle = spells.indexOf(hovered) * sector;
            dottedCircle(graphics, cx, cy, radius + 20, middle - sector / 2, middle + sector / 2, accent);
            dottedCircle(graphics, cx, cy, radius + 21, middle - sector / 2, middle + sector / 2, accent);
            pointer(graphics, cx, cy, mouseX, mouseY);
        }
        for (int i = 0; i < spells.size(); i++) {
            Spell spell = spells.get(i);
            double angle = i * sector;
            int x = cx + (int) Math.round(Math.sin(angle) * radius);
            int y = cy - (int) Math.round(Math.cos(angle) * radius);
            float cooldown = state.cooldownFraction(spell, now);
            boolean affordable = state.mana(now) >= SpellCasting.manaCost(player, spell);
            slot(graphics, x, y, spell, cooldown, affordable);
        }

        Spell shown = hovered != null ? hovered : current;
        summary(graphics, cx, cy, player, shown);
        description(graphics, cx, cy - radius - 24, shown);
    }

    /** A spell's slot: icon, frame, and the same cooldown sweep vanilla draws over items. */
    private void slot(GuiGraphics graphics, int x, int y, Spell spell, float cooldown, boolean affordable) {
        boolean isHovered = spell == hovered;
        int half = isHovered ? 13 : 11;
        int border = spell == current ? 0xFFFFFFFF : isHovered ? spell.color() : 0xFF4A2D6E;
        graphics.fill(x - half, y - half, x + half, y + half, isHovered ? 0xE0281640 : 0xC0140A22);
        frame(graphics, x - half, y - half, x + half, y + half, border);
        SpellIcons.draw(graphics, spell, x - 8, y - 8);
        if (cooldown > 0) {
            int top = y - 8 + Mth.floor(16 * (1 - cooldown));
            graphics.fill(x - 8, top, x + 8, y + 8, 0x7FFFFFFF);
        }
        if (!affordable) graphics.fill(x - 8, y - 8, x + 8, y + 8, 0x80200838);
    }

    /**
     * Name, mana cost and cooldown, stacked inside the inner ring. Cost and cooldown are what this player pays, after
     * other mods' adjustments (and creative's free casting).
     */
    private void summary(GuiGraphics graphics, int cx, int cy, Player player, Spell spell) {
        int mana = Math.round(SpellCasting.manaCost(player, spell));
        String cooldown = String.format(Locale.ROOT, "%.1f", SpellCasting.cooldown(player, spell) / 20f);
        graphics.drawCenteredString(font, spell.displayName(), cx, cy - 14, spell.color());
        graphics.drawCenteredString(font, Component.translatable("spell.arcane_spells.mana", mana), cx, cy - 2, 0xFFA898C0);
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
    private void pointer(GuiGraphics graphics, int cx, int cy, int mouseX, int mouseY) {
        double dx = mouseX - cx, dy = mouseY - cy;
        double length = Math.sqrt(dx * dx + dy * dy);
        for (int d = DEAD_ZONE; d < Math.min(length, radius - 22); d += 4) {
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
    private Spell spellAt(double dx, double dy) {
        if (dx * dx + dy * dy < DEAD_ZONE * DEAD_ZONE) return null;
        // Clockwise from straight up; screen y grows downwards.
        double angle = Math.atan2(dx, -dy);
        return spells.get(Math.floorMod(Math.round(angle / sector), spells.size()));
    }
}
