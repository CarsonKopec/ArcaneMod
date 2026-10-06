package dev.arcane.client.render;

import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.registry.ModItems;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/** Client-only bridge between the common staff items and GeckoLib's special item renderer. */
public final class StaffRenderers {
    private StaffRenderers() {}

    public static void register() {
        register((ArcaneStaffItem) ModItems.ARCANE_STAFF, "arcane_staff");
        register((ArcaneStaffItem) ModItems.ASTRAL_STAFF, "astral_staff");
    }

    private static void register(ArcaneStaffItem item, String asset) {
        GeoRenderProvider provider = new GeoRenderProvider() {
            private GeoItemRenderer<ArcaneStaffItem> renderer;

            @Override public GeoItemRenderer<?> getGeoItemRenderer() {
                if (renderer == null) renderer = new StaffRenderer(asset);
                return renderer;
            }
        };
        item.setClientRenderProvider(() -> provider);
    }
}
