package dev.arcane.client.config;

import dev.arcane.ArcaneSpells;
import io.wispforest.owo.config.ui.ConfigScreen;
import io.wispforest.owo.config.ui.ConfigScreenProviders;
import net.minecraft.client.gui.screens.Screen;

/** The single client config instance shared by the animation renderers and the settings screen. */
public final class ArcaneClientSettings {
    private static final ArcaneVisualConfig CONFIG = ArcaneVisualConfig.createAndLoad();
    private static boolean registered;

    private ArcaneClientSettings() {
    }

    public static void register() {
        if (registered) return;
        registered = true;
        ConfigScreenProviders.register(ArcaneSpells.ID, ArcaneClientSettings::screen);
    }

    public static boolean castingAnimations() {
        return CONFIG.castingAnimations();
    }

    public static boolean staffAnimations() {
        return CONFIG.staffAnimations();
    }

    public static ArcaneClientConfigModel.PlanetfallGlow planetfallGlow() {
        return CONFIG.planetfallGlow();
    }

    public static void setCastingAnimations(boolean enabled) {
        CONFIG.castingAnimations(enabled);
    }

    public static void setStaffAnimations(boolean enabled) {
        CONFIG.staffAnimations(enabled);
    }

    public static Screen screen(Screen parent) {
        return ConfigScreen.create(CONFIG, parent);
    }
}
