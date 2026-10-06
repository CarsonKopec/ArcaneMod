package dev.arcane.client.render;

import dev.arcane.client.config.ArcaneClientSettings;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.spell.SpellCasting;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;

/** Captures per-holder state before rendering; no live entity references escape into render tasks. */
final class StaffRenderer extends GeoItemRenderer<ArcaneStaffItem> {
    StaffRenderer(String asset) {
        super(new DefaultedItemGeoModel<>(Identifier.fromNamespaceAndPath("arcane_spells", asset)));
    }

    @Override public void addRenderData(ArcaneStaffItem item, RenderData data, GeoRenderState state, float partialTick) {
        boolean enabled = ArcaneClientSettings.staffAnimations();
        boolean channeling = false;
        float charge = 0;
        if (data != null && data.itemOwner() != null && heldPerspective(data.renderPerspective())) {
            LivingEntity holder = data.itemOwner().asLivingEntity();
            if (holder != null && holder.isUsingItem()
                    && isCastingHand(holder, data.renderPerspective())
                    && ItemStack.isSameItemSameComponents(holder.getUseItem(), data.itemStack())) {
                channeling = true;
                charge = Math.min(1, (holder.getTicksUsingItem() + partialTick)
                        / Math.max(1, (float) SpellCasting.selected(data.itemStack()).fullChargeTicks()));
            }
        }
        state.addGeckolibData(ArcaneStaffItem.STAFF_ANIMATIONS, enabled);
        state.addGeckolibData(ArcaneStaffItem.CHANNELING, channeling);
        state.addGeckolibData(ArcaneStaffItem.CHANNEL_POWER, charge);
    }

    private static boolean isCastingHand(LivingEntity holder, ItemDisplayContext context) {
        boolean leftArm = holder.getMainArm() == HumanoidArm.LEFT;
        if (holder.getUsedItemHand() == InteractionHand.OFF_HAND) leftArm = !leftArm;
        boolean leftView = context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        return leftArm == leftView;
    }

    private static boolean heldPerspective(ItemDisplayContext context) {
        return context == ItemDisplayContext.FIRST_PERSON_LEFT_HAND || context == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
                || context == ItemDisplayContext.THIRD_PERSON_LEFT_HAND || context == ItemDisplayContext.THIRD_PERSON_RIGHT_HAND;
    }
}
