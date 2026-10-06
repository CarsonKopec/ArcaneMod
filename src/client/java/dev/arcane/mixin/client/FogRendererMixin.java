package dev.arcane.mixin.client;

import dev.arcane.client.fx.SpaceSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Darkens the fog, and so the horizon, to space while a Planetfall's rift is open ({@link SpaceSky}). */
@Mixin(FogRenderer.class)
abstract class FogRendererMixin {
    @Inject(method = "computeFogColor", at = @At("RETURN"))
    private void arcane_spells$space(Camera camera, float partialTick, ClientLevel level, int renderDistance, float darkenWorld,
            CallbackInfoReturnable<Vector4f> cir) {
        float space = SpaceSky.strength(partialTick);
        if (space > 0) SpaceSky.fog(cir.getReturnValue(), space);
    }
}
