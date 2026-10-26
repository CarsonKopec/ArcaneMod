package dev.arcane.api;

import dev.arcane.api.spell.Spell;
import net.fabricmc.fabric.api.event.registry.FabricRegistryBuilder;
import net.fabricmc.fabric.api.event.registry.RegistryAttribute;
import net.minecraft.core.DefaultedMappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * The registry other mods add spells to, during their own initializer:
 *
 * <pre>{@code
 * public static final Spell FIREBALL = Registry.register(SpellRegistries.SPELLS,
 *         Identifier.fromNamespaceAndPath("mymod", "fireball"), new Fireball());
 * }</pre>
 *
 * <p>It's synced: a client missing a spell the server has is refused at login, with a message naming it, rather than
 * joining and failing later. A spell's visual effects are Leyline's: register their types in
 * {@code dev.leyline.LeylineRegistries#EFFECT_TYPES} and send them with {@code dev.leyline.effect.Effects}.
 */
public final class SpellRegistries {
    public static final ResourceKey<Registry<Spell>> SPELL = ResourceKey.createRegistryKey(Identifier.fromNamespaceAndPath("arcane_spells", "spell"));

    /** Every spell a staff can cast. Unknown ids (say, from a removed mod) read back as Arcane Bolt. */
    public static final DefaultedMappedRegistry<Spell> SPELLS = FabricRegistryBuilder
            .createDefaulted(SPELL, Identifier.fromNamespaceAndPath("arcane_spells", "arcane_bolt"))
            .attribute(RegistryAttribute.SYNCED).buildAndRegister();

    private SpellRegistries() {
    }
}
