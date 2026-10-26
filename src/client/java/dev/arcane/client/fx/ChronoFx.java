package dev.arcane.client.fx;

import static dev.leyline.client.fx.Fx.UP;
import static dev.leyline.client.fx.Fx.camera;
import static dev.leyline.client.fx.Fx.ease;
import static dev.leyline.client.fx.Fx.light;
import static dev.leyline.client.fx.Fx.onCircle;
import static dev.leyline.client.fx.Fx.particle;
import static dev.leyline.client.fx.Fx.particlesVisible;
import static dev.leyline.client.fx.Fx.proximity;
import static dev.leyline.client.fx.Fx.scaled;
import static dev.leyline.client.fx.Fx.unlight;
import static dev.leyline.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.effect.EffectRenderers;
import dev.leyline.client.fx.Fx;
import dev.leyline.client.fx.NebulonGuard;
import dev.leyline.client.screen.ScreenEffects;
import dev.leyline.effect.Effect;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chrono Field as the client sees it: a wireframe dome of teal rings, a clock face on the ground with its hands stopped
 * at ten past ten while its second hand ticks round, once, for as long as the field lasts, motes of light hanging
 * motionless in the air and on every creature caught, and everything inside drained of its colour
 * ({@link SpaceTimeShader}, which draws the hands too, so they keep their colour and read from afar; without that
 * pass, the view is washed teal from inside instead, and the hands are traced in sparks). When time resumes, the
 * second hand spins back, colour floods back out from the centre behind a bright front, and the dome ripples outwards
 * and fades.
 */
final class ChronoFx {
    private static final Fx.Tracker<Field> FIELDS = AstralFx.TRACKERS.create();
    private static final ColorRgba TEAL = ColorRgba.rgba(0x7FFFE6FF);
    private static final ColorRgba PALE = ColorRgba.rgba(0xDFFFF8FF);
    private static final int TINT = 0x08201C;
    /** Rings of latitude, in degrees above the ground. */
    private static final float[] LATITUDES = {0, 25, 50, 72};
    private static final int MERIDIANS = 3;
    private static final int END_TICKS = 14;
    /** The second hand steps once a second. */
    private static final int TICK = 20;
    /** Turns the second hand spins back as time resumes. */
    private static final float REWIND_TURNS = 2;

    private ChronoFx() {
    }

    static void register() {
        EffectRenderers.register(ModSpellEffects.CHRONO_FIELD,
                (effect, level) -> FIELDS.get(effect.sourceId(), level, id -> new Field(effect)));
        EffectRenderers.register(ModSpellEffects.CHRONO_END,
                (effect, level) -> FIELDS.get(effect.sourceId(), level, id -> new Field(effect)).end());
    }

    private static final class Field implements Fx.Animation {
        final Vec3 center;
        final double radius;
        final int duration;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle glow;
        int age;
        int endAge = -1;

        Field(Effect effect) {
            this.center = effect.position();
            this.radius = effect.power() > 0 ? effect.power() : SpellTuning.CHRONO_RADIUS;
            this.duration = effect.age() > 0 ? effect.age() : SpellTuning.CHRONO_DURATION_TICKS;
            float near = proximity(center);
            ScreenEffects.flash(0.25f * near);
        }

        void end() {
            if (endAge >= 0) return;
            endAge = 0;
            ScreenEffects.flash(0.2f * proximity(center));
        }

        @Override public boolean tick() {
            age++;
            if (endAge >= 0) endAge++;
            boolean drained = SpaceTimeShader.available();
            NebulonGuard.guarded(this::animate);
            if (endAge < 0 && particlesVisible(center)) particles(drained);
            // Tick, tock: once a second, as the second hand steps.
            if (endAge < 0 && age % TICK == 0 && age < duration) {
                world().playLocalSound(center.x, center.y, center.z, ModSounds.CHRONO_TICK, SoundSource.PLAYERS, 2.0f, age / TICK % 2 == 0 ? 1.0f : 0.84f, false);
            }
            // The second hand in sparks: redrawn every other tick while it steps, every tick as it spins back.
            if (!drained && (endAge >= 0 ? endAge <= END_TICKS : age % 2 == 0) && particlesVisible(center)) {
                for (double d = radius * 0.22; d <= radius * 0.88; d += 0.4) particle(ModParticles.PINK, handPoint(d, secondAngle(0)), Vec3.ZERO);
            }
            if (drained && endAge <= END_TICKS) SpaceTimeShader.show(this::write);
            boolean inside = camera().distanceTo(center) < radius;
            if (inside && endAge < 0) ScreenEffects.tint((drained ? 0.12f : 0.35f) * ease(age / 8f), TINT);
            return endAge > END_TICKS || endAge < 0 && age > duration + 30;
        }

