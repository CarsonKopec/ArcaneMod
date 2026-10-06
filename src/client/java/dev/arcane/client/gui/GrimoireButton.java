package dev.arcane.client.gui;

import dev.arcane.api.spell.Spell;
import io.wispforest.owo.mixin.ui.access.AbstractWidgetAccessor;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.Sizing;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/** A gilt-edged button that keeps owo's normal focus, input, tooltip and narration behavior. */
public final class GrimoireButton extends ButtonComponent {
    private int accent = GrimoireTheme.GOLD;
    private BooleanSupplier selected = () -> false;
    private Spell icon;
    private long pressedUntil;

    public GrimoireButton(Component message, Runnable action) {
        super(message, button -> action.run());
        verticalSizing(Sizing.fixed(24));
    }

    public GrimoireButton accent(int color) {
        this.accent = color | 0xFF000000;
        return this;
    }

    public GrimoireButton selected(BooleanSupplier selected) {
        this.selected = Objects.requireNonNull(selected, "selected");
        return this;
    }

    public GrimoireButton icon(Spell spell) {
        this.icon = spell;
        return this;
    }

    @Override public void onPress(InputWithModifiers input) {
        pressedUntil = System.nanoTime() + 130_000_000L;
        super.onPress(input);
    }

    @Override public void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), width = getWidth(), height = getHeight();
        if (width < 4 || height < 4) return;
        boolean chosen = selected.getAsBoolean();
        boolean highlighted = active && isHoveredOrFocused();
        boolean pressed = active && System.nanoTime() < pressedUntil;
        int edge = chosen ? accent : highlighted ? GrimoireTheme.GOLD : active ? 0xFF756044 : 0xFF464052;
        int top = !active ? 0xFF1B1A26 : chosen ? 0xFF353044 : highlighted ? 0xFF32293E : 0xFF242031;
        int bottom = !active ? 0xFF151620 : chosen ? 0xFF252A3D : highlighted ? 0xFF232335 : 0xFF181B29;
        if (pressed) {
            top = 0xFF11141F;
            bottom = 0xFF262136;
        }

        GrimoireTheme.clippedFill(graphics, x, y + 1, width, height - 1, 2, 0xB0000000);
        GrimoireTheme.clippedFill(graphics, x, y, width, height - 1, 2, top);
        graphics.fillGradient(x + 2, y + 2, x + width - 2, y + height - 3, top, bottom);
        GrimoireTheme.clippedOutline(graphics, x, y, width, height - 1, 2, edge);
        GrimoireTheme.clippedOutline(graphics, x + 2, y + 2, width - 4, height - 5, 1,
                GrimoireTheme.tint(edge, highlighted || chosen ? 0x85 : 0x38));

        if (chosen) {
            graphics.fill(x + 3, y + 5, x + 4, y + height - 6, accent);
            GrimoireTheme.diamond(graphics, x + width - 7, y + (height - 1) / 2, 2, accent);
        } else if (highlighted) {
            graphics.fill(x + width / 2 - 7, y, x + width / 2 + 8, y + 1, accent);
        }
        if (isFocused() && active) {
            // Focus has a second cue beyond color, including when the mouse is elsewhere.
            GrimoireTheme.diamond(graphics, x + 5, y + (height - 1) / 2, 1, GrimoireTheme.TEXT);
        }

        var client = Minecraft.getInstance();
        var font = client.font;
        int shift = pressed ? 1 : 0;
        int textLeft = x + 8, textRight = x + width - (chosen ? 14 : 8);
        boolean showIcon = icon != null && width >= 44 && height >= 20;
        if (showIcon) {
            int iconY = y + (height - 17) / 2 + shift;
            SpellIcons.draw(graphics, icon, x + 6, iconY);
            textLeft = x + 27;
        }
        int available = Math.max(0, textRight - textLeft);
        String message = getMessage().getString();
        String rendered = message;
        boolean truncated = font.width(message) > available;
        if (truncated) {
            String suffix = available >= font.width("...") ? "..." : "";
            rendered = font.plainSubstrByWidth(message, Math.max(0, available - font.width(suffix))) + suffix;
        }
        int textX = showIcon ? textLeft : textLeft + (available - font.width(rendered)) / 2;
        int textY = y + (height - font.lineHeight) / 2 + shift;
        int textColor = !active ? chosen ? 0xFFC1BDAE : 0xFF8F899D
                : chosen ? GrimoireTheme.TEXT : highlighted ? 0xFFFFF2D7 : GrimoireTheme.TEXT;
        if (available > 0) {
            graphics.enableScissor(textLeft, y + 2, textRight, y + height - 2);
            graphics.drawString(font, rendered, textX, textY, textColor, false);
            graphics.disableScissor();
        }

        var tooltip = ((AbstractWidgetAccessor) (Object) this).owo$getTooltip();
        if ((isHovered() || isFocused()) && tooltip.get() != null) {
            graphics.setTooltipForNextFrame(font, tooltip.get().toCharSequence(client), DefaultTooltipPositioner.INSTANCE,
                    mouseX, mouseY, isFocused());
        } else if (truncated && (isHovered() || isFocused())) {
            graphics.setTooltipForNextFrame(font, getMessage(), mouseX, mouseY);
        }
    }
}
