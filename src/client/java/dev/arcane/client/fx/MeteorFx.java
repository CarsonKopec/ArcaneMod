package dev.arcane.client.fx;

import static dev.arcane.client.fx.Fx.DOWN;
import static dev.arcane.client.fx.Fx.UP;
import static dev.arcane.client.fx.Fx.anyDirection;
import static dev.arcane.client.fx.Fx.circle;
import static dev.arcane.client.fx.Fx.ease;
import static dev.arcane.client.fx.Fx.light;
import static dev.arcane.client.fx.Fx.onCircle;
import static dev.arcane.client.fx.Fx.particle;
import static dev.arcane.client.fx.Fx.particlesVisible;
import static dev.arcane.client.fx.Fx.proximity;
import static dev.arcane.client.fx.Fx.scaled;
import static dev.arcane.client.fx.Fx.show;
import static dev.arcane.client.fx.Fx.unlight;
import static dev.arcane.client.fx.Fx.upwards;
import static dev.arcane.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModSpellEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor Swarm as the client sees it: a burning seal over the target area, its edge marked in red on the ground, and
 * each meteor a blazing ball trailing flame and smoke, flown here from its launch message until it strikes.
 */
final class MeteorFx {
    private static final Fx.Tracker<Swarm> SWARMS = new Fx.Tracker<>();
    private static final Fx.Tracker<Meteor> METEORS = new Fx.Tracker<>();
    private static final ColorRgba FIRE = ColorRgba.rgba(0xFF8A3DFF);
    private static final ColorRgba BLAZE = ColorRgba.rgba(0xFFE3A0FF);
    private static final ColorRgba CRIMSON = ColorRgba.rgba(0xFF3B1FFF);
    private static final int TINT = 0x200804;
    /** How long an impact keeps glowing. */
    private static final int IMPACT_TICKS = 24;

