package dev.arcane.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arcane.registry.ModItems;
import dev.emi.trinkets.api.client.TrinketRenderer;
import dev.emi.trinkets.api.client.TrinketRendererRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws the worn trinkets on the wearer: the amulet hanging at the chest, the charm at the right hip. They move with the
 * body. Rings would be a pixel or two at this scale, so they go unseen.
 */
public final class TrinketRenderers {
    private TrinketRenderers() {
    }

    public static void register() {
        // Just below the collar. Model space has y down, so the item is turned the right way up.
        TrinketRendererRegistry.registerRenderer(ModItems.ASTRAL_AMULET, worn(0, -0.12f, 0.32f));
        // Hanging from the belt at the right hip: the wearer's right is -x.
        TrinketRendererRegistry.registerRenderer(ModItems.CHRONO_CHARM, worn(-0.13f, 0.3f, 0.26f));
    }

    /** Drawn flat against the front of the body, offset from the chest by x and y (blocks), at the given size. */
    private static TrinketRenderer worn(float x, float y, float size) {
        return (stack, slot, model, pose, collector, light, state, limbAngle, limbDistance) -> {
            if (!(model instanceof HumanoidModel<?> humanoid) || !(state instanceof HumanoidRenderState body)) return;
            pose.pushPose();
            TrinketRenderer.translateToChest(pose, humanoid, body);
            pose.translate(x, y, -0.01f);
            pose.mulPose(Axis.ZP.rotationDegrees(180));
            pose.scale(size, size, size);
            draw(stack, pose, collector, light, state);
            pose.popPose();
        };
    }

    private static void draw(ItemStack stack, PoseStack pose, SubmitNodeCollector collector, int light, LivingEntityRenderState state) {
        Minecraft client = Minecraft.getInstance();
        // A state of its own each time: what is submitted is drawn later in the frame, so it must not be reused.
        ItemStackRenderState item = new ItemStackRenderState();
        client.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.FIXED, client.level, null, 0);
        item.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, state.outlineColor);
    }
}
