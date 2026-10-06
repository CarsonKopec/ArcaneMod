package dev.arcane.client.gui;

import dev.arcane.api.spell.Spell;
import dev.arcane.client.config.ArcaneClientSettings;
import dev.arcane.network.LearnTalentPayload;
import dev.arcane.network.SelectSpellPayload;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.progression.ArcaneTalent;
import dev.arcane.progression.SpellSchool;
import dev.arcane.registry.ModSounds;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.SpellCasting;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.base.BaseUIComponent;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.UIComponents;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.UIContainers;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIGraphics;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** A live view of synchronized spell knowledge; selection and learning are requests to the server. */
public final class SpellBookScreen extends BaseOwoScreen<FlowLayout> {
    private static final int TEXT = GrimoireTheme.TEXT;
    private static final int MUTED = GrimoireTheme.MUTED;
    private static final int ACCENT = GrimoireTheme.GOLD;
    private final List<Runnable> liveViews = new ArrayList<>();
    private final List<Runnable> spellViews = new ArrayList<>();
    private FlowLayout pageContainer;
    private FlowLayout spellDetails;
    private LabelComponent manaLabel;
    private LabelComponent statusLabel;
    private Spell inspected;
    private Page page = Page.SPELLS;
    private ArcaneTalent pendingTalent;
    private int pendingRank, pendingTicks;

    public SpellBookScreen() {
        super(Component.translatable("screen.arcane_spells.spell_book"));
    }

    @Override protected OwoUIAdapter<FlowLayout> createAdapter() {
        return OwoUIAdapter.create(this, UIContainers::verticalFlow);
    }

    @Override protected void build(FlowLayout root) {
        root.surface(GrimoireTheme.backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);

        FlowLayout frame = UIContainers.verticalFlow(Sizing.fill(94), Sizing.fill(94));
        frame.id("book-frame");
        frame.padding(Insets.of(10));
        frame.gap(4);
        frame.surface(GrimoireTheme.frame());

        FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        heading.gap(6);
        heading.verticalAlignment(VerticalAlignment.CENTER);
        FlowLayout title = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        title.gap(3);
        title.child(label(getTitle().copy().withStyle(ChatFormatting.BOLD), TEXT));
        if (height >= 300) title.child(label(Component.translatable("book.arcane_spells.edition"), ACCENT));
        heading.child(title);
        ButtonComponent settings = button("book-settings", "book.arcane_spells.settings",
                () -> minecraft.setScreen(ArcaneClientSettings.screen(this)));
        settings.horizontalSizing(Sizing.fixed(60));
        heading.child(settings);
        frame.child(heading);

        manaLabel = label(Component.empty(), MUTED);
        FlowLayout mana = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        mana.gap(3);
        mana.child(manaLabel);
        mana.child(GrimoireWidgets.mana(() -> {
            if (minecraft.player == null) return 0;
            CasterState state = CasterState.of(minecraft.player);
            return state.maxMana() <= 0 ? 0 : state.mana(minecraft.player.level().getGameTime()) / state.maxMana();
        }));
        frame.child(mana);

        FlowLayout navigation = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        navigation.gap(4);
        navigation.horizontalAlignment(HorizontalAlignment.CENTER);
        for (Page tab : Page.values()) {
            GrimoireButton tabButton = button("book-tab-" + tab.key, "book.arcane_spells.tab." + tab.key,
                    () -> pageContainer.queue(() -> showPage(tab))).selected(() -> page == tab);
            // owo gives every expand child the same remaining space; percentages are explicit shares, not weights.
            tabButton.horizontalSizing(Sizing.expand(100 / Page.values().length));
            navigation.child(tabButton);
        }
        frame.child(navigation);

        pageContainer = UIContainers.verticalFlow(Sizing.fill(100), Sizing.expand());
        pageContainer.id("book-page");
        pageContainer.padding(Insets.of(4));
        pageContainer.surface(GrimoireTheme.page());
        frame.child(pageContainer);

        FlowLayout footer = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        footer.gap(6);
        statusLabel = label(Component.translatable("book.arcane_spells.hint"), MUTED);
        statusLabel.horizontalSizing(Sizing.expand());
        footer.child(statusLabel);
        footer.verticalAlignment(VerticalAlignment.CENTER);
        ButtonComponent done = button("book-done", "book.arcane_spells.close", this::onClose);
        done.horizontalSizing(Sizing.fixed(54));
        footer.child(done);
        frame.child(footer);
        root.child(frame);
        showPage(page);
        refresh();
    }

