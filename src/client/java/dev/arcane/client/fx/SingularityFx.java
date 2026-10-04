package dev.arcane.client.fx;

import static dev.arcane.client.fx.Fx.UP;
import static dev.arcane.client.fx.Fx.anyDirection;
import static dev.arcane.client.fx.Fx.ease;
import static dev.arcane.client.fx.Fx.light;
import static dev.arcane.client.fx.Fx.onCircle;
import static dev.arcane.client.fx.Fx.particle;
import static dev.arcane.client.fx.Fx.particlesVisible;
import static dev.arcane.client.fx.Fx.proximity;
import static dev.arcane.client.fx.Fx.scaled;
import static dev.arcane.client.fx.Fx.unlight;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Singularity as the client sees it. Nebulon draws light, not darkness, so the hole itself is a knot of ink and smoke;
 * around it, a bright photon ring always faces the viewer, and three tilted accretion rings precess at different
 * speeds. Purple streams pour in from all sides, and its reach is marked on the ground. Then it collapses: the rings
 * crush inwards, and a flash and a shockwave burst out.
 */
final class SingularityFx {
    private static final Fx.Tracker<Hole> HOLES = new Fx.Tracker<>();
    private static final ColorRgba VIOLET = ColorRgba.rgba(0xA05CFFFF);
    private static final ColorRgba MAGENTA = ColorRgba.rgba(0xE45CFFFF);
    private static final ColorRgba PALE = ColorRgba.rgba(0xEBD8FFFF);
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFD9B8FF);
    private static final int TINT = 0x0E0418;
    /** The rings crush inwards over this long before the blast. */
    private static final int IMPLODE_TICKS = 5;
    private static final int COLLAPSE_TICKS = 40;

    private SingularityFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.SINGULARITY,
                (effect, level) -> HOLES.get(effect.sourceId(), level, id -> new Hole(effect)));
        SpellEffectRenderers.register(ModSpellEffects.SINGULARITY_COLLAPSE,
                (effect, level) -> HOLES.get(effect.sourceId(), level, id -> new Hole(effect)).collapse());
    }

    private static final class Hole implements Fx.Animation {
        final Vec3 center;
        final double reach;
        final int duration;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle glow;
        int age;
        int collapseAge = -1;

        Hole(SpellEffect effect) {
            this.center = effect.position();
            this.reach = effect.power() > 0 ? effect.power() : SpellTuning.SINGULARITY_PULL_RADIUS;
            this.duration = effect.age() > 0 ? effect.age() : SpellTuning.SINGULARITY_DURATION_TICKS;
        }

        void collapse() {
            if (collapseAge < 0) collapseAge = 0;
        }

        @Override public boolean tick() {
            age++;
            if (collapseAge >= 0) collapseAge++;
            if (collapseAge == IMPLODE_TICKS) burst();
            SpellLighting.guarded(this::animate);
            if (particlesVisible(center)) particles();
            screen(proximity(center));
            return collapseAge > COLLAPSE_TICKS || collapseAge < 0 && age > duration + 40;
        }

        /** How open the hole is: growing in, then crushed to nothing as it implodes. */
        private float size() {
            if (collapseAge >= 0) return Math.max(0, 1 - collapseAge / (float) IMPLODE_TICKS);
            return ease(age / 15f);
        }

        private void animate() {
            float size = size();
            float pulse = 0.85f + 0.15f * Mth.sin(age * 0.5f);
            planes.begin();
            if (size > 0) {
                planes.halo(center, 1.3 * size, 0.14f, scaled(PALE, pulse));
                ColorRgba[] colors = {VIOLET, MAGENTA, PALE};
                for (int i = 0; i < 3; i++) {
                    // Each ring leans 20-35 degrees and precesses at its own speed.
                    double tilt = 0.35 + 0.12 * i, phase = age * (0.05 + 0.03 * i) + i * 2.1;
                    Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(phase), Math.cos(tilt), Math.sin(tilt) * Math.sin(phase));
                    planes.ring(center, normal, (2.0 + 0.9 * i) * size, 0.22f - 0.04f * i, scaled(colors[i], 0.8f * pulse));
                }
                // Its reach, on the ground beneath, with arcs spiralling inwards.
                Vec3 ground = center.subtract(0, SpellTuning.SINGULARITY_LIFT - 0.08, 0);
                planes.ring(ground, UP, reach, 0.12f, scaled(VIOLET, 0.35f * size));
                for (int i = 0; i < 3; i++) {
                    float from = -age * 0.08f + i * Mth.TWO_PI / 3;
                    double radius = reach * (1 - ((age * 0.02 + i / 3.0) % 1));
                    planes.arc(ground, UP, radius, 0.18f, from, from + 1.2f, scaled(MAGENTA, 0.5f * size));
                }
            }
            if (collapseAge >= IMPLODE_TICKS) {
                int burst = collapseAge - IMPLODE_TICKS;
                if (burst <= 14) planes.glow(center, 2 + 12 * ease(burst / 4f), scaled(PALE, 1 - burst / 14f));
                if (burst <= 12) {
                    Vec3 ground = center.subtract(0, SpellTuning.SINGULARITY_LIFT - 0.15, 0);
                    planes.ring(ground, UP, 1 + 16 * ease(burst / 10f), 0.8f * (1 - burst / 12f) + 0.05f, scaled(MAGENTA, 1 - burst / 12f));
                }
            }
            planes.end();
            float bright = collapseAge >= IMPLODE_TICKS ? 6 * Math.max(0, 1 - (collapseAge - IMPLODE_TICKS) / 25f) : 1.6f * size * pulse;
            glow = bright > 0.01f ? light(glow, center, 12, bright, VIOLET) : unlight(glow);
        }

        private void particles() {
            if (collapseAge >= 0) return;
            float size = size();
            // The dark heart: ink and smoke, held close.
            for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                particle(ParticleTypes.SQUID_INK, center.add(anyDirection(random).scale(0.6 * size * random.nextDouble())), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                particle(ParticleTypes.LARGE_SMOKE, center.add(anyDirection(random).scale(0.9 * size)), Vec3.ZERO);
            }
            // Portal particles fly from where they're aimed back to where they spawned: these all pour into the heart.
            for (int i = 0, n = Fx.scaled(14); i < n; i++) {
                particle(ParticleTypes.PORTAL, center, anyDirection(random).scale(3 + random.nextDouble() * 5));
            }
            // Dust swept across the ground towards it.
            if (age % 2 == 0) {
                double angle = random.nextDouble() * Mth.TWO_PI, radius = 4 + random.nextDouble() * (reach - 4);
                Vec3 at = onCircle(center, radius, angle, -SpellTuning.SINGULARITY_LIFT + 0.3);
                particle(ParticleTypes.CLOUD, at, center.subtract(at).normalize().scale(0.3));
            }
        }

        /** The blast as it collapses: flares, a ring of light flung outwards, fireballs. */
        private void burst() {
            float near = proximity(center);
            ScreenEffects.flash(0.5f * near);
            ScreenEffects.shake(0.7f * near);
            if (!particlesVisible(center)) return;
            for (int i = 0; i < 3; i++) particle(FLARE, center.add(anyDirection(random)), Vec3.ZERO);
            particle(ParticleTypes.EXPLOSION_EMITTER, center, Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(120); i < n; i++) {
                particle(ParticleTypes.REVERSE_PORTAL, center, anyDirection(random).scale(0.6 + random.nextDouble() * 0.8));
            }
            for (int i = 0, n = Fx.scaled(60); i < n; i++) {
                particle(ParticleTypes.END_ROD, center, anyDirection(random).scale(0.5 + random.nextDouble() * 0.6));
            }
        }

        private void screen(float near) {
            if (near <= 0) return;
            if (collapseAge < 0) {
                ScreenEffects.tint(0.35f * near * size(), TINT);
                ScreenEffects.shake(0.12f * near);
            }
        }

        @Override public void release() {
            planes.clear();
            if (glow != null) glow.close();
        }
    }
}
