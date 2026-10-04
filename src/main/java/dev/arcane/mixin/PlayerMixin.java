package dev.arcane.mixin;

import dev.arcane.api.SpellAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives players the mana attributes. Appending to vanilla's list composes with other mods doing the same, where
 * replacing the player's whole list through {@code FabricDefaultAttributeRegistry} would drop theirs or ours.
 */
@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "createAttributes", at = @At("RETURN"))
    private static void addManaAttributes(CallbackInfoReturnable<AttributeSupplier.Builder> cir) {
        cir.getReturnValue().add(SpellAttributes.MAX_MANA).add(SpellAttributes.MANA_REGEN);
    }
}
