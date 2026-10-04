package dev.arcane.api.effect;

import dev.arcane.api.SpellRegistries;
import net.minecraft.resources.Identifier;

/**
 * A kind of visual event, such as a bolt's impact. Register one in {@link SpellRegistries#SPELL_EFFECTS} for each
 * kind your spells send, document what its {@link SpellEffect} fields mean, and draw it on the client with
 * {@code SpellEffectRenderers}.
 */
public final class SpellEffectType {
    public Identifier id() {
        return SpellRegistries.SPELL_EFFECTS.getKey(this);
    }

    @Override public String toString() {
        return String.valueOf(id());
    }
}
