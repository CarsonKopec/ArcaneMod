package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Damage types the mod defines in data ({@code data/arcane_spells/damage_type}), for their own death messages. */
public final class ModDamageTypes {
    /** Crushed by, or caught in the impact of, a falling planet. */
    public static final ResourceKey<DamageType> PLANETFALL = ResourceKey.create(Registries.DAMAGE_TYPE, ArcaneSpells.id("planetfall"));

    private ModDamageTypes() {
    }

    /** Damage of type {@code type} dealt by {@code attacker}. */
    public static DamageSource source(Level level, ResourceKey<DamageType> type, Entity attacker) {
        return new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(type), attacker);
    }
}
