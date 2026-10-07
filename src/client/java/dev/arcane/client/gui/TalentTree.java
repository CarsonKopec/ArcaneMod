package dev.arcane.client.gui;

import dev.arcane.progression.ArcaneTalent;
import dev.arcane.progression.PlayerProgressionComponent;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * The talent page: a tree growing from a sigil at its root. Each branch's ranks are a chain of nodes; a finished branch
 * opens its capstone, and the three capstones meet at Archmage, the crown. Light runs along the tree as far as it has
 * grown. Click a node to learn it, or move between nodes with the arrow keys and press Enter. The whole tree is one
 * component, laid out from its own size, so it fits whatever space the page gives it.
 */
public final class TalentTree extends BaseUIComponent {
    public enum State { LEARNED, AVAILABLE, UNAFFORDABLE, LOCKED }

    /** One rank of a talent, at a column (0 the root, 1 the crown) and a row (0 top, 1 bottom) of the tree. */
    record Node(ArcaneTalent talent, int rank, float column, float row) {
        String id() {
            return talent.name().toLowerCase(Locale.ROOT) + "-" + rank;
        }
    }

    private static final float ROOT_ROW = 0.5f;
    static final List<Node> NODES = nodes();
    private static final int PAD_X = 6, PAD_Y = 12;
    private static final int INK = 0xFF140F23, DORMANT = 0xFF3A3346, LOCKED_EDGE = 0xFF4E4560;
    private static final int CROWN = 0xFFFFE8B0;
    private static final String[] NUMERALS = {"", "I", "II", "III"};

    private final Supplier<PlayerProgressionComponent> progression;
    /** Whether a purchase can be asked for now: none waiting on the server, and a connection to send it on. */
    private final BooleanSupplier ready;
    private final Learner learner;
    /** The node the keyboard is on, or -1; and whether it is shown: the tree has focus and the keyboard was last used. */
    private int selected = -1;
    private boolean focused, keyboard;

    interface Learner {
        void learn(ArcaneTalent talent, int targetRank);
    }

    TalentTree(Supplier<PlayerProgressionComponent> progression, BooleanSupplier ready, Learner learner) {
        this.progression = progression;
        this.ready = ready;
        this.learner = learner;
        sizing(Sizing.fill(100), Sizing.fill(100));
        cursorStyle(CursorStyle.HAND);
    }

    private static List<Node> nodes() {
        List<Node> nodes = new ArrayList<>();
        ArcaneTalent[] branches = {ArcaneTalent.RESERVOIR, ArcaneTalent.RECOVERY, ArcaneTalent.FOCUS};
        ArcaneTalent[] capstones = {ArcaneTalent.WELLSPRING, ArcaneTalent.ECHO, ArcaneTalent.TEMPO};
        for (int i = 0; i < branches.length; i++) {
            float row = i / 2f;
            for (int rank = 1; rank <= branches[i].maxRank(); rank++) nodes.add(new Node(branches[i], rank, 0.01f + 0.18f * rank, row));
            nodes.add(new Node(capstones[i], 1, 0.77f, row));
        }
        nodes.add(new Node(ArcaneTalent.ARCHMAGE, 1, 1, ROOT_ROW));
        return List.copyOf(nodes);
    }

    // ------------------------------------------------------------------ state

    State state(Node node) {
        PlayerProgressionComponent learned = progression.get();
        int rank = learned.rank(node.talent());
        if (rank >= node.rank()) return State.LEARNED;
        if (rank < node.rank() - 1 || !learned.unlocked(node.talent())) return State.LOCKED;
        return learned.skillPoints() >= node.talent().costForNextRank(rank) && ready.getAsBoolean() ? State.AVAILABLE : State.UNAFFORDABLE;
    }

    /** The state of {@code talent}'s node for {@code rank}. */
    public State state(ArcaneTalent talent, int rank) {
        return state(node(talent, rank));
    }

