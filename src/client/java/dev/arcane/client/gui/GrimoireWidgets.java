package dev.arcane.client.gui;

import dev.arcane.api.spell.Spell;
import dev.arcane.progression.SpellSchool;
import dev.arcane.spell.SpellTuning;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;
import net.minecraft.client.gui.GuiGraphics;

/** Small, passive decorations drawn in GUI pixels so they stay crisp at every GUI scale. */
public final class GrimoireWidgets {
    private static final int INK = 0xFF140F23;
    private static final int DORMANT = 0xFF51465F;
    // A sixteen-sided circle, in units of sixteen. No frame-by-frame geometry allocation.
    private static final int[] RING_X = {0, 6, 11, 15, 16, 15, 11, 6, 0, -6, -11, -15, -16, -15, -11, -6};
    private static final int[] RING_Y = {-16, -15, -11, -6, 0, 6, 11, 15, 16, 15, 11, 6, 0, -6, -11, -15};

    private GrimoireWidgets() {}

    public static BaseUIComponent mana(DoubleSupplier fraction) {
        return new Decoration(5) {
            @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                if (width < 3) return;
                graphics.fill(x, y, x + width, y + 5, DORMANT);
                graphics.fill(x + 1, y + 1, x + width - 1, y + 4, INK);
                double value = fraction.getAsDouble();
                if (!Double.isFinite(value)) value = 0;
                int filled = (int) Math.round(Math.clamp(value, 0.0, 1.0) * (width - 2));
                int segmentWidth = Math.max(8, (width - 2) / 24);
                for (int start = 0; start < filled; start += segmentWidth) {
                    int end = Math.min(filled, start + segmentWidth - 1);
                    int color = blend(GrimoireTheme.CYAN, GrimoireTheme.VIOLET, (double) start / Math.max(1, width - 3));
                    graphics.fill(x + 1 + start, y + 1, x + 1 + end, y + 4, color);
                    graphics.fill(x + 1 + start, y + 1, x + 1 + end, y + 2, blend(color, GrimoireTheme.TEXT, .28));
                }
            }
        };
    }

    /** Corruption out of its maximum: violet, turning rose past the backlash threshold, which is notched in gold. */
    public static BaseUIComponent corruption(IntSupplier corruption) {
        return new Decoration(5) {
            @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                if (width < 3) return;
                graphics.fill(x, y, x + width, y + 5, DORMANT);
                graphics.fill(x + 1, y + 1, x + width - 1, y + 4, INK);
                int inner = width - 2;
                int value = Math.clamp(corruption.getAsInt(), 0, SpellTuning.CORRUPTION_MAX);
                int filled = inner * value / SpellTuning.CORRUPTION_MAX;
                int threshold = inner * SpellTuning.BACKLASH_THRESHOLD / SpellTuning.CORRUPTION_MAX;
                if (filled > 0) graphics.fill(x + 1, y + 1, x + 1 + Math.min(filled, threshold), y + 4, GrimoireTheme.VIOLET);
                if (filled > threshold) graphics.fill(x + 1 + threshold, y + 1, x + 1 + filled, y + 4, GrimoireTheme.ROSE);
                graphics.fill(x + 1, y + 1, x + 1 + filled, y + 2, 0x40FFFFFF);
                graphics.fill(x + 1 + threshold, y, x + 2 + threshold, y + 5, GrimoireTheme.GOLD);
            }
        };
    }

    public static BaseUIComponent mastery(IntSupplier rank) {
        return new Decoration(10) {
            @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                if (width < 30) return;
                int learned = Math.clamp(rank.getAsInt(), 0, 10);
                int step = Math.min(10, (width - 4) / 10);
                int radius = step >= 7 ? 2 : 1;
                int first = x + (width - step * 9) / 2;
                int cy = y + 5;
                graphics.fill(x + 1, cy, x + width - 1, cy + 1, 0x664C3C5F);
                for (int i = 0; i < 10; i++) {
                    int cx = first + step * i;
                    int color = i < learned ? GrimoireTheme.GOLD : DORMANT;
                    GrimoireTheme.diamond(graphics, cx, cy, radius + 1, INK);
                    GrimoireTheme.diamond(graphics, cx, cy, radius, color);
                    if (i < learned) graphics.fill(cx, cy - 1, cx + 1, cy, GrimoireTheme.TEXT);
                }
            }
        };
    }

    public static BaseUIComponent seal(Spell spell) {
        final int color = schoolColor(SpellSchool.of(spell));
        return new Decoration(44) {
            @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                if (width < 18) return;
                int cx = x + width / 2;
                int cy = y + height / 2;
                int radius = Math.min(20, (Math.min(width, height) - 2) / 2);
                if (radius >= 15) {
                    sideRules(graphics, x, width, cx, cy, radius + 8, color);
                    diamondOutline(graphics, cx, cy, radius, alpha(color, 105));
                    ring(graphics, cx, cy, radius - 4, alpha(color, 175));
                    for (int i = 0; i < 4; i++) {
                        int dx = i == 1 ? radius : i == 3 ? -radius : 0;
                        int dy = i == 0 ? -radius : i == 2 ? radius : 0;
                        graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, GrimoireTheme.GOLD);
                    }
                    // Tiny diagonally opposed runes keep the seal distinct from a button border.
                    graphics.fill(cx - 11, cy - 12, cx - 8, cy - 11, GrimoireTheme.GOLD);
                    graphics.fill(cx + 9, cy + 12, cx + 12, cy + 13, GrimoireTheme.GOLD);
                }
                SpellIcons.draw(graphics, spell, cx - 8, cy - 8);
            }
        };
    }

    private abstract static class Decoration extends BaseUIComponent {
        Decoration(int height) {
            horizontalSizing(Sizing.fill(100));
            verticalSizing(Sizing.fixed(height));
        }
    }

    private static int schoolColor(SpellSchool school) {
        return switch (school) {
            case ARCANE -> GrimoireTheme.VIOLET;
            case STORM -> 0xFF91BEF4;
            case FROST -> GrimoireTheme.CYAN;
            case ASTRAL -> GrimoireTheme.GOLD;
        };
    }

    private static void sideRules(GuiGraphics graphics, int x, int width, int cx, int cy, int gap, int color) {
        int extent = Math.min(46, width / 2 - gap - 5);
        if (extent < 5) return;
        graphics.fill(cx - gap - extent, cy, cx - gap, cy + 1, alpha(color, 75));
        graphics.fill(cx + gap + 1, cy, cx + gap + extent + 1, cy + 1, alpha(color, 75));
        GrimoireTheme.diamond(graphics, cx - gap - 1, cy, 1, GrimoireTheme.GOLD);
        GrimoireTheme.diamond(graphics, cx + gap + 1, cy, 1, GrimoireTheme.GOLD);
    }

    private static void ring(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        for (int i = 0; i < RING_X.length; i++) {
            int next = (i + 1) & 15;
            line(graphics, cx + RING_X[i] * radius / 16, cy + RING_Y[i] * radius / 16,
                    cx + RING_X[next] * radius / 16, cy + RING_Y[next] * radius / 16, color);
        }
    }

    private static void diamondOutline(GuiGraphics graphics, int cx, int cy, int radius, int color) {
        line(graphics, cx, cy - radius, cx + radius, cy, color);
        line(graphics, cx + radius, cy, cx, cy + radius, color);
        line(graphics, cx, cy + radius, cx - radius, cy, color);
        line(graphics, cx - radius, cy, cx, cy - radius, color);
    }

    private static void line(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int error = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) return;
            int twice = 2 * error;
            if (twice >= dy) { error += dy; x0 += sx; }
            if (twice <= dx) { error += dx; y0 += sy; }
        }
    }

    private static int alpha(int color, int alpha) {
        return (color & 0xFFFFFF) | (alpha << 24);
    }

    private static int blend(int first, int second, double amount) {
        int red = (int) (((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int green = (int) (((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