        private void write(SpaceTimeShader.View view, float partialTick) {
            float drain = ease((age + partialTick) / 10f);
            // The front runs out past the dome's edge, so colour is back everywhere before the pass lets go.
            float front = endAge >= 0 ? 0.01f + (float) radius * 1.3f * ease((endAge + partialTick) / (END_TICKS - 2f)) : 0;
            // The hands appear as the colour drains, and fade as the second hand spins back.
            float hands = endAge >= 0 ? Math.max(0, 1 - (endAge + partialTick) / END_TICKS) : drain;
            view.field(center, (float) radius, drain, front, (float) secondAngle(partialTick), hands);
        }

        private void animate() {
            float grow = ease(age / 10f);
            float fade = endAge >= 0 ? 1 - endAge / (float) END_TICKS : 1;
            // The dome swells a little as time resumes.
            double r = radius * grow * (endAge >= 0 ? 1 + 0.25 * ease(endAge / (float) END_TICKS) : 1);
            planes.begin();
            if (fade > 0) {
                for (float latitude : LATITUDES) {
                    double angle = Math.toRadians(latitude);
                    planes.ring(center.add(0, r * Math.sin(angle), 0), UP, r * Math.cos(angle), 0.15f, scaled(TEAL, 0.45f * fade));
                }
                for (int i = 0; i < MERIDIANS; i++) {
                    double angle = i * Math.PI / MERIDIANS;
                    planes.ring(center, new Vec3(Math.cos(angle), 0, Math.sin(angle)), r, 0.1f, scaled(TEAL, 0.3f * fade));
                }
                // The clock face: rim, twelve-sided hour ring, centre. The rim pulses with every tick.
                Vec3 ground = center.add(0, 0.06, 0);
                float pulse = endAge < 0 ? 1 + 0.7f * Math.max(0, 1 - age % TICK / 4f) : 1;
                planes.ring(ground, UP, r * 0.95, 0.6f, scaled(TEAL, Math.min(1, fade * pulse)));
                planes.polygon(ground, UP, r * 0.85, 12, 0, 0.25f, scaled(PALE, 0.6f * fade));
                planes.ring(ground, UP, r * 0.2, 0.3f, scaled(PALE, 0.8f * fade));
                if (endAge >= 0) planes.ring(ground, UP, r * (1 + 0.6 * ease(endAge / (float) END_TICKS)), 1.2f * fade, scaled(PALE, fade));
            }
            planes.end();
            glow = fade > 0 ? light(glow, center.add(0, 2, 0), (float) radius, 1.1f * fade, TEAL) : unlight(glow);
        }

        /**
         * The second hand, clockwise from twelve, {@code partialTick} of the way to the next tick: a step each second,
         * round once over the field's whole life, then spinning back from where it stopped as time resumes.
         */
        private double secondAngle(float partialTick) {
            int ticks = Math.max(TICK, duration / TICK * TICK);
            int stopped = endAge >= 0 ? age - endAge : age;
            double swept = Mth.TWO_PI * Math.min(1, stopped / TICK * TICK / (double) ticks);
            return endAge < 0 ? swept : swept - REWIND_TURNS * Mth.TWO_PI * ease(Math.min(END_TICKS, endAge + partialTick) / END_TICKS);
        }

        /** The point on the clock face {@code distance} from its centre, {@code angle} clockwise from twelve (north). */
        private Vec3 handPoint(double distance, double angle) {
            return onCircle(center, distance, angle - Math.PI / 2, 0.15);
        }

        private void particles(boolean drained) {
            // Motes hang where they appear: nothing moves in here.
            if (age % 3 == 0) {
                for (int i = 0, n = Fx.scaled(12); i < n; i++) {
                    Vec3 at = center.add(Fx.anyDirection(random).scale(Math.cbrt(random.nextDouble()) * radius));
                    if (at.y > center.y) particle(ParticleTypes.END_ROD, at, Vec3.ZERO);
                }
            }
            // The clock's hands, stopped at ten past ten, in sparks (spacetime.fsh draws the same hands).
            if (!drained && age % 6 == 0) {
                hand(Math.toRadians(-60), radius * 0.45);
                hand(Math.toRadians(60), radius * 0.75);
            }
            // Every creature caught glitters; players aren't marked, as the caster walks free.
            if (age % 5 == 0) {
                for (LivingEntity entity : world().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                        entity -> !(entity instanceof Player) && entity.getBoundingBox().getCenter().distanceTo(center) <= radius)) {
                    for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                        Vec3 at = entity.getBoundingBox().getCenter().add(Fx.anyDirection(random).scale(entity.getBbWidth()));
                        particle(ModParticles.CYAN, at, Vec3.ZERO);
                    }
                }
            }
        }

        /** A hand of the clock: a line of light from the centre, {@code angle} clockwise from twelve (north). */
        private void hand(double angle, double length) {
            for (double d = radius * 0.22; d <= length; d += 0.5) particle(ModParticles.CYAN, handPoint(d, angle), Vec3.ZERO);
        }

        @Override public void release() {
            planes.clear();
            if (glow != null) glow.close();
        }
    }
}
