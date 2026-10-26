package dev.arcane.client.fx;

import static dev.arcane.client.fx.Fx.UP;
import static dev.arcane.client.fx.Fx.anyDirection;
import static dev.arcane.client.fx.Fx.camera;
import static dev.arcane.client.fx.Fx.ease;
import static dev.arcane.client.fx.Fx.hide;
import static dev.arcane.client.fx.Fx.light;
import static dev.arcane.client.fx.Fx.particle;
import static dev.arcane.client.fx.Fx.particlesVisible;
import static dev.arcane.client.fx.Fx.proximity;
import static dev.arcane.client.fx.Fx.scaled;
import static dev.arcane.client.fx.Fx.show;
import static dev.arcane.client.fx.Fx.unlight;
import static dev.arcane.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.effect.BillboardSprite;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.screen.ScreenEffects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Astral Gate as the client sees it: at each end an upright ring facing the other end, two hexagons turning against each
 * other inside it, a vortex inside that, always seeming to fall away from whoever looks into it, a seal on the ground,
 * a shaft of light from high above, and light pouring in. Whatever crosses leaves an arc of light along the way, flares
 * both gates and bursts out at the far one. A gate whose far end is in another world is drawn alone, facing the way it
 * was cast.
 */
final class GateFx {
    private static final Fx.Tracker<Gate> GATES = new Fx.Tracker<>();
    private static final ColorRgba ROSE = ColorRgba.rgba(0xFFB8F0FF);
    private static final ColorRgba GOLD = ColorRgba.rgba(0xFFD27AFF);
    /** The gate rings' centres, above the ground. */
    private static final double RING_HEIGHT = 1.3;
    private static final int CLOSE_TICKS = 10;
    /** Rings of the vortex, each further into the gate. */
    private static final int VORTEX_RINGS = 5;
    /** How far the shafts of light reach up from each gate. */
    private static final float SHAFT_HEIGHT = 28;
    /** How long both gates flare after something crosses. */
    private static final int FLARE_TICKS = 10;
    /** The length of the gate's hum, played again as each one ends. */
    private static final int HUM_TICKS = 40;

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
        /** Its other end is in another world. */
        final boolean single;
        final int duration;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle originLight, destinationLight;
        /** Each gate's shaft of light and the wider glow round it: the origin's pair, then the destination's. */
        final List<EffectHandle<BillboardSprite>> shafts = new ArrayList<>(Collections.nCopies(4, null));
        int age;
        int flare;

        Gate(SpellEffect effect) {
            this.origin = effect.position();
            this.single = effect.power() < 0.5f;
            this.destination = single ? origin : origin.add(effect.direction());
            Vec3 across = new Vec3(effect.direction().x, 0, effect.direction().z);
            this.facing = across.lengthSqr() < 1.0e-6 ? new Vec3(0, 0, 1) : across.normalize();
            this.duration = effect.age() > 0 ? effect.age() : SpellTuning.GATE_OPEN_TICKS;
        }

        @Override public boolean tick() {
            age++;
            if (flare > 0) flare--;
            SpellLighting.guarded(this::animate);
            for (Vec3 end : single ? new Vec3[] {origin} : new Vec3[] {origin, destination}) {
                if (age < duration && particlesVisible(end)) inflow(end);
                if (age % HUM_TICKS == 1 && age < duration - CLOSE_TICKS) {
                    world().playLocalSound(end.x, end.y + RING_HEIGHT, end.z, ModSounds.GATE_HUM, SoundSource.PLAYERS, 0.7f, 1.0f, false);
                }
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
            if (!single) portal(destination, facing.reverse(), open);
            planes.end();
            shafts(0, origin, open);
            shafts(1, single ? null : destination, open);
            float bright = 1.4f * open + 3 * flare / (float) FLARE_TICKS;
            originLight = bright > 0.01f ? light(originLight, origin.add(0, RING_HEIGHT, 0), 9, bright, ROSE) : unlight(originLight);
            destinationLight = bright > 0.01f && !single
                    ? light(destinationLight, destination.add(0, RING_HEIGHT, 0), 9, bright, ROSE) : unlight(destinationLight);
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
            // The vortex: rings shrinking and dimming as they go deeper, arms turning faster the deeper they are, and the
            // light at the far end. It recedes from the side the camera is on, so it falls away from either side.
            Vec3 into = camera().subtract(center).dot(normal) > 0 ? normal.reverse() : normal;
            for (int k = 1; k <= VORTEX_RINGS; k++) {
                Vec3 at = center.add(into.scale(k * 0.32));
                double r = radius * 0.72 * (1 - k * 0.13);
                float dim = 1 - k / (VORTEX_RINGS + 1f);
                planes.ring(at, normal, r, 0.05f, scaled(ROSE, 0.6f * dim));
                float spin = age * (0.06f + 0.035f * k) + k * 0.7f;
                for (int arm = 0; arm < 2; arm++) {
                    float from = spin + arm * Mth.PI;
                    planes.arc(at, normal, r * 0.9, 0.09f, from, from + 1.3f, scaled(GOLD, 0.7f * dim));
                }
            }
            planes.glow(center.add(into.scale((VORTEX_RINGS + 1) * 0.32)), (float) radius * 0.3f, scaled(ColorRgba.WHITE, 0.6f));
            Vec3 ground = feet.add(0, 0.06, 0);
            planes.ring(ground, UP, radius + 0.5, 0.1f, scaled(ROSE, 0.6f));
            planes.polygon(ground, UP, radius + 0.2, 8, age * 0.03f, 0.06f, scaled(GOLD, 0.5f));
        }

        /** The gate at {@code feet}'s shaft of light, falling on it from high above, or none for a null gate. */
        private void shafts(int gate, Vec3 feet, float open) {
            for (int i = 0; i < 2; i++) {
                int slot = gate * 2 + i;
                if (feet == null || open <= 0.01f) {
                    shafts.set(slot, hide(shafts.get(slot)));
                    continue;
                }
                Vec3 middle = feet.add(0, SHAFT_HEIGHT / 2 - 0.5, 0);
                BillboardSprite shaft = i == 0
                        ? JudgmentFx.beamSprite(middle, 1.1f * open, SHAFT_HEIGHT, scaled(ROSE, 0.45f * open))
                        : JudgmentFx.beamSprite(middle, 3.4f * open, SHAFT_HEIGHT, scaled(GOLD, 0.16f * open));
                shafts.set(slot, show(shafts.get(slot), shaft));
            }
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
            flare = FLARE_TICKS;
            // The way it went, drawn in light: rose sparks close-set along the arc, white motes that linger after them.
            double length = from.distanceTo(to);
            int steps = (int) Math.min(240, length / 0.5);
            for (int i = 0; i <= steps; i++) {
                Vec3 at = from.lerp(to, i / (double) Math.max(1, steps)).add(0, 1 + 2 * Math.sin(Math.PI * i / Math.max(1, steps)), 0);
                if (!particlesVisible(at)) continue;
                particle(ModParticles.PINK, at, Vec3.ZERO);
                if (i % 3 == 0) particle(ParticleTypes.END_ROD, at, Vec3.ZERO);
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
            shafts.forEach(Fx::hide);
            if (originLight != null) originLight.close();
            if (destinationLight != null) destinationLight.close();
        }
    }
}