    /** Where {@code talent}'s node for {@code rank} is drawn: its left and top edges and its size, in GUI pixels. */
    public int[] bounds(ArcaneTalent talent, int rank) {
        Node node = node(talent, rank);
        int size = nodeSize();
        return new int[] {centerX(node.column()) - size / 2, centerY(node.row()) - size / 2, size};
    }

    static Node node(ArcaneTalent talent, int rank) {
        return NODES.stream().filter(node -> node.talent() == talent && node.rank() == rank).findFirst().orElseThrow();
    }

    /** The nodes a node grows from: the rank below it, or what its talent requires, or none (the root). */
    private static List<Node> parents(Node node) {
        if (node.rank() > 1) return List.of(node(node.talent(), node.rank() - 1));
        return node.talent().requires().stream().map(required -> node(required, required.maxRank())).toList();
    }

    // ------------------------------------------------------------------ layout

    int nodeSize() {
        return Math.clamp(Math.min(width / 14, (height - 2 * PAD_Y) / 4), 14, 26);
    }

    int centerX(float column) {
        int size = nodeSize();
        return x + PAD_X + size / 2 + Math.round(column * (width - 2 * PAD_X - size));
    }

    int centerY(float row) {
        int size = nodeSize();
        return y + PAD_Y + size / 2 + Math.round(row * (height - 2 * PAD_Y - size));
    }

    private Node at(double mouseX, double mouseY) {
        int half = nodeSize() / 2;
        for (Node node : NODES) {
            if (Math.abs(mouseX - centerX(node.column())) <= half && Math.abs(mouseY - centerY(node.row())) <= half) return node;
        }
        return null;
    }

    // ------------------------------------------------------------------ drawing

    @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        if (width < 120 || height < 60) return;
        long time = System.currentTimeMillis();
        stars(graphics);
        for (Node node : NODES) {
            List<Node> parents = parents(node);
            State state = state(node);
            if (parents.isEmpty()) {
                edge(graphics, centerX(0), centerY(ROOT_ROW), centerX(node.column()), centerY(node.row()), accent(node.talent()), state, time);
            }
            for (Node parent : parents) {
                edge(graphics, centerX(parent.column()), centerY(parent.row()), centerX(node.column()), centerY(node.row()),
                        accent(node.talent()), state, time);
            }
        }
        root(graphics, time);
        Node pointed = hovered ? at(mouseX, mouseY) : null;
        for (int i = 0; i < NODES.size(); i++) {
            Node node = NODES.get(i);
            node(graphics, node, state(node), node == pointed || (i == selected && cursorShown()), time);
        }
        labels(graphics);

        Node shown = pointed != null ? pointed : cursorShown() ? NODES.get(selected) : null;
        if (shown != null) {
            int tipX = pointed != null ? mouseX : centerX(shown.column());
            int tipY = pointed != null ? mouseY : centerY(shown.row());
            graphics.setTooltipForNextFrame(tooltip(shown), tipX, tipY);
        } else if (hovered && rootHovered(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(wrap(List.of(Component.translatable("book.arcane_spells.tree.root").withStyle(ChatFormatting.GOLD),
                    Component.translatable("book.arcane_spells.skill_points", progression.get().skillPoints()))), mouseX, mouseY);
        }
    }

    /** A faint field of fixed stars behind the tree. */
    private void stars(GuiGraphics graphics) {
        int count = width * height / 450;
        for (int i = 0; i < count; i++) {
            int hash = i * 0x9E3779B1;
            hash ^= hash >>> 15;
            hash *= 0x85EBCA6B;
            int sx = x + Math.floorMod(hash, width), sy = y + Math.floorMod(hash >>> 11, height);
            graphics.fill(sx, sy, sx + 1, sy + 1, (hash & 7) == 0 ? 0x50CEAC69 : 0x30B0A9BF);
        }
    }

