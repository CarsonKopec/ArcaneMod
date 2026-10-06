package dev.arcane.item;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import dev.emi.trinkets.api.SlotReference;
import dev.emi.trinkets.api.TrinketItem;
import dev.emi.trinkets.api.TrinketsApi;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * A trinket, worn in one of Trinkets' slots: which slots it fits is an item tag under {@code data/trinkets/tags/item}.
 * Using it equips it. Its attribute bonus, if it has one, applies once for each slot it is worn in, so two rings count
 * twice, and Trinkets lists it in the tooltip. Trinkets without one are checked for where they matter, with {@link #worn}.
 */
public class ArcaneTrinketItem extends TrinketItem {
    private final Holder<Attribute> attribute;
    private final double amount;

    public ArcaneTrinketItem(Properties properties, Holder<Attribute> attribute, double amount) {
        super(properties);
        this.attribute = attribute;
        this.amount = amount;
    }

    /** A trinket with no attribute bonus. */
    public ArcaneTrinketItem(Properties properties) {
        this(properties, null, 0);
    }

    @Override public Multimap<Holder<Attribute>, AttributeModifier> getModifiers(ItemStack stack, SlotReference slot,
            LivingEntity entity, Identifier slotIdentifier) {
        // Whatever the stack's own component adds, then the bonus, under the slot's id so each slot's copy is its own.
        Multimap<Holder<Attribute>, AttributeModifier> modifiers = HashMultimap.create(super.getModifiers(stack, slot, entity, slotIdentifier));
        if (attribute != null) modifiers.put(attribute, new AttributeModifier(slotIdentifier, amount, AttributeModifier.Operation.ADD_VALUE));
        return modifiers;
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip,
            TooltipFlag flag) {
        tooltip.accept(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }

    /** Whether {@code entity} wears {@code item} in any trinket slot. Either side: Trinkets syncs what is worn. */
    public static boolean worn(LivingEntity entity, Item item) {
        return TrinketsApi.getTrinketComponent(entity).map(trinkets -> trinkets.isEquipped(item)).orElse(false);
    }
}