    @Override public boolean isPauseScreen() {
        return false;
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (ArcaneScreens.matchesBookKey(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override public void tick() {
        if (minecraft.player == null || minecraft.level == null) {
            onClose();
            return;
        }
        if (pendingTalent != null && (ArcaneProgression.get(minecraft.player).rank(pendingTalent) != pendingRank
                || --pendingTicks <= 0)) {
            pendingTalent = null;
            statusLabel.text(Component.translatable("book.arcane_spells.hint"));
        }
        refresh();
    }

    private void refresh() {
        Player player = minecraft.player;
        if (player == null || manaLabel == null) return;
        CasterState state = CasterState.of(player);
        update(manaLabel, Component.translatable("book.arcane_spells.mana", decimal(state.mana(player.level().getGameTime())),
                decimal(state.maxMana()), decimal(state.manaRegen())));
        liveViews.forEach(Runnable::run);
        spellViews.forEach(Runnable::run);
    }

    private void showPage(Page next) {
        page = next;
        liveViews.clear();
        spellViews.clear();
        pageContainer.clearChildren();
        switch (page) {
            case SPELLS -> buildSpells();
            case PROGRESSION -> buildProgression();
            case TALENTS -> buildTalents();
        }
        refresh();
    }

    private void buildSpells() {
        List<Spell> spells = SpellCasting.spells();
        if (spells.isEmpty()) {
            pageContainer.child(label(Component.translatable("book.arcane_spells.no_spells"), MUTED));
            return;
        }
        if (inspected == null || !spells.contains(inspected)) {
            ItemStack staff = SpellCasting.heldStaff(minecraft.player);
            inspected = staff.isEmpty() ? spells.getFirst() : SpellCasting.selected(staff);
            if (!spells.contains(inspected)) inspected = spells.getFirst();
        }
        FlowLayout columns = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.fill(100));
        columns.gap(6);
        FlowLayout index = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        index.gap(3);
        for (Spell spell : spells) {
            GrimoireButton entry = new GrimoireButton(spell.displayName(), () ->
                    pageContainer.queue(() -> inspect(spell))).accent(spell.color()).icon(spell).selected(() -> inspected == spell);
            entry.id("spell-" + spell.id().toString().replace(':', '-'));
            entry.horizontalSizing(Sizing.fill(100));
            entry.verticalSizing(Sizing.fixed(26));
            entry.tooltip(spell.description());
            index.child(entry);
        }
        columns.child(GrimoireTheme.scroll(index, Sizing.fill(37), Sizing.fill(100)));
        spellDetails = UIContainers.verticalFlow(Sizing.expand(), Sizing.fill(100));
        spellDetails.padding(Insets.of(5));
        spellDetails.gap(4);
        columns.child(spellDetails);
        pageContainer.child(columns);
        inspect(inspected);
    }

    private void inspect(Spell spell) {
        inspected = spell;
        spellViews.clear();
        spellDetails.clearChildren();
        spellDetails.surface(GrimoireTheme.card(spell.color()));
        FlowLayout heading = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        heading.gap(5);
        heading.verticalAlignment(VerticalAlignment.CENTER);
        BaseUIComponent seal = GrimoireWidgets.seal(spell);
        seal.sizing(Sizing.fixed(34));
        heading.child(seal);
        FlowLayout headingText = UIContainers.verticalFlow(Sizing.expand(), Sizing.content());
        headingText.gap(3);
        LabelComponent name = label(spell.displayName().copy().withStyle(ChatFormatting.BOLD), spell.color());
        headingText.child(name);
        headingText.child(label(schoolName(SpellSchool.of(spell)), MUTED));
        heading.child(headingText);
        spellDetails.child(heading);

        FlowLayout details = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        details.gap(6);
        details.padding(Insets.right(3));
        details.child(label(spell.description(), TEXT));
        details.child(liveLabel(spellViews, () -> Component.translatable("book.arcane_spells.cost",
                decimal(SpellCasting.manaCost(minecraft.player, spell))), TEXT));
        details.child(liveLabel(spellViews, () -> Component.translatable("book.arcane_spells.cooldown",
                decimal(SpellCasting.cooldown(minecraft.player, spell) / 20f),
                decimal(CasterState.of(minecraft.player).cooldownLeft(spell, minecraft.level.getGameTime()) / 20f)), TEXT));
        details.child(label(spell.charged()
                ? Component.translatable("book.arcane_spells.channel", decimal(spell.fullChargeTicks() / 20f))
                : Component.translatable("book.arcane_spells.instant"), MUTED));
        details.child(liveLabel(spellViews, () -> Component.translatable("book.arcane_spells.mastery",
                ArcaneProgression.get(minecraft.player).mastery(spell), ArcaneProgression.get(minecraft.player).casts(spell)), ACCENT));
        details.child(GrimoireWidgets.mastery(() -> ArcaneProgression.get(minecraft.player).mastery(spell)));
        spellDetails.child(GrimoireTheme.scroll(details, Sizing.fill(100), Sizing.expand()));
        GrimoireButton select = button("book-select-spell", "book.arcane_spells.select", () -> select(spell)).accent(spell.color())
                .selected(() -> SpellCasting.holds(SpellCasting.heldStaff(minecraft.player), spell)
                        && SpellCasting.selected(SpellCasting.heldStaff(minecraft.player)) == spell);
        select.horizontalSizing(Sizing.fill(100));
        spellDetails.child(select);
        spellViews.add(() -> {
            ItemStack staff = SpellCasting.heldStaff(minecraft.player);
            boolean compatible = SpellCasting.holds(staff, spell);
            boolean selected = compatible && SpellCasting.selected(staff) == spell;
            select.active(compatible && !selected && !minecraft.player.isUsingItem()
                    && ClientPlayNetworking.canSend(SelectSpellPayload.TYPE));
            Component title = Component.translatable(selected ? "book.arcane_spells.selected"
                    : compatible ? "book.arcane_spells.select" : "book.arcane_spells.need_staff");
            if (!select.getMessage().equals(title)) select.setMessage(title);
        });
        spellViews.forEach(Runnable::run);
    }

    private void select(Spell spell) {
        Player player = minecraft.player;
        if (player == null || player.isUsingItem() || !SpellCasting.holds(SpellCasting.heldStaff(player), spell)
                || !ClientPlayNetworking.canSend(SelectSpellPayload.TYPE)) return;
        ClientPlayNetworking.send(new SelectSpellPayload(spell));
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.WHEEL_SELECT, 1));
    }

