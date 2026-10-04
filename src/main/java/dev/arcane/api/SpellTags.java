package dev.arcane.api;

import dev.arcane.api.spell.Spell;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;

/**
 * Which spells each staff holds: a staff's wheel shows the spells in its tag, and it casts nothing else. Tags are
 * data, so a mod (or a datapack) adds a spell to a staff with a file such as
 * {@code data/arcane_spells/tags/arcane_spells/spell/arcane_staff.json}:
 *
 * <pre>{@code
 * {"values": ["mymod:fireball"]}
 * }</pre>
 *
 * <p>Tags from every mod and datapack are merged, unless one sets {@code "replace": true}.
 */
public final class SpellTags {
    /** The Arcane Staff's everyday spells. */
    public static final TagKey<Spell> ARCANE_STAFF = create("arcane_staff");
    /** The Astral Staff's advanced spells: costly, slow and devastating. */
    public static final TagKey<Spell> ASTRAL_STAFF = create("astral_staff");

    private SpellTags() {
    }

    private static TagKey<Spell> create(String name) {
        return TagKey.create(SpellRegistries.SPELL, Identifier.fromNamespaceAndPath("arcane_spells", name));
    }
}
