package dev.arcane.mixin.client;

import dev.arcane.client.fx.SpaceSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.client.renderer.state.SkyRenderState;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Darkens the sky to space and brings out the stars while a Planetfall's rift is open ({@link SpaceSky}). */
@Mixin(SkyRenderer.class)
abstract class SkyRendererMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void arcane_spells$space(ClientLevel level, float partialTick, Camera camera, SkyRenderState state, CallbackInfo ci) {
        float space = SpaceSky.strength(partialTick);
        if (space <= 0) return;
        state.skyColor = SpaceSky.sky(state.skyColor, space);
        state.starBrightness = Mth.lerp(space, state.starBrightness, 1);
        // No sunset glow in space.
        int glow = state.sunriseAndSunsetColor;
        state.sunriseAndSunsetColor = ARGB.color(Math.round(ARGB.alpha(glow) * (1 - space)), ARGB.red(glow), ARGB.green(glow), ARGB.blue(glow));
    }
}
