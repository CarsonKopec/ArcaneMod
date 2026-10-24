package dev.arcane.client.gui;

import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.UIComponent;
import net.minecraft.client.gui.GuiGraphics;

/** Pixel-aligned ink, leather and gilt shared by the grimoire's pages and controls. */
public final class GrimoireTheme {
    public static final int TEXT = 0xFFF0E7D5;
    public static final int MUTED = 0xFFB0A9BF;
    public static final int GOLD = 0xFFCEAC69;
    public static final int VIOLET = 0xFFAF8CEE;
    public static final int CYAN = 0xFF79D7DB;
    /** Corruption past the point where it can lash back. */
    public static final int ROSE = 0xFFE07BC4;

    private GrimoireTheme() {
    }

    public static Surface backdrop() {
        return (graphics, component) -> {
            int x = component.x(), y = component.y(), w = component.width(), h = component.height();
            graphics.fillGradient(x, y, x + w, y + h, 0xDB080813, 0xEC100A1C);
            // Quiet points of light stay outside the book and never obscure its text.
            diamond(graphics, x + 9, y + h / 4, 2, 0x5079D7DB);
            diamond(graphics, x + w - 10, y + h * 3 / 4, 2, 0x50AF8CEE);
        };
    }

    public static Surface frame() {
        return (graphics, component) -> {
            int x = component.x(), y = component.y(), w = component.width(), h = component.height();
            if (w < 12 || h < 12) return;
            clippedFill(graphics, x + 2, y + 3, w, h, 3, 0x90000000);
            clippedFill(graphics, x, y, w, h, 3, 0xFF221B30);
            graphics.fillGradient(x + 3, y + 3, x + w - 3, y + h - 3, 0xFF211D30, 0xFF111420);
            clippedOutline(graphics, x, y, w, h, 3, 0xFF57456C);
            clippedOutline(graphics, x + 2, y + 2, w - 4, h - 4, 2, GOLD);
            clippedOutline(graphics, x + 4, y + 4, w - 8, h - 8, 1, 0xFF65523C);
            // The binding is confined to the padded edge, leaving the pages free for content.
            graphics.fill(x + 6, y + 12, x + 7, y + h - 12, 0xFF070A12);
            graphics.fill(x + 7, y + 12, x + 8, y + h - 12, 0xFF40324C);
            for (int band = 1; band <= 3; band++) {
                int bandY = y + h * band / 4;
                graphics.fill(x + 3, bandY, x + 8, bandY + 1, GOLD);
                graphics.fill(x + 3, bandY + 2, x + 8, bandY + 3, 0xFF65523C);
            }
            corner(graphics, x + 5, y + 5, 1, 1);
            corner(graphics, x + w - 6, y + 5, -1, 1);
            corner(graphics, x + 5, y + h - 6, 1, -1);
            corner(graphics, x + w - 6, y + h - 6, -1, -1);
            diamond(graphics, x + w / 2, y + 3, 2, GOLD);
            diamond(graphics, x + w / 2, y + h - 4, 2, GOLD);
        };
    }

    public static Surface page() {
        return (graphics, component) -> {
            int x = component.x(), y = component.y(), w = component.width(), h = component.height();
            if (w < 4 || h < 4) return;
            graphics.fillGradient(x, y, x + w, y + h, 0xFF191826, 0xFF10121D);
            clippedOutline(graphics, x, y, w, h, 2, 0xFF453C52);
            graphics.fill(x + 2, y + 3, x + 3, y + h - 3, 0xFF242030);
            graphics.fill(x + w - 3, y + 3, x + w - 2, y + h - 3, 0xFF080B13);
        };
    }

    public static Surface card(int accent) {
        return (graphics, component) -> {
            int x = component.x(), y = component.y(), w = component.width(), h = component.height();
            if (w < 6 || h < 6) return;
            clippedFill(graphics, x, y, w, h, 2, 0xFF1B1A2A);
            graphics.fillGradient(x + 2, y + 2, x + w - 2, y + h - 2, 0xFF232033, 0xFF151724);
            clippedOutline(graphics, x, y, w, h, 2, 0xFF494053);
            graphics.fill(x + 2, y + 6, x + 3, y + h - 6, tint(accent, 0x99));
            graphics.fill(x + 5, y, x + Math.min(w - 5, 24), y + 1, accent);
            diamond(graphics, x + w - 4, y + h - 4, 1, tint(accent, 0x99));
        };
    }