    /** The root: a ringed star the branches spring from, and the points there are to spend. */
    private void root(GuiGraphics graphics, long time) {
        int cx = centerX(0), cy = centerY(ROOT_ROW), radius = nodeSize() / 2 + 2;
        float pulse = 0.75f + 0.25f * (float) Math.sin(time / 500.0);
        GrimoireTheme.diamond(graphics, cx, cy, radius + 1, INK);
        diamondOutline(graphics, cx, cy, radius + 1, GrimoireTheme.GOLD);
        diamondOutline(graphics, cx, cy, radius - 2, GrimoireTheme.tint(GrimoireTheme.GOLD, (int) (140 * pulse)));
        line(graphics, cx - radius + 3, cy, cx + radius - 3, cy, GrimoireTheme.GOLD);
        line(graphics, cx, cy - radius + 3, cx, cy + radius - 3, GrimoireTheme.GOLD);
        GrimoireTheme.diamond(graphics, cx, cy, 2, CROWN);
        Font font = Minecraft.getInstance().font;
        String points = Component.translatable("book.arcane_spells.tree.points", progression.get().skillPoints()).getString();
        graphics.drawString(font, points, cx - font.width(points) / 2, cy + radius + 4, GrimoireTheme.GOLD, false);
    }

    private boolean rootHovered(double mouseX, double mouseY) {
        int radius = nodeSize() / 2 + 3;
        return Math.abs(mouseX - centerX(0)) + Math.abs(mouseY - centerY(ROOT_ROW)) <= radius;
    }

    /**
     * A branch from one node to the next: straight along a row, or across, down and across between rows. Dim until the
     * node it leads to can be reached, then lit; a learned node's branch carries a spark of light along it.
     */
    private void edge(GuiGraphics graphics, int fromX, int fromY, int toX, int toY, int accent, State state, long time) {
        int middle = (fromX + toX) / 2;
        int[][] path = fromY == toY ? new int[][] {{fromX, fromY}, {toX, toY}}
                : new int[][] {{fromX, fromY}, {middle, fromY}, {middle, toY}, {toX, toY}};
        int color = switch (state) {
            case LEARNED -> accent;
            case AVAILABLE, UNAFFORDABLE -> GrimoireTheme.tint(accent, 0x90);
            case LOCKED -> DORMANT;
        };
        for (int i = 0; i + 1 < path.length; i++) {
            int[] a = path[i], b = path[i + 1];
            int left = Math.min(a[0], b[0]), right = Math.max(a[0], b[0]), top = Math.min(a[1], b[1]), bottom = Math.max(a[1], b[1]);
            if (state == State.LEARNED) graphics.fill(left - 1, top - 1, right + 2, bottom + 2, GrimoireTheme.tint(accent, 0x38));
            graphics.fill(left, top, right + 1, bottom + 1, color);
        }
        if (state == State.LEARNED) {
            // The spark: once along the branch every two seconds.
            int length = 0;
            for (int i = 0; i + 1 < path.length; i++) length += Math.abs(path[i + 1][0] - path[i][0]) + Math.abs(path[i + 1][1] - path[i][1]);
            int along = (int) (time % 2000 * length / 2000);
            for (int i = 0; i + 1 < path.length; i++) {
                int[] a = path[i], b = path[i + 1];
                int segment = Math.abs(b[0] - a[0]) + Math.abs(b[1] - a[1]);
                if (along <= segment) {
                    int sx = a[0] + Integer.signum(b[0] - a[0]) * along, sy = a[1] + Integer.signum(b[1] - a[1]) * along;
                    graphics.fill(sx - 1, sy - 1, sx + 2, sy + 2, CROWN);
                    break;
                }
                along -= segment;
            }
        }
    }

