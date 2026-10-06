package dev.arcane.client.fx;

import dev.arcane.ArcaneSpells;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Whole-screen feedback for the biggest spells: camera shake, a flash, a darkening of the view, and the camera itself
 * widened or narrowed, rolled, and drawn towards something. Each respects the player's accessibility settings: shake,
 * roll and the pull scale with Screen Effect Scale (none at 0%), the field of view with FOV Effects, and there is no
 * flash with Hide Lightning Flashes on.
 *
 * <p>Shake is "trauma": a level that spells raise and that drains away by itself; the camera turns by its square, so
 * small amounts barely register and big ones hit hard. Field of view, roll and pull are asked for every tick they
 * should last, the strongest request winning, and ease towards it. The camera itself is turned by {@code CameraMixin},
 * rolled and widened by {@code GameRendererMixin}; neither ever moves the player's actual aim.
 */
public final class ScreenEffects {
    private static final Identifier OVERLAY = ArcaneSpells.id("screen_effects");
    /** How far the camera turns at full trauma, before Screen Effect Scale. */
    private static final float MAX_SHAKE_DEGREES = 3.0f;
    /** Full trauma settles in two seconds. */
    private static final float TRAUMA_DECAY = 1 / 40f;
    private static final int FLASH_RGB = 0xFFF4DC;
    /** The most the camera turns towards what it is drawn to, at full strength. */
    private static final float MAX_PULL_DEGREES = 9.0f;
    /** How much of the way to its target the field of view, roll and pull move each tick. */
    private static final float EASE = 0.3f;

    private static float trauma;
    private static float previousTrauma;
    private static float flash;
    private static float previousFlash;
    private static float tint;
    private static float previousTint;
    private static float tintTarget;
    /** The darkening's colour: whichever spell asked for the most this tick sets it. */
    private static int tintRgb;
    private static int tintTargetRgb;
    private static int ticks;
    private static float fov = 1, previousFov = 1, fovTarget = 1;
    private static float roll, previousRoll, rollTarget;
    private static float pull, previousPull, pullTarget;
    private static Vec3 pullAt, pullTargetAt;

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

    /**
     * Darkens the screen towards {@code strength}, 0 to 1, in colour {@code rgb} (a deep shade), for this tick: call
     * every tick it should last. It's darkest at the top of the screen, where the sky is.
     */
    public static void tint(float strength, int rgb) {
        strength = Mth.clamp(strength, 0, 1);
        if (strength > tintTarget) {
            tintTarget = strength;
            tintTargetRgb = rgb;
        }
    }

    /** Widens (above 1) or narrows the field of view by this factor, for this tick. */
    public static void fov(float scale) {
        if (Math.abs(scale - 1) > Math.abs(fovTarget - 1)) fovTarget = scale;
    }

    /** Rolls the camera this many degrees (clockwise is positive), for this tick. */
    public static void roll(float degrees) {
        if (Math.abs(degrees) > Math.abs(rollTarget)) rollTarget = degrees;
    }

    /** Draws the camera's gaze towards {@code at}, 0 to 1 of the way to its strongest, for this tick. */
    public static void pull(Vec3 at, float strength) {
        if (strength > pullTarget) {
            pullTarget = Mth.clamp(strength, 0, 1);
            pullTargetAt = at;
        }
    }

    public static void tick() {
        ticks++;
        previousFov = fov;
        fov += (fovTarget - fov) * EASE;
        fovTarget = 1;
        previousRoll = roll;
        roll += (rollTarget - roll) * EASE;
        rollTarget = 0;
        previousPull = pull;
        pull += (pullTarget - pull) * EASE * 0.5f;
        if (pullTargetAt != null) pullAt = pullTargetAt;
        pullTarget = 0;
        pullTargetAt = null;
        previousTrauma = trauma;
        trauma = Math.max(0, trauma - TRAUMA_DECAY);
        previousFlash = flash;
        flash = flash < 0.02f ? 0 : flash * 0.72f;
        previousTint = tint;
        // A fading tint keeps its colour.
        if (tintTarget > 0) tintRgb = tintTargetRgb;
        // Eases in and out over about a second.
        tint += (tintTarget - tint) * 0.1f;
        if (tint < 0.002f) tint = 0;
        tintTarget = 0;
    }

    public static void clear() {
        trauma = previousTrauma = flash = previousFlash = tint = previousTint = tintTarget = 0;
        fov = previousFov = fovTarget = 1;
        roll = previousRoll = rollTarget = pull = previousPull = pullTarget = 0;
        pullAt = pullTargetAt = null;
    }

    /** The factor to multiply the field of view by this frame. */
    public static float fovScale(float partialTick) {
        float scale = Mth.lerp(partialTick, previousFov, fov);
        return 1 + (scale - 1) * Minecraft.getInstance().options.fovEffectScale().get().floatValue();
    }

    /** Degrees to roll the camera this frame. */
    public static float rollDegrees(float partialTick) {
        return Mth.lerp(partialTick, previousRoll, roll) * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
    }

    /**
     * Degrees to add to the camera's yaw and pitch this frame, turning it from {@code yaw}, {@code pitch} at
     * {@code eye} towards what it is drawn to. Never more than a few degrees: something behind you draws it hardly
     * at all, so the view is never swung round.
     */
    public static float[] pullDegrees(Vec3 eye, float yaw, float pitch, float partialTick) {
        float strength = Mth.lerp(partialTick, previousPull, pull) * Minecraft.getInstance().options.screenEffectScale().get().floatValue();
        if (strength <= 0.001f || pullAt == null) return null;
        Vec3 to = pullAt.subtract(eye);
        double horizontal = Math.sqrt(to.x * to.x + to.z * to.z);
        float wantYaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90;
        float wantPitch = (float) -(Mth.atan2(to.y, horizontal) * Mth.RAD_TO_DEG);
        float yawOff = Mth.wrapDegrees(wantYaw - yaw), pitchOff = wantPitch - pitch;
        float off = (float) Math.sqrt(yawOff * yawOff + pitchOff * pitchOff);
        // Fades out between 60 and 120 degrees off the view.
        float within = Mth.clamp((120 - off) / 60, 0, 1);
        float limit = MAX_PULL_DEGREES * strength * within;
        float scale = off > limit && off > 0 ? limit / off : strength * within;
        return new float[] {yawOff * scale, pitchOff * scale};
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
            graphics.fillGradient(0, 0, width, height, argb(darkness * 0.62f, tintRgb), argb(darkness * 0.3f, tintRgb));
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
