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
import static dev.arcane.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Singularity as the client sees it. Nebulon draws light, not darkness, so at first the hole is a knot of ink and
 * smoke; around it, a bright photon ring always faces the viewer, and three tilted accretion rings precess at
 * different speeds. Purple streams pour in from all sides, and its reach is marked on the ground.
 *
 * <p>For its last {@link #FORM_BEFORE} ticks it becomes a true black hole, drawn by {@link SpaceTimeShader}: black,
 * the world behind it bent round it, a white-hot disc whirling about it, and the rings and ink fade away. Without
 * that pass it stays as it was. Then it collapses: it is crushed to nothing, and a flash and a shockwave burst out.
 */
final class SingularityFx {
    private static final Fx.Tracker<Hole> HOLES = new Fx.Tracker<>();
    private static final ColorRgba VIOLET = ColorRgba.rgba(0xA05CFFFF);
    private static final ColorRgba MAGENTA = ColorRgba.rgba(0xE45CFFFF);
    private static final ColorRgba PALE = ColorRgba.rgba(0xEBD8FFFF);
    /** The black hole's disc, lighting the ground. */
    private static final ColorRgba DISC_LIGHT = ColorRgba.rgba(0xFFC48AFF);
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFD9B8FF);
    private static final int TINT = 0x0E0418;
    /** The heart's radius: the unit its rings are sized in. */
    private static final float HEART = (float) SpellTuning.SINGULARITY_CRUSH_RADIUS;
    /** The rings crush inwards over this long before the blast. */
    private static final int IMPLODE_TICKS = 5;
    private static final int COLLAPSE_TICKS = 40;
    /** The black hole's event horizon, in blocks from its centre; its shadow looks some 2.6 times as wide. */
    private static final float HORIZON = 1.15f;
    /** Its disc reaches as far as the singularity eats the ground. */
    private static final float DISC = (float) SpellTuning.SINGULARITY_CONSUME_RADIUS;
    /** It becomes a true black hole this many ticks before it collapses, taking {@link #FORM_TICKS} to form. */
    private static final int FORM_BEFORE = 80;
    private static final int FORM_TICKS = 20;
    /** How fast its disc's inner edge turns, in radians a second; the rest turns slower, as orbits do. */
    private static final float DISC_SPIN = 1.7f;

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
        /** The way its disc turns round: nearly upright, tipped a little to one side. */
        final Vec3 axis;
        /** Varies the disc's streaks from one black hole to the next. */
        final float seed;
        PointLightHandle glow;
        int age;
        int collapseAge = -1;
        /** Whether the black hole is being drawn by the screen pass, rather than not at all. */
        boolean drawn;

        Hole(SpellEffect effect) {
            this.center = effect.position();
            this.reach = effect.power() > 0 ? effect.power() : SpellTuning.SINGULARITY_PULL_RADIUS;
            this.duration = effect.age() > 0 ? effect.age() : SpellTuning.SINGULARITY_DURATION_TICKS;
            // Every client sees the same disc.
            RandomSource shape = RandomSource.create(effect.sourceId() * 31L + Double.hashCode(center.x) + Double.hashCode(center.z));
            double tip = 0.12 + 0.12 * shape.nextDouble(), toward = shape.nextDouble() * Mth.TWO_PI;
            this.axis = new Vec3(Math.sin(tip) * Math.cos(toward), Math.cos(tip), Math.sin(tip) * Math.sin(toward));
            this.seed = shape.nextFloat() * 100;
        }

        void collapse() {
            if (collapseAge < 0) collapseAge = 0;
        }

        @Override public boolean tick() {
            age++;
            if (collapseAge >= 0) collapseAge++;
            if (collapseAge < 0 && age == duration - FORM_BEFORE) form();
            if (collapseAge == IMPLODE_TICKS) burst();
            drawn = SpaceTimeShader.available();
            if (drawn && age >= duration - FORM_BEFORE && collapseAge <= IMPLODE_TICKS) SpaceTimeShader.show(this::write);
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

        /** How far it has become a true black hole, 0 to 1: forming before the collapse, then crushed with the rest. */
        private float formed(float partialTick) {
            float formed = ease((age + partialTick - (duration - FORM_BEFORE)) / FORM_TICKS);
            if (collapseAge >= 0) formed *= Math.max(0, 1 - (collapseAge + partialTick) / IMPLODE_TICKS);
            return formed;
        }

        /** How much of it the screen pass draws: the rest is drawn with rings and particles. */
        private float shown() {
            return drawn ? formed(0) : 0;
        }

        /** It becomes a true black hole: from here on, light bends round it. */
        private void form() {
            world().playLocalSound(center.x, center.y, center.z, ModSounds.SINGULARITY_FORM, SoundSource.PLAYERS, 4.0f, 1.0f, false);
            ScreenEffects.shake(0.35f * proximity(center));
        }

        private void write(SpaceTimeShader.View view, float partialTick) {
            float formed = formed(partialTick);
            if (formed <= 0) return;
            float seconds = (age + partialTick) / 20;
            view.hole(center, HORIZON * formed, axis, DISC * (0.5f + 0.5f * formed), formed, seconds * DISC_SPIN, seed);
        }

        private void animate() {
            float size = size();
            float pulse = 0.85f + 0.15f * Mth.sin(age * 0.5f);
            // As the black hole forms, the rings and the halo give way to it.
            float rings = 1 - shown();
            planes.begin();
            if (size > 0 && rings > 0) {
                planes.halo(center, HEART * 0.55 * size, 0.25f, scaled(PALE, pulse * rings));
                ColorRgba[] colors = {VIOLET, MAGENTA, PALE};
                for (int i = 0; i < 3; i++) {
                    // Each ring leans 20-35 degrees and precesses at its own speed.
                    double tilt = 0.35 + 0.12 * i, phase = age * (0.05 + 0.03 * i) + i * 2.1;
                    Vec3 normal = new Vec3(Math.sin(tilt) * Math.cos(phase), Math.cos(tilt), Math.sin(tilt) * Math.sin(phase));
                    planes.ring(center, normal, HEART * (0.85 + 0.4 * i) * size, 0.4f - 0.07f * i, scaled(colors[i], 0.8f * pulse * rings));
                }
            }
            if (size > 0) {
                // Its reach, on the ground beneath, with arcs spiralling inwards.
                Vec3 ground = center.subtract(0, SpellTuning.SINGULARITY_LIFT - 0.08, 0);
                planes.ring(ground, UP, reach, 0.25f, scaled(VIOLET, 0.35f * size));
                for (int i = 0; i < 3; i++) {
                    float from = -age * 0.08f + i * Mth.TWO_PI / 3;
                    double radius = reach * (1 - ((age * 0.02 + i / 3.0) % 1));
                    planes.arc(ground, UP, radius, 0.35f, from, from + 1.2f, scaled(MAGENTA, 0.5f * size));
                }
            }
            if (collapseAge >= IMPLODE_TICKS) {
                int burst = collapseAge - IMPLODE_TICKS;
                if (burst <= 16) planes.glow(center, HEART * (0.8f + 5 * ease(burst / 4f)), scaled(PALE, 1 - burst / 16f));
                if (burst <= 14) {
                    Vec3 ground = center.subtract(0, SpellTuning.SINGULARITY_LIFT - 0.15, 0);
                    double radius = 1 + SpellTuning.SINGULARITY_COLLAPSE_RADIUS * 1.3 * ease(burst / 12f);
                    planes.ring(ground, UP, radius, 2f * (1 - burst / 14f) + 0.1f, scaled(MAGENTA, 1 - burst / 14f));
                }
            }
            planes.end();
            // The black hole's disc lights the ground below it warm and bright.
            float shown = shown();
            float bright = collapseAge >= IMPLODE_TICKS ? 6 * Math.max(0, 1 - (collapseAge - IMPLODE_TICKS) / 25f)
                    : 1.6f * size * pulse * (1 + 1.2f * shown);
            glow = bright > 0.01f ? light(glow, center, HEART * 6, bright, shown > 0.5f ? DISC_LIGHT : VIOLET) : unlight(glow);
        }

        private void particles() {
            if (collapseAge >= 0) return;
            float size = size();
            // The dark heart: ink and smoke, held close, until the black hole's own darkness takes its place.
            float ink = 1 - shown();
            for (int i = 0, n = Fx.scaled(Math.round(12 * ink)); i < n; i++) {
                particle(ParticleTypes.SQUID_INK, center.add(anyDirection(random).scale(HEART * 0.35 * size * random.nextDouble())), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(Math.round(4 * ink)); i < n; i++) {
                particle(ParticleTypes.LARGE_SMOKE, center.add(anyDirection(random).scale(HEART * 0.5 * size)), Vec3.ZERO);
            }
            // Portal particles fly from where they're aimed back to where they spawned: these all pour into the heart.
            // Particles are drawn over the black hole, so once it forms only a few still stream in across its darkness.
            for (int i = 0, n = Fx.scaled(Math.round(24 * (0.25f + 0.75f * ink))); i < n; i++) {
                particle(ParticleTypes.PORTAL, center, anyDirection(random).scale(4 + random.nextDouble() * 10));
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
            for (int i = 0; i < 4; i++) particle(ParticleTypes.EXPLOSION_EMITTER, center.add(anyDirection(random).scale(HEART)), Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(240); i < n; i++) {
                particle(ParticleTypes.REVERSE_PORTAL, center, anyDirection(random).scale(0.8 + random.nextDouble() * 1.4));
            }
            for (int i = 0, n = Fx.scaled(120); i < n; i++) {
                particle(ParticleTypes.END_ROD, center, anyDirection(random).scale(0.6 + random.nextDouble() * 1.0));
            }
        }

        private void screen(float near) {
            if (near <= 0) return;
            if (collapseAge < 0) {
                float formed = formed(0);
                ScreenEffects.tint((0.35f + 0.2f * formed) * near * size(), TINT);
                ScreenEffects.shake((0.12f + 0.08f * formed) * near);
                // A true black hole draws the eye.
                if (formed > 0) ScreenEffects.pull(center, 0.3f * formed * near);
            }
        }

        @Override public void release() {
            planes.clear();
            if (glow != null) glow.close();
        }
    }
}
