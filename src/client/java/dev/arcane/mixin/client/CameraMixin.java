package dev.arcane.mixin.client;

import dev.arcane.client.fx.ScreenEffects;
import net.minecraft.client.Camera;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies {@link ScreenEffects}' shake, and its pull towards whatever a spell draws the eye to, to the view only: the
 * player's aim, and what they hit, are untouched.
 */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract Vec3 position();

    @Inject(method = "setup", at = @At("TAIL"))
    private void arcane_spells$shake(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        float yaw = ScreenEffects.shakeYaw(partialTick), pitch = ScreenEffects.shakePitch(partialTick);
        float[] pull = ScreenEffects.pullDegrees(position(), yRot(), xRot(), partialTick);
        if (pull != null) {
            yaw += pull[0];
            pitch += pull[1];
        }
        if (yaw != 0 || pitch != 0) setRotation(yRot() + yaw, Mth.clamp(xRot() + pitch, -90, 90));
    }
}
