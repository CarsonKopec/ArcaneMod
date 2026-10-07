package dev.arcane.test;

import com.mojang.blaze3d.platform.InputConstants;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import dev.arcane.ArcaneSpells;
import dev.arcane.client.animation.StaffCastingAnimations;
import dev.arcane.client.config.ArcaneClientSettings;
import dev.arcane.client.gui.SpellBookScreen;
import dev.arcane.client.gui.SpellWheelScreen;
import dev.arcane.client.gui.TalentTree;
import dev.arcane.item.StaffCastingProfile;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.progression.ArcaneTalent;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.SpellCasting;
import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.ParentUIComponent;
import io.wispforest.owo.ui.core.UIComponent;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;

/** Real key input, owo navigation/config persistence, resizing, and both animated staff render paths. */
public final class LibraryUiGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        boolean[] animationPreferences = context.computeOnClient(client -> new boolean[] {
                ArcaneClientSettings.castingAnimations(), ArcaneClientSettings.staffAnimations()});
        int[] originalSize = context.computeOnClient(client -> new int[] {
                client.getWindow().getWidth(), client.getWindow().getHeight()});
        CameraType originalCamera = context.computeOnClient(client -> client.options.getCameraType());
        int originalGuiScale = context.computeOnClient(client -> client.options.guiScale().get());
        try (var game = context.worldBuilder().create()) {
            var server = game.getServer();
            server.runCommand("time set noon");
            server.runCommand("gamemode survival @a");
            server.runCommand("tp @a 0 -60 0 0 0");
            server.runCommand("item replace entity @a weapon.mainhand with arcane_spells:arcane_staff");
            game.getClientWorld().waitForChunksRender();
            context.waitTicks(5);
            context.runOnClient(client -> {
                ArcaneClientSettings.setCastingAnimations(true);
                ArcaneClientSettings.setStaffAnimations(true);
                for (String staff : new String[] {"arcane_staff", "astral_staff"}) {
                    requireResource("geckolib/models/item/" + staff + ".geo.json");
                    requireResource("geckolib/animations/item/" + staff + ".animation.json");
                    requireResource("items/" + staff + ".json");
                }
                requireResource("player_animations/staff_casting.animation.json");
            });

            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitForScreen(SpellBookScreen.class);
            context.waitTicks(3);
            context.runOnClient(client -> assertBookBounds(client.screen, false));
            context.takeScreenshot("libraries-book-spells");
            click(context, "spell-arcane_spells-chain_lightning");
            hover(context, "book-select-spell");
            context.takeScreenshot("libraries-book-spell-detail-hover");
            click(context, "book-select-spell");
            server.runOnServer(s -> {
                var player = s.getPlayerList().getPlayers().getFirst();
                check(SpellCasting.selected(SpellCasting.heldStaff(player)) == ModSpells.CHAIN_LIGHTNING,
                        "Book selection did not reach the server");
            });
            context.takeScreenshot("libraries-book-spell-selected");
            context.runOnClient(client -> client.options.guiScale().set(2));
            context.getInput().resizeWindow(1280, 720);
            context.waitTicks(3);
            context.runOnClient(client -> assertBookBounds(client.screen, false));
            context.takeScreenshot("libraries-book-wide");
            context.runOnClient(client -> client.options.guiScale().set(originalGuiScale));
            context.getInput().resizeWindow(originalSize[0], originalSize[1]);
            context.waitTicks(3);

            click(context, "book-tab-progression");
            context.takeScreenshot("libraries-book-progression");
            click(context, "book-tab-talents");
            context.runOnClient(client -> {
                assertBookBounds(client.screen, true);
                check(tree(client.screen).state(ArcaneTalent.RESERVOIR, 1) == TalentTree.State.UNAFFORDABLE,
                        "A new player can buy a talent without skill points");
                check(tree(client.screen).state(ArcaneTalent.WELLSPRING, 1) == TalentTree.State.LOCKED,
                        "A capstone is open before its branch is complete");
            });
            context.takeScreenshot("libraries-book-talents");

            // Give the fixture earned mastery through the server component, then buy through real UI requests.
            server.runOnServer(s -> {
                var progression = ArcaneProgression.get(s.getPlayerList().getPlayers().getFirst());
                for (int cast = 0; cast < 10; cast++) {
                    progression.recordCast(ModSpells.ARCANE_BOLT, 12);
                    progression.recordCast(ModSpells.CHAIN_LIGHTNING, 30);
                }
            });
            context.waitTicks(5);
            clickNode(context, ArcaneTalent.RESERVOIR, 1);
            clickNode(context, ArcaneTalent.RECOVERY, 1);
            // A capstone whose branch isn't complete: clicking it must do nothing.
            clickNode(context, ArcaneTalent.WELLSPRING, 1);
            server.runOnServer(s -> {
                var progression = ArcaneProgression.get(s.getPlayerList().getPlayers().getFirst());
                check(progression.rank(ArcaneTalent.RESERVOIR) == 1 && progression.rank(ArcaneTalent.RECOVERY) == 1,
                        "Grimoire talent purchases did not reach the server");
                check(progression.rank(ArcaneTalent.WELLSPRING) == 0, "A locked capstone was bought");
                check(progression.skillPoints() == 2, "Grimoire talent purchases spent the wrong number of points");
            });
            context.runOnClient(client -> {
                TalentTree tree = tree(client.screen);
                check(tree.state(ArcaneTalent.RESERVOIR, 1) == TalentTree.State.LEARNED, "A learned rank doesn't show as learned");
                check(tree.state(ArcaneTalent.RESERVOIR, 2) == TalentTree.State.AVAILABLE, "The affordable next rank isn't available");
                check(tree.state(ArcaneTalent.RESERVOIR, 3) == TalentTree.State.LOCKED, "A rank can be skipped");
            });
            hoverNode(context, ArcaneTalent.RESERVOIR, 2);
            context.takeScreenshot("libraries-book-talents-earned");

            context.getInput().resizeWindow(640, 480);
            context.waitTicks(3);
            context.runOnClient(client -> assertBookBounds(client.screen, true));
            context.getInput().setCursorPos(1, 1);
            context.waitTicks(3);
            context.takeScreenshot("libraries-book-compact");
            hoverNode(context, ArcaneTalent.WELLSPRING, 1);
            context.takeScreenshot("libraries-book-compact-locked");
            context.getInput().resizeWindow(originalSize[0], originalSize[1]);
            context.waitTicks(3);

            click(context, "book-settings");
            context.waitForScreen(ConfigScreen.class);
            context.takeScreenshot("libraries-config");
            clickToggle(context, "castingAnimations");
            clickToggle(context, "staffAnimations");
            click(context, "done-button");
            context.waitForScreen(SpellBookScreen.class);
            context.runOnClient(client -> check(!ArcaneClientSettings.castingAnimations() && !ArcaneClientSettings.staffAnimations(),
                    "Generated config controls did not update the shared animation settings"));
            click(context, "book-settings");
            context.waitForScreen(ConfigScreen.class);
            clickToggle(context, "castingAnimations");
            clickToggle(context, "staffAnimations");
            click(context, "done-button");
            context.waitForScreen(SpellBookScreen.class);
            context.runOnClient(client -> check(ArcaneClientSettings.castingAnimations() && ArcaneClientSettings.staffAnimations(),
                    "Animation settings could not be re-enabled"));
            context.getInput().pressKey(InputConstants.KEY_K);
            context.waitForScreen(null);

            // The wheel retains its hold/release lifecycle after moving to an owo custom component.
            context.getInput().holdKey(InputConstants.KEY_R);
            context.waitForScreen(SpellWheelScreen.class);
            context.waitTicks(2);
            context.takeScreenshot("libraries-wheel");
            context.getInput().releaseKey(InputConstants.KEY_R);
            context.waitForScreen(null);

            captureStaff(context, server, "arcane_staff");
            captureStaff(context, server, "astral_staff");
        } finally {
            context.runOnClient(client -> {
                ArcaneClientSettings.setCastingAnimations(animationPreferences[0]);
                ArcaneClientSettings.setStaffAnimations(animationPreferences[1]);
                client.options.setCameraType(originalCamera);
                client.options.guiScale().set(originalGuiScale);
            });
            context.getInput().resizeWindow(originalSize[0], originalSize[1]);
        }
    }

    private static void captureStaff(ClientGameTestContext context, TestServerContext server, String staff) {
        server.runCommand("item replace entity @a weapon.mainhand with arcane_spells:" + staff);
        server.runCommand("tp @a 0 -60 0 25 -5");
        context.runOnClient(client -> client.options.setCameraType(CameraType.FIRST_PERSON));
        context.waitTicks(8);
        context.takeScreenshot("libraries-" + staff + "-held");
        context.runOnClient(client -> client.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        context.getInput().holdMouse(1);
        context.waitTicks(10);
        context.runOnClient(client -> {
            check(client.player.isUsingItem(), "Staff did not start its channel animation: " + staff);
            var layer = PlayerAnimationAccess.getPlayerAnimationLayer(client.player, StaffCastingAnimations.LAYER);
            check(layer != null && layer.isActive(), "PAL did not activate the staff channel pose: " + staff);
        });
        context.takeScreenshot("libraries-" + staff + "-channel");
        // Cancel before release: screenshots should not destroy the shared fixture or spend mana.
        server.runOnServer(s -> s.getPlayerList().getPlayers().getFirst().stopUsingItem());
        context.runOnClient(client -> client.player.stopUsingItem());
        context.getInput().releaseMouse(1);
        context.waitTicks(12);
        context.runOnClient(client -> {
            var layer = PlayerAnimationAccess.getPlayerAnimationLayer(client.player, StaffCastingAnimations.LAYER);
            check(layer != null && !layer.isActive(), "Cancelled staff channel did not fade out: " + staff);
            // Exercise the same visual release entry point used by authoritative cast events.
            StaffCastingAnimations.playRelease(client.player.getId(), InteractionHand.MAIN_HAND,
                    staff.equals("astral_staff") ? StaffCastingProfile.ASTRAL : StaffCastingProfile.ARCANE);
        });
        context.waitTicks(3);
        context.runOnClient(client -> check(PlayerAnimationAccess.getPlayerAnimationLayer(client.player,
                StaffCastingAnimations.LAYER).isActive(), "PAL did not activate the staff release pose: " + staff));
        context.takeScreenshot("libraries-" + staff + "-release");
        context.waitTicks(35);
        context.runOnClient(client -> check(!PlayerAnimationAccess.getPlayerAnimationLayer(client.player,
                StaffCastingAnimations.LAYER).isActive(), "Staff release pose did not fade out: " + staff));
    }

    private static void requireResource(String path) {
        // Called from the client thread; do not submit another context task from here.
        check(net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(ArcaneSpells.id(path)).isPresent(),
                "Missing animation/render resource " + path);
    }

    private static void click(ClientGameTestContext context, String id) {
        reveal(context, id);
        context.runOnClient(client -> click(client.screen, root(client.screen).childById(UIComponent.class, id)));
        context.waitTicks(3);
    }

    private static void hover(ClientGameTestContext context, String id) {
        reveal(context, id);
        double[] cursor = context.computeOnClient(client -> {
            UIComponent component = root(client.screen).childById(UIComponent.class, id);
            // TestInput uses physical window coordinates; owo components use scaled GUI coordinates.
            return new double[] {
                    (component.x() + component.width() / 2.0) * client.getWindow().getScreenWidth() / client.screen.width,
                    (component.y() + component.height() / 2.0) * client.getWindow().getScreenHeight() / client.screen.height};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(8);
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

    private static void clickToggle(ClientGameTestContext context, String option) {
        context.runOnClient(client -> {
            var descendants = new ArrayList<UIComponent>();
            root(client.screen).collectDescendants(descendants);
            String name = Component.translatable("text.config.arcane_spells-client.option." + option).getString();
            for (UIComponent component : descendants) {
                if (!(component instanceof ParentUIComponent parent)) continue;
                boolean matchingRow = parent.children().stream().anyMatch(child -> child instanceof LabelComponent label
                        && "option-name".equals(label.id()) && name.equals(label.text().getString()));
                if (!matchingRow) continue;
                ButtonComponent toggle = parent.childById(ButtonComponent.class, "toggle-button");
                check(toggle != null, "No config toggle for " + option);
                click(client.screen, toggle);
                return;
            }
            throw new AssertionError("No generated config row for " + option);
        });
        context.waitTicks(2);
    }

    private static void click(Screen screen, UIComponent component) {
        check(component != null, "Missing button component");
        check(component.x() >= 0 && component.y() >= 0 && component.x() + component.width() <= screen.width
                && component.y() + component.height() <= screen.height, "Button is outside the screen: " + component.id());
        MouseButtonEvent click = new MouseButtonEvent(component.x() + component.width() / 2.0,
                component.y() + component.height() / 2.0, new MouseButtonInfo(0, 0));
        check(screen.mouseClicked(click, false), "owo component ignored the click: " + component.id());
        screen.mouseReleased(click);
    }

    private static void assertBookBounds(Screen screen, boolean talents) {
        ParentUIComponent root = root(screen);
        UIComponent frame = root.childById(UIComponent.class, "book-frame");
        check(frame != null, "Book frame is missing");
        check(frame.x() >= 0 && frame.y() >= 0 && frame.x() + frame.width() <= screen.width
                && frame.y() + frame.height() <= screen.height, "Book frame escaped the screen");
        int previousRight = -1, tabWidth = -1;
        for (String tab : new String[] {"spells", "progression", "talents"}) {
            UIComponent button = root.childById(UIComponent.class, "book-tab-" + tab);
            inside(button, frame, true);
            check(button.x() >= previousRight, "Book tabs overlap: " + tab);
            check(tabWidth < 0 || button.width() == tabWidth, "Book tabs have unequal widths");
            previousRight = button.x() + button.width();
            tabWidth = button.width();
        }
        inside(root.childById(UIComponent.class, "book-settings"), frame, true);
        inside(root.childById(UIComponent.class, "book-done"), frame, true);
        if (!talents) return;
        UIComponent page = root.childById(UIComponent.class, "book-page");
        TalentTree tree = tree(screen);
        inside(tree, page, true);
        // Every node is on the page, and none overlaps another.
        List<int[]> placed = new ArrayList<>();
        for (ArcaneTalent talent : ArcaneTalent.values()) {
            for (int rank = 1; rank <= talent.maxRank(); rank++) {
                int[] node = tree.bounds(talent, rank);
                check(node[0] >= tree.x() && node[1] >= tree.y() && node[0] + node[2] <= tree.x() + tree.width()
                        && node[1] + node[2] <= tree.y() + tree.height(), "Talent node off the page: " + talent + " " + rank);
                for (int[] other : placed) {
                    check(node[0] >= other[0] + other[2] || other[0] >= node[0] + node[2] || node[1] >= other[1] + other[2]
                            || other[1] >= node[1] + node[2], "Talent nodes overlap: " + talent + " " + rank);
                }
                placed.add(node);
            }
        }
    }

    private static TalentTree tree(Screen screen) {
        TalentTree tree = root(screen).childById(TalentTree.class, "talent-tree");
        check(tree != null, "The talents page has no tree");
        return tree;
    }

    /** A real click on a node of the talent tree. */
    private static void clickNode(ClientGameTestContext context, ArcaneTalent talent, int rank) {
        context.runOnClient(client -> {
            int[] node = tree(client.screen).bounds(talent, rank);
            MouseButtonEvent click = new MouseButtonEvent(node[0] + node[2] / 2.0, node[1] + node[2] / 2.0, new MouseButtonInfo(0, 0));
            client.screen.mouseClicked(click, false);
            client.screen.mouseReleased(click);
        });
        context.waitTicks(5);
    }

    private static void hoverNode(ClientGameTestContext context, ArcaneTalent talent, int rank) {
        double[] cursor = context.computeOnClient(client -> {
            int[] node = tree(client.screen).bounds(talent, rank);
            // TestInput uses physical window coordinates; owo components use scaled GUI coordinates.
            return new double[] {(node[0] + node[2] / 2.0) * client.getWindow().getScreenWidth() / client.screen.width,
                    (node[1] + node[2] / 2.0) * client.getWindow().getScreenHeight() / client.screen.height};
        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTicks(8);
    }

    private static void inside(UIComponent child, UIComponent parent, boolean vertical) {
        check(child != null && parent != null, "Missing component in book bounds check");
        check(child.width() > 0 && child.height() > 0 && child.x() >= parent.x()
                && child.x() + child.width() <= parent.x() + parent.width(), "Horizontal overflow: " + child.id());
        if (vertical) check(child.y() >= parent.y() && child.y() + child.height() <= parent.y() + parent.height(),
                "Vertical overflow: " + child.id());
    }

    private static ParentUIComponent root(Screen screen) {
        check(screen != null, "No screen is open");
        return screen.children().stream().filter(OwoUIAdapter.class::isInstance).map(OwoUIAdapter.class::cast)
                .map(adapter -> (ParentUIComponent) adapter.rootComponent).findFirst()
                .orElseThrow(() -> new AssertionError("Screen has no owo adapter: " + screen.getClass().getSimpleName()));
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