    private void buildProgression() {
        FlowLayout content = scrollingPage();
        FlowLayout overview = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        overview.gap(6);
        overview.horizontalAlignment(HorizontalAlignment.CENTER);
        FlowLayout points = card(Sizing.expand(50), ACCENT);
        points.child(label(Component.translatable("book.arcane_spells.earned_knowledge"), MUTED));
        points.child(liveLabel(liveViews, () -> Component.translatable("book.arcane_spells.skill_points",
                ArcaneProgression.get(minecraft.player).skillPoints()), ACCENT));
        overview.child(points);
        FlowLayout corruption = card(Sizing.expand(50), GrimoireTheme.VIOLET);
        corruption.child(label(Component.translatable("book.arcane_spells.astral_echo"), MUTED));
        corruption.child(liveLabel(liveViews, () -> Component.translatable("book.arcane_spells.corruption",
                ArcaneProgression.get(minecraft.player).corruption()), GrimoireTheme.VIOLET));
        overview.child(corruption);
        content.child(overview);
        content.child(label(Component.translatable("book.arcane_spells.affinities").withStyle(ChatFormatting.BOLD), ACCENT));
        FlowLayout schools = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        schools.gap(4);
        schools.horizontalAlignment(HorizontalAlignment.CENTER);
        for (SpellSchool school : SpellSchool.values()) {
            int color = schoolColor(school);
            FlowLayout schoolCard = card(Sizing.expand(25), color);
            schoolCard.child(label(schoolName(school), color));
            schoolCard.child(liveLabel(liveViews, () -> Component.translatable("book.arcane_spells.affinity_xp",
                    ArcaneProgression.get(minecraft.player).affinity(school)), TEXT));
            schools.child(schoolCard);
        }
        content.child(schools);
        content.child(label(Component.translatable("book.arcane_spells.masteries").withStyle(ChatFormatting.BOLD), ACCENT));
        for (Spell spell : SpellCasting.spells()) {
            FlowLayout mastery = card(Sizing.fill(100), spell.color());
            FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
            row.gap(5);
            row.verticalAlignment(VerticalAlignment.CENTER);
            row.child(new SpellIcon(spell));
            LabelComponent value = liveLabel(liveViews, () -> Component.translatable("book.arcane_spells.mastery_entry",
                    spell.displayName(), ArcaneProgression.get(minecraft.player).mastery(spell),
                    ArcaneProgression.get(minecraft.player).casts(spell)), TEXT);
            value.horizontalSizing(Sizing.expand());
            row.child(value);
            mastery.child(row);
            mastery.child(GrimoireWidgets.mastery(() -> ArcaneProgression.get(minecraft.player).mastery(spell)));
            content.child(mastery);
        }
    }

