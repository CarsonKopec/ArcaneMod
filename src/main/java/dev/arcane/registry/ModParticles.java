package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

/** Tinted glow sprites; the colors themselves are assigned by the client particle providers. */
public final class ModParticles {
    public static final SimpleParticleType VIOLET = FabricParticleTypes.simple();
    public static final SimpleParticleType PINK = FabricParticleTypes.simple();
    public static final SimpleParticleType CYAN = FabricParticleTypes.simple();

    private ModParticles() {
    }

    public static void register() {
        register("violet", VIOLET);
        register("pink", PINK);
        register("cyan", CYAN);
    }

    private static void register(String name, SimpleParticleType type) {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, ArcaneSpells.id(name), type);
    }
}
