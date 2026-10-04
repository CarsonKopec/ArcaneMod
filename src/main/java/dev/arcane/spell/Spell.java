package dev.arcane.spell;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ByIdMap;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Every spell the staff can cast. Saved by name; sent over the network by ordinal, which is safe because the client
 * and server must run the same mod version. Append new spells to keep the wheel's existing order.
 */
public enum Spell implements StringRepresentable, TooltipProvider {
    ARCANE_BOLT("arcane_bolt", SpellTuning.BOLT_MANA, SpellTuning.BOLT_COOLDOWN_TICKS, true),
    CHAIN_LIGHTNING("chain_lightning", SpellTuning.CHAIN_MANA, SpellTuning.CHAIN_COOLDOWN_TICKS, false),
    BLINK("blink", SpellTuning.BLINK_MANA, SpellTuning.BLINK_COOLDOWN_TICKS, false),
    ARCANE_WARD("arcane_ward", SpellTuning.WARD_MANA, SpellTuning.WARD_COOLDOWN_TICKS, false),
    FROST_NOVA("frost_nova", SpellTuning.NOVA_MANA, SpellTuning.NOVA_COOLDOWN_TICKS, false);

    public static final Codec<Spell> CODEC = StringRepresentable.fromEnum(Spell::values);
    public static final StreamCodec<ByteBuf, Spell> STREAM_CODEC = ByteBufCodecs.idMapper(
            ByIdMap.continuous(Spell::ordinal, values(), ByIdMap.OutOfBoundsStrategy.ZERO), Spell::ordinal);

    private final String name;
    private final float manaCost;
    private final int cooldownTicks;
    private final boolean charged;

    Spell(String name, float manaCost, int cooldownTicks, boolean charged) {
        this.name = name;
        this.manaCost = manaCost;
        this.cooldownTicks = cooldownTicks;
        this.charged = charged;
    }

    public float manaCost() {
        return manaCost;
    }

    public int cooldownTicks() {
        return cooldownTicks;
    }

    /** Charged spells are held to charge and fire on release; the others fire on click. */
    public boolean charged() {
        return charged;
    }

    public Component displayName() {
        return Component.translatable("spell.arcane_spells." + name);
    }

    public Component description() {
        return Component.translatable("spell.arcane_spells." + name + ".description");
    }

    /** Tooltip for a staff holding this spell. */
    @Override public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag,
                                       DataComponentGetter components) {
        tooltip.accept(Component.translatable("item.arcane_spells.arcane_staff.spell", displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        // A keybind component shows whatever key the player has bound, resolved on their client.
        tooltip.accept(Component.translatable("item.arcane_spells.arcane_staff.hint", Component.keybind("key.arcane_spells.spell_wheel"))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override public String getSerializedName() {
        return name;
    }
}