    private void node(GuiGraphics graphics, Node node, State state, boolean highlighted, long time) {
        int size = nodeSize(), left = centerX(node.column()) - size / 2, top = centerY(node.row()) - size / 2;
        int cx = left + size / 2, cy = top + size / 2;
        int accent = accent(node.talent());
        boolean capstone = node.talent().maxRank() == 1;
        int cut = capstone ? size / 3 : 3;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time / 260.0);
        int edge = switch (state) {
            case LEARNED -> accent;
            case AVAILABLE -> blend(GrimoireTheme.GOLD, CROWN, pulse);
            case UNAFFORDABLE -> blend(accent, LOCKED_EDGE, 0.55f);
            case LOCKED -> LOCKED_EDGE;
        };
        if (state == State.LEARNED) GrimoireTheme.clippedFill(graphics, left - 2, top - 2, size + 4, size + 4, cut + 1, GrimoireTheme.tint(accent, 0x45));
        if (state == State.AVAILABLE) GrimoireTheme.clippedFill(graphics, left - 2, top - 2, size + 4, size + 4, cut + 1,
                GrimoireTheme.tint(GrimoireTheme.GOLD, (int) (0x20 + 0x40 * pulse)));
        GrimoireTheme.clippedFill(graphics, left, top + 1, size, size, cut, 0xA0000000);
        GrimoireTheme.clippedFill(graphics, left, top, size, size, cut, edge);
        int inner = state == State.LEARNED ? blend(accent, INK, 0.72f) : 0xFF1A1826;
        GrimoireTheme.clippedFill(graphics, left + 1, top + 1, size - 2, size - 2, Math.max(0, cut - 1), inner);
        if (highlighted) GrimoireTheme.clippedOutline(graphics, left - 3, top - 3, size + 6, size + 6, cut + 2, GrimoireTheme.TEXT);

