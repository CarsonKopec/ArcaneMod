package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.spell.Spell;
import dev.arcane.spell.ArcaneBolt;
import dev.arcane.spell.ArcaneWard;
import dev.arcane.spell.AstralGate;
import dev.arcane.spell.Blink;
import dev.arcane.spell.CelestialJudgment;
import dev.arcane.spell.ChainLightning;
import dev.arcane.spell.ChronoField;
import dev.arcane.spell.FrostNova;
import dev.arcane.spell.MeteorSwarm;
import dev.arcane.spell.Singularity;
import net.minecraft.core.Registry;

/** The built-in spells, in wheel order. Registered exactly as another mod would register its own. */
public final class ModSpells {
    public static final Spell ARCANE_BOLT = register("arcane_bolt", new ArcaneBolt());
    public static final Spell CHAIN_LIGHTNING = register("chain_lightning", new ChainLightning());
    public static final Spell BLINK = register("blink", new Blink());
    public static final Spell ARCANE_WARD = register("arcane_ward", new ArcaneWard());
    public static final Spell FROST_NOVA = register("frost_nova", new FrostNova());
    // The Astral Staff's advanced spells.
    public static final Spell CELESTIAL_JUDGMENT = register("celestial_judgment", new CelestialJudgment());
    public static final Spell METEOR_SWARM = register("meteor_swarm", new MeteorSwarm());
    public static final Spell SINGULARITY = register("singularity", new Singularity());
    public static final Spell CHRONO_FIELD = register("chrono_field", new ChronoField());
    public static final Spell ASTRAL_GATE = register("astral_gate", new AstralGate());

    private ModSpells() {
    }

    /** Loading this class registers the spells. */
    public static void register() {
    }

    private static Spell register(String name, Spell spell) {
        return Registry.register(SpellRegistries.SPELLS, ArcaneSpells.id(name), spell);
    }
}
