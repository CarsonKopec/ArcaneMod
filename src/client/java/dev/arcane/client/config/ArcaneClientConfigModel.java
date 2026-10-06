package dev.arcane.client.config;

import io.wispforest.owo.config.annotation.Config;

/** Local presentation preferences. Gameplay and progression remain controlled by the server. */
@Config(name = "arcane_spells-client", wrapperName = "ArcaneVisualConfig")
public class ArcaneClientConfigModel {
    public boolean castingAnimations = true;
    public boolean staffAnimations = true;
    /** Planetfall's glow: its bloom and lens streaks. Auto leaves it off on Intel's integrated graphics. */
    public PlanetfallGlow planetfallGlow = PlanetfallGlow.AUTO;

    public enum PlanetfallGlow {
        AUTO, ON, OFF
    }
}