    /** A narrow, always discoverable scroll rail with space reserved beside its content. */
    public static <C extends UIComponent> ScrollContainer<C> scroll(C content, Sizing width, Sizing height) {
        Insets margins = content.margins().get();
        content.margins(margins.withRight(Math.max(6, margins.right())));
        ScrollContainer<C> scroll = UIContainers.verticalScroll(width, height, content);
        scroll.scrollbarThiccness(3);
        scroll.scrollbar((graphics, x, y, w, h, trackX, trackY, trackW, trackH, lastInteraction, direction, active) -> {
            if (!active || h <= 0 || trackH <= 0) return;
            int color = lastInteraction > System.currentTimeMillis() ? GOLD : 0xFF877452;
            graphics.fill(trackX + 1, trackY, trackX + 2, trackY + trackH, 0xFF3B334A);
            clippedFill(graphics, x, y, w, h, 1, color);
            if (h >= 7) graphics.fill(x + 1, y + 2, x + 2, y + h - 2, 0xFFDAC898);
        });
        return scroll;
    }

    /** Draws a compact, filled rune without font or texture dependencies. */
    public static void diamond(GuiGraphics graphics, int centerX, int centerY, int radius, int color) {
        if (radius < 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int halfWidth = radius - Math.abs(dy);
            graphics.fill(centerX - halfWidth, centerY + dy, centerX + halfWidth + 1, centerY + dy + 1, color);
        }
    }

    /** A thin divider with a small central lozenge. */
    public static void rule(GuiGraphics graphics, int x, int y, int width, int color) {
        if (width <= 0) return;
        if (width < 13) {
            graphics.fill(x, y, x + width, y + 1, color);
            return;
        }
        int center = x + width / 2;
        graphics.fill(x, y, center - 4, y + 1, tint(color, 0x70));
        graphics.fill(center + 5, y, x + width, y + 1, tint(color, 0x70));
        diamond(graphics, center, y, 2, color);
    }

    static int tint(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    static void clippedFill(GuiGraphics graphics, int x, int y, int width, int height, int cut, int color) {
        if (width <= 0 || height <= 0) return;
        cut = Math.max(0, Math.min(cut, (Math.min(width, height) - 1) / 2));
        graphics.fill(x, y + cut, x + width, y + height - cut, color);
        for (int row = 0; row < cut; row++) {
            int inset = cut - row;
            graphics.fill(x + inset, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    static void clippedOutline(GuiGraphics graphics, int x, int y, int width, int height, int cut, int color) {
        if (width <= 0 || height <= 0) return;
        cut = Math.max(0, Math.min(cut, (Math.min(width, height) - 1) / 2));
        graphics.fill(x + cut, y, x + width - cut, y + 1, color);
        graphics.fill(x + cut, y + height - 1, x + width - cut, y + height, color);
        graphics.fill(x, y + cut, x + 1, y + height - cut, color);
        graphics.fill(x + width - 1, y + cut, x + width, y + height - cut, color);
        for (int row = 1; row < cut; row++) {
            int inset = cut - row;
            graphics.fill(x + inset, y + row, x + inset + 1, y + row + 1, color);
            graphics.fill(x + width - inset - 1, y + row, x + width - inset, y + row + 1, color);
            graphics.fill(x + inset, y + height - row - 1, x + inset + 1, y + height - row, color);
            graphics.fill(x + width - inset - 1, y + height - row - 1, x + width - inset, y + height - row, color);
        }
    }

    private static void corner(GuiGraphics graphics, int x, int y, int directionX, int directionY) {
        for (int step = 0; step < 6; step++) {
            graphics.fill(x + directionX * step, y, x + directionX * step + 1, y + 1, GOLD);
            graphics.fill(x, y + directionY * step, x + 1, y + directionY * step + 1, GOLD);
        }
        diamond(graphics, x + directionX * 3, y + directionY * 3, 1, GOLD);
    }
}
