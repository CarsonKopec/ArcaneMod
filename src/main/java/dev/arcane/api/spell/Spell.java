package dev.arcane.api.spell;

import com.mojang.serialization.Codec;
import dev.arcane.api.SpellRegistries;
import dev.arcane.spell.SpellTuning;
import io.netty.buffer.ByteBuf;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * A spell a staff can cast. Subclass it, register one instance in {@link SpellRegistries#SPELLS}, then add it to the
 * tag of each staff that should hold it (see {@link dev.arcane.api.SpellTags}). The spell wheel, HUD, mana and
 * cooldowns pick it up from there.
 *
 * <p>Casting runs on the server only. Mana and cooldown are checked before {@link #cast} and charged only if it
 * returns true. To show the cast to players, send a {@linkplain dev.arcane.api.effect.SpellEffect spell effect}.
 *
 * <p>Presentation comes from the spell's id, {@code <namespace>:<path>}:
 * <ul>
 *   <li>name: the translation key {@code spell.<namespace>.<path>}, and its description {@code spell.<namespace>.<path>.description}</li>
 *   <li>icon: a 16×16 GUI sprite at {@code assets/<namespace>/textures/gui/sprites/spell/<path>.png}</li>
 * </ul>
 */
public abstract class Spell implements TooltipProvider {
    /** Saved by id. An unknown id reads back as Arcane Bolt, so removing a mod doesn't break staffs that held its spells. */
    public static final Codec<Spell> CODEC = Identifier.CODEC.xmap(SpellRegistries.SPELLS::getValue, SpellRegistries.SPELLS::getKey);
    /** Sent by registry id, which registry sync keeps the same on both sides. */
    public static final StreamCodec<ByteBuf, Spell> STREAM_CODEC = ByteBufCodecs.idMapper(SpellRegistries.SPELLS);

    private final float manaCost;
    private final int cooldownTicks;
    private final boolean charged;
    private final int fullChargeTicks;
    private final int color;
    private String descriptionId;

    protected Spell(Properties properties) {
        this.manaCost = properties.manaCost;
        this.cooldownTicks = properties.cooldownTicks;
        this.charged = properties.charged;
        this.fullChargeTicks = properties.fullChargeTicks;
        this.color = properties.color;
    }

    /**
     * Casts the spell. Return true if it was cast, which spends its mana and starts its cooldown; return false if it
     * failed (no target, no room), which costs nothing. Tell the caster why with
     * {@code caster.displayClientMessage(message, true)}.
     */
    public abstract boolean cast(SpellContext context);

    /** Charged spells only: the caster started holding right-click. {@link SpellContext#power} is 0. */
    public void onChargeStart(SpellContext context) {
    }

    /** Charged spells only: called every tick while charging, with the charge so far as {@link SpellContext#power}. */
    public void onChargeTick(SpellContext context, int chargedTicks) {
    }

    public final Identifier id() {
        return SpellRegistries.SPELLS.getKey(this);
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

    /** Charged spells only: how long the caster must hold to reach full power. */
    public int fullChargeTicks() {
        return fullChargeTicks;
    }

    /** Whether the spell is in {@code tag}, such as the list of spells a staff holds. */
    public boolean is(TagKey<Spell> tag) {
        return SpellRegistries.SPELLS.wrapAsHolder(this).is(tag);
    }

    /** ARGB accent colour for the spell's name and highlight on the wheel. */
    public int color() {
        return color;
    }

    /** GUI sprite id, drawn 16×16. */
    public Identifier icon() {
        return id().withPrefix("spell/");
    }

    public String descriptionId() {
        if (descriptionId == null) descriptionId = id().toLanguageKey("spell");
        return descriptionId;
    }

    public Component displayName() {
        return Component.translatable(descriptionId());
    }

    public Component description() {
        return Component.translatable(descriptionId() + ".description");
    }

    /** Tooltip for a staff holding this spell. */
    @Override public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltip, TooltipFlag flag,
                                       DataComponentGetter components) {
        tooltip.accept(Component.translatable("item.arcane_spells.arcane_staff.spell", displayName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        // A keybind component shows whatever key the player has bound, resolved on their client.
        tooltip.accept(Component.translatable("item.arcane_spells.arcane_staff.hint", Component.keybind("key.arcane_spells.spell_wheel"))
                .withStyle(ChatFormatting.GRAY));
    }

    @Override public String toString() {
        return id().toString();
    }

    public static final class Properties {
        private float manaCost;
        private int cooldownTicks;
        private boolean charged;
        private int fullChargeTicks = SpellTuning.FULL_CHARGE_TICKS;
        private int color = 0xFFC9A6FF;

        public Properties manaCost(float manaCost) {
            if (manaCost < 0) throw new IllegalArgumentException("Negative mana cost " + manaCost);
            this.manaCost = manaCost;
            return this;
        }

        public Properties cooldown(int ticks) {
            if (ticks < 0) throw new IllegalArgumentException("Negative cooldown " + ticks);
            this.cooldownTicks = ticks;
            return this;
        }

        /** Held to charge, firing on release with a power from 0 to 1, full after the usual 1.2 s. */
        public Properties charged() {
            this.charged = true;
            return this;
        }

        /** Held to charge, firing on release with a power from 0 to 1, full after {@code fullChargeTicks}. */
        public Properties charged(int fullChargeTicks) {
            if (fullChargeTicks < 1) throw new IllegalArgumentException("Full charge needs at least a tick, not " + fullChargeTicks);
            this.charged = true;
            this.fullChargeTicks = fullChargeTicks;
            return this;
        }

        /** ARGB, e.g. {@code 0xFFB46CFF}. */
        public Properties color(int argb) {
            this.color = argb;
            return this;
        }
    }
}