        int glyph = switch (state) {
            case LEARNED -> blend(accent, CROWN, 0.35f);
            case AVAILABLE -> accent;
            case UNAFFORDABLE -> blend(accent, LOCKED_EDGE, 0.5f);
            case LOCKED -> 0xFF6A6178;
        };
        glyph(graphics, node.talent(), cx, cy, Math.max(4, size / 2 - 4), glyph);
        // A ranked talent's rank, in pips along the node's foot.
        if (!capstone) {
            for (int i = 0; i < node.rank(); i++) {
                int px = cx + (i * 2 - (node.rank() - 1)) * 2;
                graphics.fill(px, top + size - 3, px + 1, top + size - 2, state == State.LEARNED ? CROWN : glyph);
            }
        }
    }

    /** A small emblem for each talent, drawn in lines, about {@code r} pixels from its centre. */
    private static void glyph(GuiGraphics graphics, ArcaneTalent talent, int cx, int cy, int r, int color) {
        switch (talent) {
            case RESERVOIR -> {
                // A crystal: a lozenge with a seam down it.
                diamondOutline(graphics, cx, cy, r, color);
                line(graphics, cx, cy - r + 1, cx, cy + r - 1, GrimoireTheme.tint(color, 0xA0));
            }
            case RECOVERY -> {
                // Rising motes.
                line(graphics, cx - r + 1, cy + r - 1, cx + r - 1, cy - r + 1, GrimoireTheme.tint(color, 0x90));
                GrimoireTheme.diamond(graphics, cx - r / 2, cy + r / 3, 1, color);
                GrimoireTheme.diamond(graphics, cx + r / 3, cy - r / 4, 1, color);
                GrimoireTheme.diamond(graphics, cx + r - 1, cy - r + 1, 1, color);
            }
            case FOCUS -> {
                // An eye.
                line(graphics, cx - r, cy, cx, cy - r / 2 - 1, color);
                line(graphics, cx, cy - r / 2 - 1, cx + r, cy, color);
                line(graphics, cx + r, cy, cx, cy + r / 2 + 1, color);
                line(graphics, cx, cy + r / 2 + 1, cx - r, cy, color);
                GrimoireTheme.diamond(graphics, cx, cy, 1, color);
            }
            case WELLSPRING -> {
                // A fountain: a jet that parts and falls into a basin.
                line(graphics, cx, cy + r - 1, cx, cy - r + 1, color);
                line(graphics, cx, cy - r + 1, cx - r + 1, cy, color);
                line(graphics, cx, cy - r + 1, cx + r - 1, cy, color);
                line(graphics, cx - r + 1, cy + r - 1, cx + r - 1, cy + r - 1, color);
            }
            case ECHO -> {
                // A point and the rings it sends out.
                GrimoireTheme.diamond(graphics, cx, cy, 1, color);
                diamondOutline(graphics, cx, cy, r / 2 + 1, GrimoireTheme.tint(color, 0xC0));
                diamondOutline(graphics, cx, cy, r, GrimoireTheme.tint(color, 0x80));
            }
            case TEMPO -> {
                // An hourglass.
                line(graphics, cx - r + 1, cy - r + 1, cx + r - 1, cy - r + 1, color);
                line(graphics, cx - r + 1, cy + r - 1, cx + r - 1, cy + r - 1, color);
                line(graphics, cx - r + 1, cy - r + 1, cx + r - 1, cy + r - 1, color);
                line(graphics, cx + r - 1, cy - r + 1, cx - r + 1, cy + r - 1, color);
            }
            case ARCHMAGE -> {
                // An eight-pointed star.
                line(graphics, cx - r, cy, cx + r, cy, color);
                line(graphics, cx, cy - r, cx, cy + r, color);
                int d = r * 2 / 3;
                line(graphics, cx - d, cy - d, cx + d, cy + d, GrimoireTheme.tint(color, 0xB0));
                line(graphics, cx + d, cy - d, cx - d, cy + d, GrimoireTheme.tint(color, 0xB0));
                GrimoireTheme.diamond(graphics, cx, cy, 2, CROWN);
            }
        }
    }

    /** Branch names over their middle ranks, and capstone names over their nodes, where there is room. */
    private void labels(GuiGraphics graphics) {
        Font font = Minecraft.getInstance().font;
        int size = nodeSize();
        // Rows are close on a small page: the labels sit in the gap above each row, so they need a line's height of it.
        if (centerY(0.5f) - centerY(0) - size < font.lineHeight + 1) return;
        for (Node node : NODES) {
            boolean named = node.rank() == 2 || node.talent().maxRank() == 1;
            if (!named) continue;
            String name = Component.translatable("talent.arcane_spells." + node.talent().name().toLowerCase(Locale.ROOT)).getString();
            int textX = Math.clamp(centerX(node.column()) - font.width(name) / 2, x + 1, x + width - font.width(name) - 1);
            int textY = centerY(node.row()) - size / 2 - font.lineHeight - 1;
            int color = state(node) == State.LOCKED ? 0xFF7D7590 : accent(node.talent());
            graphics.drawString(font, name, textX, textY, color, false);
        }
    }

    private List<FormattedCharSequence> tooltip(Node node) {
        PlayerProgressionComponent learned = progression.get();
        String key = node.talent().name().toLowerCase(Locale.ROOT);
        Component name = Component.translatable("talent.arcane_spells." + key);
        if (node.talent().maxRank() > 1) name = name.copy().append(" " + NUMERALS[node.rank()]);
        List<Component> lines = new ArrayList<>();
        lines.add(name.copy().withColor(accent(node.talent()) & 0xFFFFFF));
        lines.add(Component.translatable("talent.arcane_spells." + key + ".description").withStyle(ChatFormatting.GRAY));
        int cost = node.talent().costForNextRank(node.rank() - 1);
        lines.add(switch (state(node)) {
            case LEARNED -> Component.translatable("book.arcane_spells.tree.learned").withStyle(ChatFormatting.GOLD);
            case AVAILABLE -> Component.translatable("book.arcane_spells.tree.learn", cost).withStyle(ChatFormatting.YELLOW);
            case UNAFFORDABLE -> Component.translatable("book.arcane_spells.tree.short", cost, learned.skillPoints()).withStyle(ChatFormatting.RED);
            case LOCKED -> Component.translatable("book.arcane_spells.tree.requires", requirement(node)).withStyle(ChatFormatting.DARK_GRAY);
        });
        return wrap(lines);
    }

    /** What must be learned first: the rank below, or the talents required, by name. */
    private static Component requirement(Node node) {
        List<Node> parents = parents(node);
        Component names = Component.empty();
        for (int i = 0; i < parents.size(); i++) {
            Node parent = parents.get(i);
            if (i > 0) names = names.copy().append(i == parents.size() - 1 ? " & " : ", ");
            Component name = Component.translatable("talent.arcane_spells." + parent.talent().name().toLowerCase(Locale.ROOT));
            if (parent.talent().maxRank() > 1) name = name.copy().append(" " + NUMERALS[parent.rank()]);
            names = names.copy().append(name);
        }
        return names;
    }

    private static List<FormattedCharSequence> wrap(List<Component> lines) {
        Font font = Minecraft.getInstance().font;
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) wrapped.addAll(font.split(line, 170));
        return wrapped;
    }

    // ------------------------------------------------------------------ input

    @Override public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        Node node = at(x + click.x(), y + click.y());
        if (node != null) {
            selected = NODES.indexOf(node);
            keyboard = false;
            learn(node);
            return true;
        }
        return super.onMouseDown(click, doubled);
    }

    @Override public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override public void onFocusGained(FocusSource source) {
        super.onFocusGained(source);
        focused = true;
        keyboard = source == FocusSource.KEYBOARD_CYCLE;
        if (selected < 0) selected = 0;
    }

    @Override public void onFocusLost() {
        super.onFocusLost();
        focused = false;
    }

    @Override public boolean onKeyPress(KeyEvent input) {
        if (selected < 0) selected = 0;
        Node current = NODES.get(selected);
        if (input.isConfirmation() || input.isSelection()) {
            keyboard = true;
            learn(current);
            return true;
        }
        int dx = input.isRight() ? 1 : input.isLeft() ? -1 : 0, dy = input.isDown() ? 1 : input.isUp() ? -1 : 0;
        if (dx == 0 && dy == 0) return super.onKeyPress(input);
        keyboard = true;
        // The nearest node that way, favouring the direction pressed over sideways drift.
        Node best = null;
        float bestScore = Float.MAX_VALUE;
        for (Node node : NODES) {
            float along = (node.column() - current.column()) * dx + (node.row() - current.row()) * dy;
            if (along <= 0.001f) continue;
            float across = dx != 0 ? Math.abs(node.row() - current.row()) : Math.abs(node.column() - current.column());
            float score = along + across * 3;
            if (score < bestScore) {
                bestScore = score;
                best = node;
            }
        }
        if (best != null) selected = NODES.indexOf(best);
        return true;
    }

    /** A mouse click focuses the tree too, but only keyboard use shows which node the keys are on. */
    private boolean cursorShown() {
        return focused && keyboard && selected >= 0;
    }

    private void learn(Node node) {
        if (state(node) == State.AVAILABLE) learner.learn(node.talent(), node.rank());
    }

    // ------------------------------------------------------------------ colour

    static int accent(ArcaneTalent talent) {
        return switch (talent) {
            case RESERVOIR, WELLSPRING -> GrimoireTheme.VIOLET;
            case RECOVERY, ECHO -> GrimoireTheme.CYAN;
            case FOCUS, TEMPO -> GrimoireTheme.GOLD;
            case ARCHMAGE -> CROWN;
        };
    }

    private static int blend(int first, int second, float amount) {
        int red = (int) (((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int green = (int) (((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int blue = (int) ((first & 255) * (1 - amount) + (second & 255) * amount);
        return 0xFF000000 | red << 16 | green << 8 | blue;
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
            if (twice >= dy) {
                error += dy;
                x0 += sx;
            }
            if (twice <= dx) {
                error += dx;
                y0 += sy;
            }
        }
    }
}
