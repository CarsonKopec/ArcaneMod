package dev.arcane.client.fx;

import net.minecraft.client.Minecraft;

/** The Astral Staff's visuals: each spell's renderers registered, ticked and cleared together. */
public final class AstralFx {
    private AstralFx() {
    }

    public static void register() {
        ChannelFx.register();
        JudgmentFx.register();
        MeteorFx.register();
        SingularityFx.register();
        ChronoFx.register();
        GateFx.register();
    }

    public static void tick(Minecraft client) {
        for (Fx.Tracker<?> tracker : Fx.TRACKERS) tracker.tick(client.level);
    }

    public static void clear() {
        for (Fx.Tracker<?> tracker : Fx.TRACKERS) tracker.clear();
    }
}