    private void buildTalents() {
        FlowLayout content = scrollingPage();
        content.child(label(Component.translatable("book.arcane_spells.talents_intro"), MUTED));
        content.child(liveLabel(liveViews, () -> Component.translatable("book.arcane_spells.skill_points",
                ArcaneProgression.get(minecraft.player).skillPoints()), ACCENT));
        FlowLayout branches = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        branches.gap(6);
        branches.horizontalAlignment(HorizontalAlignment.CENTER);
        for (ArcaneTalent talent : ArcaneTalent.values()) {
            int accent = talentColor(talent);
            FlowLayout branch = card(Sizing.expand(100 / ArcaneTalent.values().length), accent);
            String key = talent.name().toLowerCase(Locale.ROOT);
            branch.id("branch-" + key);
            branch.child(GrimoireWidgets.talent(talent, () -> ArcaneProgression.get(minecraft.player).rank(talent)));
            LabelComponent branchTitle = label(Component.translatable("talent.arcane_spells." + key).withStyle(ChatFormatting.BOLD), accent);
            branchTitle.horizontalTextAlignment(HorizontalAlignment.CENTER);
            branch.child(branchTitle);
            LabelComponent description = label(Component.translatable("talent.arcane_spells." + key + ".description"), MUTED);
            // Give every branch the same header height so the three rank rows stay connected visually.
            description.verticalSizing(Sizing.fixed(45));
            branch.child(description);
            for (int target = 1; target <= talent.maxRank(); target++) {
                int targetRank = target;
                if (target > 1) {
                    branch.child(new BaseUIComponent() {
                        { sizing(Sizing.fill(100), Sizing.fixed(7)); }
                        @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
                            int color = ArcaneProgression.get(minecraft.player).rank(talent) >= targetRank - 1 ? accent : 0xFF44374F;
                            graphics.fill(x + width / 2, y, x + width / 2 + 1, y + height, color);
                            GrimoireTheme.diamond(graphics, x + width / 2, y + height / 2, 2, color);
                        }
                    });
                }
                GrimoireButton learn = new GrimoireButton(Component.empty(), () -> learn(talent, targetRank))
                        .accent(accent).selected(() -> ArcaneProgression.get(minecraft.player).rank(talent) >= targetRank);
                learn.id("learn-" + key + "-" + target);
                learn.horizontalSizing(Sizing.fill(100));
                learn.tooltip(Component.translatable("book.arcane_spells.learn_cost", talent.costForNextRank(target - 1)));
                branch.child(learn);
                liveViews.add(() -> {
                    var progression = ArcaneProgression.get(minecraft.player);
                    int rank = progression.rank(talent);
                    boolean acquired = rank >= targetRank;
                    int cost = talent.costForNextRank(targetRank - 1);
                    learn.active(pendingTalent == null && rank == targetRank - 1 && progression.skillPoints() >= cost
                            && ClientPlayNetworking.canSend(LearnTalentPayload.TYPE));
                    Component title = Component.translatable(acquired ? "book.arcane_spells.rank_learned"
                            : "book.arcane_spells.rank_cost", targetRank, cost);
                    if (!learn.getMessage().equals(title)) {
                        learn.setMessage(title);
                        learn.tooltip(acquired ? Component.translatable("book.arcane_spells.rank_learned_tooltip", targetRank)
                                : Component.translatable("book.arcane_spells.learn_cost", cost));
                    }
                });
            }
            branches.child(branch);
        }
        content.child(branches);
    }

    private void learn(ArcaneTalent talent, int targetRank) {
        if (minecraft.player == null || pendingTalent != null || !ClientPlayNetworking.canSend(LearnTalentPayload.TYPE)) return;
        var progression = ArcaneProgression.get(minecraft.player);
        int rank = progression.rank(talent);
        if (rank != targetRank - 1 || rank >= talent.maxRank() || progression.skillPoints() < talent.costForNextRank(rank)) return;
        // Prevent a double-click from submitting two purchases before the first server response arrives.
        pendingTalent = talent;
        pendingRank = rank;
        pendingTicks = 40;
        ClientPlayNetworking.send(new LearnTalentPayload(talent));
        statusLabel.text(Component.translatable("book.arcane_spells.learning"));
        refresh();
    }

    private FlowLayout scrollingPage() {
        FlowLayout content = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        content.gap(7);
        content.padding(Insets.right(4));
        pageContainer.child(GrimoireTheme.scroll(content, Sizing.fill(100), Sizing.fill(100)));
        return content;
    }

    private static GrimoireButton button(String id, String key, Runnable action) {
        GrimoireButton button = new GrimoireButton(Component.translatable(key), action);
        button.id(id);
        button.verticalSizing(Sizing.fixed(22));
        return button;
    }

    private static FlowLayout card(Sizing width, int accent) {
        FlowLayout card = UIContainers.verticalFlow(width, Sizing.content());
        card.padding(Insets.of(5));
        card.gap(4);
        card.surface(GrimoireTheme.card(accent));
        return card;
    }

    private static int talentColor(ArcaneTalent talent) {
        return switch (talent) {
            case RESERVOIR -> GrimoireTheme.VIOLET;
            case RECOVERY -> GrimoireTheme.CYAN;
            case FOCUS -> GrimoireTheme.GOLD;
        };
    }

    private static int schoolColor(SpellSchool school) {
        return switch (school) {
            case ARCANE -> GrimoireTheme.VIOLET;
            case STORM -> 0xFF91BEF4;
            case FROST -> GrimoireTheme.CYAN;
            case ASTRAL -> GrimoireTheme.GOLD;
        };
    }

    private static LabelComponent label(Component text, int color) {
        LabelComponent label = UIComponents.label(text).color(Color.ofArgb(color)).shadow(false);
        label.horizontalSizing(Sizing.fill(100));
        return label;
    }

    private LabelComponent liveLabel(List<Runnable> views, Supplier<Component> text, int color) {
        LabelComponent label = label(Component.empty(), color);
        views.add(() -> update(label, text.get()));
        return label;
    }

    private static void update(LabelComponent label, Component text) {
        // Avoid invalidating the layout on ticks where the visible value has not changed.
        if (!label.text().equals(text)) label.text(text);
    }

    private static Component schoolName(SpellSchool school) {
        return Component.translatable("school.arcane_spells." + school.name().toLowerCase(Locale.ROOT));
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private enum Page {
        SPELLS("spells"), PROGRESSION("progression"), TALENTS("talents");
        final String key;

        Page(String key) {
            this.key = key;
        }
    }

    private static final class SpellIcon extends BaseUIComponent {
        private final Spell spell;

        SpellIcon(Spell spell) {
            this.spell = spell;
            sizing(Sizing.fixed(18));
            tooltip(spell.displayName());
        }

        @Override public void draw(OwoUIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
            SpellIcons.draw(graphics, spell, x + 1, y + 1);
        }
    }
}
