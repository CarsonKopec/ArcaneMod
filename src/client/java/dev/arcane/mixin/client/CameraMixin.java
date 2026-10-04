package dev.arcane.mixin.client;

import dev.arcane.client.fx.ScreenEffects;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies {@link ScreenEffects}' shake to the view only: the player's aim, and what they hit, are untouched. */
@Mixin(Camera.class)
abstract class CameraMixin {
    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract float xRot();

    @Inject(method = "setup", at = @At("TAIL"))
    private void arcane_spells$shake(Level level, Entity entity, boolean detached, boolean mirrored, float partialTick, CallbackInfo ci) {
        float yaw = ScreenEffects.shakeYaw(partialTick), pitch = ScreenEffects.shakePitch(partialTick);
        if (yaw != 0 || pitch != 0) setRotation(yRot() + yaw, xRot() + pitch);
    }
}
