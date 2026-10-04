package dev.arcane.client.fx;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModParticles;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Particle choreography for each spell phase. Everything is computed locally from one effect message. */
public final class SpellParticles {
    private static final ParticleOptions VIOLET = ModParticles.VIOLET;
    private static final ParticleOptions PINK = ModParticles.PINK;
    private static final ParticleOptions CYAN = ModParticles.CYAN;
    /** Golden angle in radians: successive points never line up, giving even spreads without randomness. */
    private static final double GOLDEN_ANGLE = 2.3999632297;
    private static final int MAX_SHOCKWAVES = 12;
    private static final List<Shockwave> WAVES = new ArrayList<>();

    private SpellParticles() {
    }

    public static void play(SpellEffectPayload effect) {
        switch (effect.kind()) {
            case CHARGE -> charge(effect);
            case BOLT -> bolt(effect);
            case IMPACT -> impact(effect);
        }
    }

    public static void tick() {
        WAVES.removeIf(Shockwave::tick);
    }

    public static void clear() {
        WAVES.clear();
    }

    private static void charge(SpellEffectPayload effect) {
        Vec3 n = effect.direction().normalize(), u = perpendicular(n), v = n.cross(u);
        double rotation = effect.age() * 0.10;
        double radius = 0.35 + effect.power() * 0.30;
        ring(effect.position(), u, v, radius, rotation, 24, VIOLET);
        ring(effect.position(), u, v, radius * 0.72, -rotation, 16, PINK);
        // Six rotating rune spokes bridge the concentric rings.
        for (int i = 0; i < 6; i++) {
            double angle = rotation + i * Math.PI / 3;
            Vec3 radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
            for (int j = 0; j < 3; j++) {
                particle(CYAN, effect.position().add(radial.scale(radius * (0.77 + j * 0.13))), Vec3.ZERO);
            }
            Vec3 at = effect.position().add(radial.scale(radius * 1.25));
            particle(ParticleTypes.END_ROD, at, effect.position().subtract(at).scale(0.035));
        }
        if (effect.power() >= 1 && effect.age() % 8 == 0) particle(ParticleTypes.END_ROD, effect.position(), n.scale(0.01));
    }

    private static void bolt(SpellEffectPayload effect) {
        Vec3 n = effect.direction().normalize(), u = perpendicular(n), v = n.cross(u);
        double radius = 0.20 + effect.power() * 0.18;
        // A cyan/violet double helix around a pink core, sampled along this tick's travel.
        for (int i = 0; i < 14; i++) {
            double t = i / 14.0;
            Vec3 center = effect.position().add(effect.direction().scale(t));
            double angle = (effect.age() + t) * 0.95;
            Vec3 spiral = u.scale(Math.cos(angle) * radius).add(v.scale(Math.sin(angle) * radius));
            particle(VIOLET, center.add(spiral), Vec3.ZERO);
            particle(CYAN, center.subtract(spiral), Vec3.ZERO);
            particle(PINK, center, Vec3.ZERO);
            if (i % 4 == 0) particle(ParticleTypes.ELECTRIC_SPARK, center.add(spiral.scale(2)), spiral.scale(0.1));
            if (i % 2 == 0) particle(ParticleTypes.END_ROD, center, n.scale(-0.015));
        }
        particle(ParticleTypes.ELECTRIC_SPARK, effect.position().add(effect.direction()), Vec3.ZERO);
    }

    private static void impact(SpellEffectPayload effect) {
        if (WAVES.size() >= MAX_SHOCKWAVES) WAVES.removeFirst();
        WAVES.add(new Shockwave(effect.position(), effect.direction(), effect.power()));
        // Fibonacci sphere: an even burst in every direction.
        for (int i = 0; i < 150; i++) {
            double y = 1 - 2 * (i + 0.5) / 150;
            double radius = Math.sqrt(1 - y * y);
            double angle = i * GOLDEN_ANGLE;
            Vec3 direction = new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
            particle(i % 3 == 0 ? ParticleTypes.END_ROD : VIOLET, effect.position(), direction.scale(0.3 + effect.power() * 0.65));
        }
        particle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE4BCFF), effect.position(), Vec3.ZERO);
        particle(ParticleTypes.EXPLOSION_EMITTER, effect.position(), Vec3.ZERO);
        for (int i = 0; i < 32; i++) {
            double a = i * GOLDEN_ANGLE;
            Vec3 velocity = new Vec3(Math.cos(a) * 0.25, 0.05 + (i % 5) * 0.035, Math.sin(a) * 0.25);
            particle(ParticleTypes.LARGE_SMOKE, effect.position().add(velocity.scale(3)), velocity);
            particle(ParticleTypes.FIREWORK, effect.position(), velocity.scale(3));
        }
    }

    private static Vec3 perpendicular(Vec3 forward) {
        return forward.cross(Math.abs(forward.y) > 0.9 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0)).normalize();
    }

    private static void particle(ParticleOptions type, Vec3 at, Vec3 velocity) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.addParticle(type, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    /** Evenly spaced particles on a circle in the plane spanned by unit vectors {@code u} and {@code v}. */
    private static void ring(Vec3 center, Vec3 u, Vec3 v, double radius, double rotation, int count, ParticleOptions type) {
        for (int i = 0; i < count; i++) {
            double a = rotation + Math.PI * 2 * i / count;
            particle(type, center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius)), Vec3.ZERO);
        }
    }

    /** Multi-tick impact animation: a fast primary blast, a slower echo ring, and rising embers. */
    private static final class Shockwave {
        private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
        final Vec3 center, u, v;
        final float power;
        int age;

        Shockwave(Vec3 center, Vec3 direction, float power) {
            this.center = center;
            this.power = power;
            Vec3 n = direction.normalize();
            u = perpendicular(n);
            v = n.cross(u);
        }

        /** Returns true when the animation has finished. */
        boolean tick() {
            ++age;
            if (age <= 22 && age % 2 == 0) {
                double t = age / 22.0;
                double radius = (4 + power * 6) * (1 - Math.pow(1 - t, 3)); // ease-out cubic
                ring(center, u, v, radius, age * 0.05, 72, age < 10 ? PINK : VIOLET);
                ring(center, X, Z, radius, -age * 0.04, 72, CYAN);
                ring(center, X, Y, radius * 0.8, age * 0.06, 48, VIOLET);
            }
            if (age >= 8 && age <= 32 && age % 3 == 0) {
                double radius = (age - 7) / 25.0 * (3 + power * 5);
                ring(center, u, v, radius, -age * 0.08, 48, CYAN);
            }
            if (age < 30 && age % 3 == 0) {
                for (int i = 0; i < 10; i++) {
                    double a = i * GOLDEN_ANGLE + age;
                    double r = (1 + power * 3) * (1 - age / 35.0);
                    Vec3 at = center.add(Math.cos(a) * r, age * 0.035, Math.sin(a) * r);
                    particle(VIOLET, at, new Vec3(0, 0.12, 0));
                    if (age < 15) particle(ParticleTypes.ELECTRIC_SPARK, at, new Vec3(Math.cos(a) * 0.2, 0.1, Math.sin(a) * 0.2));
                }
            }
            return age >= 36;
        }
    }
}
