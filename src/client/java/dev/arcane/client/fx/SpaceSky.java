package dev.arcane.client.fx;

import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.joml.Vector4f;

/**
 * Turns the sky to outer space while a Planetfall's rift is open, black and full of stars even at noon. The sky and fog
 * colours are drawn towards a near-black violet and the stars shine out; the ground stays lit, as it would on the
 * Moon. Spells {@link #request} it every tick they want it, fading it in and out themselves; {@code SkyRendererMixin}
 * and {@code FogRendererMixin} apply it.
 */
public final class SpaceSky {
    private static final float RED = 0.024f, GREEN = 0.012f, BLUE = 0.06f;
    private static float previous, current, requested;

    private SpaceSky() {
    }

    /** Asks for the sky to be {@code amount} (0 to 1) of the way to space this tick; the strongest request wins. */
    static void request(float amount) {
        requested = Math.max(requested, Mth.clamp(amount, 0, 1));
    }

    static void tick() {
        previous = current;
        current = requested;
        requested = 0;
    }

    static void clear() {
        previous = current = requested = 0;
    }

    /** How far the sky is to space this frame, 0 to 1. */
    public static float strength(float partialTick) {
        return Mth.lerp(partialTick, previous, current);
    }

    /** {@code argb} drawn {@code amount} of the way to space, keeping its alpha. */
    public static int sky(int argb, float amount) {
        return ARGB.color(ARGB.alpha(argb), blend(ARGB.red(argb), RED, amount), blend(ARGB.green(argb), GREEN, amount),
                blend(ARGB.blue(argb), BLUE, amount));
    }

    /** Draws the fog colour (which also clears the screen behind the sky) {@code amount} of the way to space. */
    public static void fog(Vector4f color, float amount) {
        color.set(Mth.lerp(amount, color.x, RED), Mth.lerp(amount, color.y, GREEN), Mth.lerp(amount, color.z, BLUE), color.w);
    }

    private static int blend(int channel, float target, float amount) {
        return Math.round(Mth.lerp(amount, channel, target * 255));
    }
}
