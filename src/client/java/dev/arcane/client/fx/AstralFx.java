package dev.arcane.client.fx;

import dev.leyline.client.fx.Fx;
import net.minecraft.client.Minecraft;

/** The Astral Staff's visuals: each spell's renderers registered, ticked and cleared together. */
public final class AstralFx {
    /** Every Astral spell's running animations, ticked before the screen passes that draw what they ask for. */
    static final Fx.Trackers TRACKERS = new Fx.Trackers();

    private AstralFx() {
    }

    public static void register() {
        ChannelFx.register();
        JudgmentFx.register();
        MeteorFx.register();
        SingularityFx.register();
        ChronoFx.register();
        GateFx.register();
        PlanetfallFx.register();
    }

    public static void tick(Minecraft client) {
        TRACKERS.tick(client.level);
        PlanetfallFx.tick(client);
        SpaceSky.tick();
        PlanetfallShader.tick();
        SpaceTimeShader.tick();
    }

    public static void clear() {
        TRACKERS.clear();
        SpaceSky.clear();
        PlanetfallShader.clear();
        SpaceTimeShader.clear();
    }
}
