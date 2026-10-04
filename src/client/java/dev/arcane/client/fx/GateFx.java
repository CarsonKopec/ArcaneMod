package dev.arcane.client.fx;

import static dev.arcane.client.fx.Fx.UP;
import static dev.arcane.client.fx.Fx.anyDirection;
import static dev.arcane.client.fx.Fx.ease;
import static dev.arcane.client.fx.Fx.light;
import static dev.arcane.client.fx.Fx.particle;
import static dev.arcane.client.fx.Fx.particlesVisible;
import static dev.arcane.client.fx.Fx.proximity;
import static dev.arcane.client.fx.Fx.scaled;
import static dev.arcane.client.fx.Fx.unlight;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Astral Gate as the client sees it: at each end an upright ring facing the other end, two hexagons turning against each
 * other inside it and a seal on the ground, light pouring in. Whatever crosses leaves a streak of light along the way
 * and bursts out at the far gate.
 */
final class GateFx {
    private static final Fx.Tracker<Gate> GATES = new Fx.Tracker<>();
    private static final ColorRgba ROSE = ColorRgba.rgba(0xFFB8F0FF);
    private static final ColorRgba GOLD = ColorRgba.rgba(0xFFD27AFF);
    /** The gate rings' centres, above the ground. */
    private static final double RING_HEIGHT = 1.3;
    private static final int CLOSE_TICKS = 10;

    private GateFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.ASTRAL_GATE,
                (effect, level) -> GATES.get(effect.sourceId(), level, id -> new Gate(effect)));
        SpellEffectRenderers.register(ModSpellEffects.GATE_CROSSING,
                (effect, level) -> GATES.get(effect.sourceId(), level, id -> new Gate(effect)).cross(effect));
    }

    private static final class Gate implements Fx.Animation {
        final Vec3 origin;
        final Vec3 destination;
        /** Horizontal, from the first gate towards the second: each gate faces the other. */
        final Vec3 facing;
        final int duration;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle originLight, destinationLight;
        int age;

        Gate(SpellEffect effect) {
            this.origin = effect.position();
            this.destination = origin.add(effect.direction());
            Vec3 across = new Vec3(effect.direction().x, 0, effect.direction().z);
            this.facing = across.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : across.normalize();
            this.duration = effect.age() > 0 ? effect.age() : SpellTuning.GATE_OPEN_TICKS;
        }

        @Override public boolean tick() {
            age++;
            SpellLighting.guarded(this::animate);
            for (Vec3 end : new Vec3[] {origin, destination}) {
                if (age < duration && particlesVisible(end)) inflow(end);
            }
            return age > duration;
        }

        /** Opens over a few ticks, closes over the last few. */
        private float openness() {
            return Math.min(ease(age / 8f), Mth.clamp((duration - age) / (float) CLOSE_TICKS, 0, 1));
        }

        private void animate() {
            float open = openness();
            planes.begin();
            portal(origin, facing, open);
            portal(destination, facing.reverse(), open);
            planes.end();
            float bright = 1.4f * open;
            originLight = bright > 0.01f ? light(originLight, origin.add(0, RING_HEIGHT, 0), 7, bright, ROSE) : unlight(originLight);
            destinationLight = bright > 0.01f ? light(destinationLight, destination.add(0, RING_HEIGHT, 0), 7, bright, ROSE) : unlight(destinationLight);
        }

        private void portal(Vec3 feet, Vec3 normal, float open) {
            if (open <= 0) return;
            double radius = SpellTuning.GATE_RADIUS * open;
            Vec3 center = feet.add(0, RING_HEIGHT, 0);
            float turn = age * 0.08f;
            planes.ring(center, normal, radius, 0.14f, ROSE);
            planes.ring(center, normal, radius * 0.82, 0.05f, scaled(ColorRgba.WHITE, 0.7f));
            planes.polygon(center, normal, radius * 0.7, 6, turn, 0.06f, scaled(GOLD, 0.9f));
            planes.polygon(center, normal, radius * 0.7, 6, -turn, 0.06f, scaled(GOLD, 0.6f));
            Vec3 ground = feet.add(0, 0.06, 0);
            planes.ring(ground, UP, radius + 0.5, 0.1f, scaled(ROSE, 0.6f));
            planes.polygon(ground, UP, radius + 0.2, 8, age * 0.03f, 0.06f, scaled(GOLD, 0.5f));
        }

        /** Light drawn into the gate's ring from all around it. */
        private void inflow(Vec3 feet) {
            Vec3 center = feet.add(0, RING_HEIGHT, 0);
            for (int i = 0, n = Fx.scaled(4); i < n; i++) {
                particle(ParticleTypes.PORTAL, center, anyDirection(random).scale(1.5 + random.nextDouble() * 1.5));
            }
            if (age % 2 == 0) particle(ParticleTypes.END_ROD, center.add(anyDirection(random).scale(SpellTuning.GATE_RADIUS)), Vec3.ZERO);
        }

        /** Something crossed: a streak along the way, a burst at each end, and a flash for whoever is near. */
        void cross(SpellEffect effect) {
            Vec3 from = effect.position(), to = from.add(effect.direction());
            float weight = Mth.clamp(effect.power(), 0, 1);
            ScreenEffects.flash(0.35f * weight * Math.max(proximity(from) * 0.3f, proximity(to)));
            if (particlesVisible(from)) burst(from.add(0, 1, 0), weight);
            if (particlesVisible(to)) burst(to.add(0, 1, 0), weight);
            double length = from.distanceTo(to);
            int steps = (int) Math.min(80, length / 1.5);
            for (int i = 0; i <= steps; i++) {
                Vec3 at = from.lerp(to, i / (double) Math.max(1, steps)).add(0, 1 + 2 * Math.sin(Math.PI * i / Math.max(1, steps)), 0);
                if (particlesVisible(at)) particle(i % 2 == 0 ? ModParticles.PINK : ParticleTypes.END_ROD, at, Vec3.ZERO);
            }
        }

        private void burst(Vec3 at, float weight) {
            for (int i = 0, n = Fx.scaled(Math.round(40 * weight)); i < n; i++) {
                particle(ParticleTypes.REVERSE_PORTAL, at, anyDirection(random).scale(0.2 + random.nextDouble() * 0.3));
            }
            for (int i = 0, n = Fx.scaled(Math.round(20 * weight)); i < n; i++) {
                particle(ModParticles.PINK, at, anyDirection(random).scale(0.15 + random.nextDouble() * 0.25));
            }
        }

        @Override public void release() {
            planes.clear();
            if (originLight != null) originLight.close();
            if (destinationLight != null) destinationLight.close();
        }
    }
}
