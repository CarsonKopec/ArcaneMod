package dev.arcane.api;

import dev.arcane.api.effect.SpellEffectType;
import dev.arcane.api.spell.Spell;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The registries other mods add spells and spell effects to, during their own initializer:
 *
 * <pre>{@code
 * public static final Spell FIREBALL = Registry.register(SpellRegistries.SPELLS,
 *         Identifier.fromNamespaceAndPath("mymod", "fireball"), new Fireball());
 * }</pre>
 *
 * <p>Both are synced: a client missing a spell or effect the server has is refused at login, with a message naming
 * it, rather than joining and failing later.
 */
public final class SpellRegistries {
    public static final ResourceKey<Registry<Spell>> SPELL = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("arcane_spells", "spell"));
    public static final ResourceKey<Registry<SpellEffectType>> SPELL_EFFECT =
            ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("arcane_spells", "spell_effect"));

    /** Every spell a staff can cast. Unknown ids (say, from a removed mod) read back as Arcane Bolt. */
    public static final DefaultedMappedRegistry<Spell> SPELLS = FabricRegistryBuilder
            .createDefaulted(SPELL, Identifier.fromNamespaceAndPath("arcane_spells", "arcane_bolt"))
            .attribute(RegistryAttribute.SYNCED).buildAndRegister();
    /** Kinds of visual event the server can send with {@link ArcaneSpellsApi#sendEffect}. */
    public static final MappedRegistry<SpellEffectType> SPELL_EFFECTS = FabricRegistryBuilder.createSimple(SPELL_EFFECT)
            .attribute(RegistryAttribute.SYNCED).buildAndRegister();

    private SpellRegistries() {
    }
}
