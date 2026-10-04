package dev.arcane.client.fx;

import dev.arcane.ArcaneSpells;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

/**
 * Whole-screen feedback for the biggest spells: camera shake, a flash, and a darkening of the view. Each respects the
 * player's accessibility settings: shake scales with Screen Effect Scale (none at 0%), and there is no flash with
 * Hide Lightning Flashes on.
 *
 * <p>Shake is "trauma": a level that spells raise and that drains away by itself; the camera turns by its square, so
 * small amounts barely register and big ones hit hard. The camera itself is turned by {@code CameraMixin}, which never
 * moves the player's actual aim.
 */
public final class ScreenEffects {
    private static final Identifier OVERLAY = ArcaneSpells.id("screen_effects");
    /** How far the camera turns at full trauma, before Screen Effect Scale. */
    private static final float MAX_SHAKE_DEGREES = 3.0f;
    /** Full trauma settles in two seconds. */
    private static final float TRAUMA_DECAY = 1 / 40f;
    /** Deep indigo, darkest at the top of the screen where the sky is. */
    private static final int TINT_RGB = 0x0C0620;
    private static final int FLASH_RGB = 0xFFF4DC;

    private static float trauma;
    private static float previousTrauma;
    private static float flash;
    private static float previousFlash;
    private static float tint;
    private static float previousTint;
    private static float tintTarget;
    private static int ticks;

    private ScreenEffects() {
    }

    public static void register() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, OVERLAY, ScreenEffects::render);
    }

    /** Shakes the camera at least this hard, 0 to 1; it then settles by itself. Call each tick to sustain it. */
    public static void shake(float strength) {
        trauma = Math.max(trauma, Mth.clamp(strength, 0, 1));
    }

    /** Flashes the screen at least this bright, 0 to 1, fading within half a second. */
    public static void flash(float strength) {
        flash = Math.max(flash, Mth.clamp(strength, 0, 1));
    }

    /** Darkens the screen towards {@code strength}, 0 to 1, for this tick: call every tick it should last. */
    public static void tint(float strength) {
        tintTarget = Math.max(tintTarget, Mth.clamp(strength, 0, 1));
    }

    public static void tick() {
        ticks++;
        previousTrauma = trauma;
        trauma = Math.max(0, trauma - TRAUMA_DECAY);
        previousFlash = flash;
        flash = flash < 0.02f ? 0 : flash * 0.72f;
        previousTint = tint;
        // Eases in and out over about a second.
        tint += (tintTarget - tint) * 0.1f;
        if (tint < 0.002f) tint = 0;
        tintTarget = 0;
    }

    public static void clear() {
        trauma = previousTrauma = flash = previousFlash = tint = previousTint = tintTarget = 0;
    }

    /** Degrees to add to the camera's yaw this frame. */
    public static float shakeYaw(float partialTick) {
        float t = ticks + partialTick;
        return shakeDegrees(partialTick) * (0.6f * Mth.sin(t * 1.9f) + 0.4f * Mth.sin(t * 3.7f + 1.3f));
    }

    /** Degrees to add to the camera's pitch this frame. */
    public static float shakePitch(float partialTick) {
        float t = ticks + partialTick;
        return shakeDegrees(partialTick) * (0.6f * Mth.sin(t * 2.3f + 0.7f) + 0.4f * Mth.sin(t * 4.1f + 2.1f));
    }

    private static float shakeDegrees(float partialTick) {
        float level = Mth.lerp(partialTick, previousTrauma, trauma);
        if (level <= 0) return 0;
        float scale = Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        return level * level * MAX_SHAKE_DEGREES * scale;
    }

    private static void render(GuiGraphics graphics, DeltaTracker delta) {
        float partialTick = delta.getGameTimeDeltaPartialTick(false);
        int width = graphics.guiWidth(), height = graphics.guiHeight();
        float darkness = Mth.lerp(partialTick, previousTint, tint);
        if (darkness > 0) {
            graphics.fillGradient(0, 0, width, height, argb(darkness * 0.62f, TINT_RGB), argb(darkness * 0.3f, TINT_RGB));
        }
        float brightness = Mth.lerp(partialTick, previousFlash, flash);
        if (brightness > 0 && !Minecraft.getInstance().options.hideLightningFlash().get()) {
            graphics.fill(0, 0, width, height, argb(brightness * 0.85f, FLASH_RGB));
        }
    }

    private static int argb(float alpha, int rgb) {
        return Mth.clamp(Math.round(alpha * 255), 0, 255) << 24 | rgb;
    }
}