    private MeteorFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.METEOR_SWARM,
                (effect, level) -> SWARMS.get(effect.sourceId(), level, id -> new Swarm(id, effect)));
        SpellEffectRenderers.register(ModSpellEffects.METEOR,
                (effect, level) -> METEORS.get(effect.sourceId(), level, id -> new Meteor(effect)));
        SpellEffectRenderers.register(ModSpellEffects.METEOR_IMPACT,
                (effect, level) -> METEORS.get(effect.sourceId(), level, id -> new Meteor(effect)).impact(effect.position()));
    }

    /** The seal in the sky and the red ring on the ground, for as long as meteors fall. */
    private static final class Swarm implements Fx.Animation {
        final int id;
        final Vec3 mark;
        final Vec3 sky;
        final double area;
        final int duration;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        EffectHandle<MagicCircle> runes;
        PointLightHandle skyLight;
        int age;

        Swarm(int id, SpellEffect effect) {
            this.id = id;
            this.mark = effect.position();
            this.sky = mark.add(effect.direction());
            this.area = effect.power();
            this.duration = effect.age();
        }

        @Override public boolean tick() {
            age++;
            SpellLighting.guarded(this::animate);
            if (particlesVisible(sky) && age < duration) particles();
            float near = proximity(mark);
            if (near > 0 && age < duration) {
                ScreenEffects.tint(0.4f * near * ease(age / 20f), TINT);
                ScreenEffects.shake(0.08f * near);
            }
            return age > duration + 20;
        }

        private void animate() {
            float fade = age < duration ? ease(age / 15f) : 1 - (age - duration) / 20f;
            planes.begin();
            double outer = 18 * ease(age / 20f);
            runes = show(runes, circle(sky, DOWN, outer * 0.95, 0.12f, scaled(FIRE, 0.3f * fade), 4, id, 0.3f));
            planes.seal(sky, DOWN, outer, 0.45f, 8, age * 0.02f, scaled(FIRE, fade));
            planes.ring(sky.subtract(0, 0.3, 0), DOWN, outer * 0.45, 0.35f, scaled(BLAZE, fade));
            // The danger zone, pulsing on the ground.
            float pulse = 0.6f + 0.4f * Mth.sin(age * 0.4f);
            planes.ring(mark.add(0, 0.08, 0), UP, area, 0.25f, scaled(CRIMSON, 0.7f * pulse * fade));
            skyLight = fade > 0 ? light(skyLight, sky.subtract(0, 3, 0), 26, 2.2f * fade, FIRE) : unlight(skyLight);
            planes.end();
        }

        /** Embers and ash drifting down from the seal. */
        private void particles() {
            for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                Vec3 at = onCircle(sky, Math.sqrt(random.nextDouble()) * 18, random.nextDouble() * Mth.TWO_PI, -0.5);
                particle(i == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, at, new Vec3(0, -0.15, 0));
            }
            if (age % 3 == 0) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                Vec3 at = onCircle(sky, 14 + random.nextDouble() * 10, angle, 1 + random.nextDouble() * 3);
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.12));
            }
        }

        @Override public void release() {
            planes.clear();
            if (runes != null) runes.remove();
            if (skyLight != null) skyLight.close();
        }
    }

    /** One meteor, flown at the speed the server flies it, from its launch until its impact message, then its blast. */
    private static final class Meteor implements Fx.Animation {
        final Vec3 start;
        final Vec3 velocity;
        final int lifetime;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle light;
        Vec3 position;
        int age;
        int impactAge = -1;

        Meteor(SpellEffect launch) {
            this.start = launch.position();
            this.velocity = launch.direction();
            this.lifetime = Math.max(1, launch.age());
            this.position = start;
        }

        void impact(Vec3 at) {
            if (impactAge >= 0) return;
            impactAge = 0;
            position = at;
            float near = proximity(at);
            ScreenEffects.shake(0.35f * near);
            ScreenEffects.flash(0.12f * near);
            if (particlesVisible(at)) impactParticles();
        }

        @Override public boolean tick() {
            if (impactAge >= 0) {
                impactAge++;
            } else {
                age++;
                Vec3 previous = position;
                position = start.add(velocity.scale(age));
                if (particlesVisible(position)) trailParticles(previous);
            }
            SpellLighting.guarded(this::animate);
            return impactAge > IMPACT_TICKS || impactAge < 0 && age > lifetime + 5;
        }

        private void animate() {
            planes.begin();
            if (impactAge < 0) {
                planes.glow(position, 1.8f + 0.3f * Mth.sin(age * 1.3f), BLAZE);
                planes.glow(position.subtract(velocity.scale(0.4)), 1.3f, scaled(FIRE, 0.8f));
                light = light(light, position, 12, 2.5f, FIRE);
            } else {
                float fade = 1 - impactAge / (float) IMPACT_TICKS;
                if (impactAge <= 10) planes.glow(position.add(0, 1, 0), 2 + 5 * ease(impactAge / 3f), scaled(BLAZE, 1 - impactAge / 10f));
                if (impactAge <= 9) planes.ring(position.add(0, 0.2, 0), UP, 1 + 8 * ease(impactAge / 8f), 0.5f * (1 - impactAge / 9f) + 0.05f,
                        scaled(FIRE, 1 - impactAge / 9f));
                light = light(light, position.add(0, 1.5, 0), 14, 4 * fade * fade, FIRE);
            }
            planes.end();
        }

        /** Flame and smoke along this tick's stretch of the path, with the odd spat ember. */
        private void trailParticles(Vec3 from) {
            for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                Vec3 at = from.lerp(position, random.nextDouble()).add(anyDirection(random).scale(0.4));
                particle(ParticleTypes.FLAME, at, anyDirection(random).scale(0.03));
            }
            for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                particle(ParticleTypes.LARGE_SMOKE, from.lerp(position, random.nextDouble()), Vec3.ZERO);
            }
            if (age % 2 == 0) particle(ParticleTypes.LAVA, position, Vec3.ZERO);
        }

        private void impactParticles() {
            particle(ParticleTypes.EXPLOSION_EMITTER, position, Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                particle(ParticleTypes.EXPLOSION, position.add(upwards(random, 0.2).scale(1 + random.nextDouble() * 2.5)), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(30); i < n; i++) {
                particle(ParticleTypes.FLAME, position.add(0, 0.5, 0), upwards(random, 0.3).scale(0.2 + random.nextDouble() * 0.4));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = Fx.scaled(30); i < n; i++) {
                particle(debris, position.add(0, 0.5, 0), upwards(random, 0.6).scale(0.4 + random.nextDouble() * 0.6));
            }
            for (int i = 0, n = Fx.scaled(10); i < n; i++) particle(ParticleTypes.LAVA, position, Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(4); i < n; i++) {
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, position.add(upwards(random, 0).scale(random.nextDouble() * 2)), new Vec3(0, 0.07, 0));
            }
        }

        private BlockState ground() {
            BlockPos pos = BlockPos.containing(position);
            for (int i = 0; i < 4; i++, pos = pos.below()) {
                BlockState state = world().getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) return state;
            }
            return Blocks.STONE.defaultBlockState();
        }

        @Override public void release() {
            planes.clear();
            if (light != null) light.close();
        }
    }
}
