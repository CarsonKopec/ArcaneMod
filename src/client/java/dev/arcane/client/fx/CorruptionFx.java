package dev.arcane.client.fx;

import dev.arcane.ArcaneSpells;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.effect.EffectRenderers;
import dev.leyline.client.screen.ScreenEffects;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Corruption as its carrier sees it. Past the backlash threshold a violet haze closes in at the edges of sight, deeper
 * and pulsing faster the nearer the maximum; and a backlash makes the view lurch and darken for a moment. Both follow
 * Screen Effect Scale (none at 0%).
 */
public final class CorruptionFx {
    private static final Identifier OVERLAY = ArcaneSpells.id("corruption");
    /** White, clear in the middle and opaque at the edges; tinted when drawn. */
    private static final Identifier VIGNETTE = ArcaneSpells.id("textures/misc/corruption_vignette.png");
    private static final int VIGNETTE_RGB = 0x7A2CC8;
    private static final int BACKLASH_RGB = 0x2A0838;
    private static final int BACKLASH_TICKS = 24;
    private static int backlash;

    private CorruptionFx() {
    }

    public static void register() {
        // Under the screen effects' darkening and flashes, over vanilla's own overlays.
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, OVERLAY, CorruptionFx::render);
        EffectRenderers.register(ModSpellEffects.BACKLASH, (effect, level) -> {
            Player player = Minecraft.getInstance().player;
            if (player == null || player.getId() != effect.sourceId()) return;
            backlash = BACKLASH_TICKS;
            ScreenEffects.shake(0.4f + 0.3f * effect.power());
        });
    }

    public static void tick() {
        if (backlash <= 0) return;
        ScreenEffects.tint(0.7f * backlash / BACKLASH_TICKS, BACKLASH_RGB);
        backlash--;
    }

    public static void clear() {
        backlash = 0;
    }

    /** How strongly the haze shows for {@code corruption}, 0 at the threshold to 1 at the maximum. */
    public static float haze(int corruption) {
        return Mth.clamp((corruption - SpellTuning.BACKLASH_THRESHOLD) / (float) (SpellTuning.CORRUPTION_MAX - SpellTuning.BACKLASH_THRESHOLD), 0, 1);
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft client = Minecraft.getInstance();
        Player player = client.player;
        if (player == null || player.isSpectator() || client.options.hideGui) return;
        float haze = haze(ArcaneProgression.get(player).corruption());
        float scale = client.options.screenEffectScale().get().floatValue();
        if (haze <= 0 || scale <= 0) return;
        float time = player.tickCount + delta.getGameTimeDeltaPartialTick(false);
        // A slow breath at the threshold, quickening to a heartbeat at the maximum.
        float pulse = 0.8f + 0.2f * Mth.sin(time * (0.06f + 0.18f * haze));
        float alpha = (0.25f + 0.45f * haze) * pulse * scale;
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, VIGNETTE, 0, 0, 0, 0, width, height, width, height,
                ARGB.color(Mth.clamp(alpha, 0, 1), VIGNETTE_RGB));
    }
}
