package dev.arcane.client.fx;

import static dev.leyline.client.fx.Fx.DOWN;
import static dev.leyline.client.fx.Fx.UP;
import static dev.leyline.client.fx.Fx.anyDirection;
import static dev.leyline.client.fx.Fx.circle;
import static dev.leyline.client.fx.Fx.ease;
import static dev.leyline.client.fx.Fx.jitter;
import static dev.leyline.client.fx.Fx.light;
import static dev.leyline.client.fx.Fx.onCircle;
import static dev.leyline.client.fx.Fx.particle;
import static dev.leyline.client.fx.Fx.particlesVisible;
import static dev.leyline.client.fx.Fx.proximity;
import static dev.leyline.client.fx.Fx.scaled;
import static dev.leyline.client.fx.Fx.show;
import static dev.leyline.client.fx.Fx.unlight;
import static dev.leyline.client.fx.Fx.upwards;
import static dev.leyline.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.effect.EffectRenderers;
import dev.leyline.client.fx.Fx;
import dev.leyline.client.fx.NebulonGuard;
import dev.leyline.client.screen.ScreenEffects;
import dev.leyline.effect.Effect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Meteor Swarm as the client sees it: a burning seal over the target area, its edge marked in red on the ground, and
 * each meteor a white-hot ball shedding sparks and trailing flame and lingering smoke, flown here from its launch
 * message until it strikes: a fireball, a shockwave racing out across the ground and debris flung high, drawn as big
 * as the server's blast. The Dread Star, last, is the same at its own far larger size, and its shockwave drives a wall
 * of dust out across the ground.
 */
final class MeteorFx {
    private static final Fx.Tracker<Swarm> SWARMS = AstralFx.TRACKERS.create();
    private static final Fx.Tracker<Meteor> METEORS = AstralFx.TRACKERS.create();
    private static final ColorRgba FIRE = ColorRgba.rgba(0xFF8A3DFF);
    private static final ColorRgba BLAZE = ColorRgba.rgba(0xFFE3A0FF);
    private static final ColorRgba CRIMSON = ColorRgba.rgba(0xFF3B1FFF);
    /** Vanilla's firework flash, tinted: the instant of each impact. */
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFC890);
    /** Big, slow motes of brown dust, for the Dread Star's dust wall. */
    private static final ParticleOptions DUST = new DustParticleOptions(0x7A6A58, 4);
    private static final int TINT = 0x200804;
    /** How long an impact keeps glowing. */
    private static final int IMPACT_TICKS = 24;

    private MeteorFx() {
    }

    static void register() {
        EffectRenderers.register(ModSpellEffects.METEOR_SWARM,
                (effect, level) -> SWARMS.get(effect.sourceId(), level, id -> new Swarm(id, effect)));
        EffectRenderers.register(ModSpellEffects.METEOR,
                (effect, level) -> METEORS.get(effect.sourceId(), level, id -> new Meteor(effect)));
        EffectRenderers.register(ModSpellEffects.METEOR_IMPACT,
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

        Swarm(int id, Effect effect) {
            this.id = id;
            this.mark = effect.position();
            this.sky = mark.add(effect.direction());
            this.area = effect.power();
            this.duration = effect.age();
        }

        @Override public boolean tick() {
            age++;
            NebulonGuard.guarded(this::animate);
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
        /** 1 for a meteor; the Dread Star is several times that. */
        final float size;
        /**
         * How big its impact looks, 1 for a blast reaching 6 blocks: drawn to scale with the blast the server deals, so
         * a meteor's lands far bigger than its head looks, and the Dread Star's bigger still.
         */
        final float impact;
        final Fx.Planes planes = new Fx.Planes();
        final RandomSource random = RandomSource.create();
        PointLightHandle light;
        Vec3 position;
        int age;
        int impactAge = -1;

        Meteor(Effect launch) {
            this.start = launch.position();
            this.velocity = launch.direction();
            this.lifetime = Math.max(1, launch.age());
            this.size = Math.max(1, launch.power());
            this.impact = (float) ((size > 1 ? SpellTuning.DREAD_STAR_BLAST_RADIUS : SpellTuning.METEOR_BLAST_RADIUS) / 6);
            this.position = start;
        }

        void impact(Vec3 at) {
            if (impactAge >= 0) return;
            impactAge = 0;
            position = at;
            float near = proximity(at);
            ScreenEffects.shake(Math.min(1, 0.3f * impact) * near);
            ScreenEffects.flash(Math.min(1, 0.06f * impact * impact) * near);
            if (particlesVisible(at)) impactParticles();
        }

        /** How long its impact keeps glowing: longer for the Dread Star. */
        private int impactTicks() {
            return Math.round(IMPACT_TICKS * (float) Math.sqrt(impact));
        }

        @Override public boolean tick() {
            if (impactAge >= 0) {
                impactAge++;
                if (size > 1 && particlesVisible(position)) dustWall();
            } else {
                age++;
                Vec3 previous = position;
                position = start.add(velocity.scale(age));
                if (particlesVisible(position)) trailParticles(previous);
            }
            NebulonGuard.guarded(this::animate);
            return impactAge > impactTicks() || impactAge < 0 && age > lifetime + 5;
        }

        private void animate() {
            planes.begin();
            if (impactAge < 0) {
                // A white-hot core in a blazing head, its fire streaming out behind it.
                planes.glow(position, size * (1.8f + 0.3f * Mth.sin(age * 1.3f)), BLAZE);
                planes.glow(position, size * 0.55f, ColorRgba.WHITE);
                for (int i = 1; i <= 3; i++) {
                    planes.glow(position.subtract(velocity.scale(0.35 * i * size)), size * (1.6f - 0.3f * i), scaled(i < 3 ? FIRE : CRIMSON, 1 - 0.2f * i));
                }
                light = light(light, position, 14 * size, 3.5f * size, FIRE);
            } else {
                int glowTicks = Math.round(10 * impact), ringTicks = ringTicks();
                float fade = 1 - impactAge / (float) impactTicks();
                if (impactAge <= glowTicks) {
                    // The fireball, and a white-hot core flaring at its heart for the first instants.
                    planes.glow(position.add(0, impact, 0), impact * (2 + 5 * ease(impactAge / 3f)), scaled(BLAZE, 1 - impactAge / (float) glowTicks));
                    if (impactAge <= 4) planes.glow(position.add(0, impact, 0), impact * 2.5f, scaled(ColorRgba.WHITE, 1 - impactAge / 5f));
                }
                if (impactAge <= ringTicks) {
                    float left = 1 - impactAge / (float) ringTicks;
                    planes.ring(position.add(0, 0.2, 0), UP, shockwave(), impact * 0.5f * left + 0.05f, scaled(FIRE, left));
                    // Behind it, the ground glowing where the blast scoured it.
                    planes.ring(position.add(0, 0.25, 0), UP, shockwave() * 0.55, impact * 0.9f * left + 0.05f, scaled(CRIMSON, 0.6f * left));
                }
                light = light(light, position.add(0, 1.5, 0), 14 * impact, 4 * impact * fade * fade, FIRE);
            }
            planes.end();
        }

        private int ringTicks() {
            return Math.round(9 * impact);
        }

        /** How far the impact's shockwave has raced out across the ground. */
        private double shockwave() {
            return impact * (1 + 8 * ease(impactAge / (float) (ringTicks() - 1)));
        }

        /** The Dread Star's shockwave, driving a wall of dust and grit out across the ground and leaving a haze. */
        private void dustWall() {
            if (impactAge > ringTicks()) return;
            double radius = shockwave();
            float left = 1 - impactAge / (float) ringTicks();
            BlockParticleOption grit = new BlockParticleOption(ParticleTypes.DUST_PILLAR, ground());
            for (int i = 0, n = Fx.scaled(Math.round(48 * left) + 8); i < n; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                Vec3 at = onCircle(position, radius + jitter(random, 1.5), angle, random.nextDouble() * 3 * left);
                switch (i % 4) {
                    case 0 -> particle(grit, at, out.scale(0.5).add(0, 0.4 + random.nextDouble() * 0.4, 0));
                    case 1 -> particle(DUST, at.add(0, random.nextDouble() * 4 * left, 0), out.scale(0.6));
                    default -> particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at, out.scale(0.12).add(0, 0.03, 0));
                }
            }
        }

        /** Flame and smoke along this tick's stretch of the path, with the odd spat ember. */
        private void trailParticles(Vec3 from) {
            for (int i = 0, n = Fx.scaled(Math.round(6 * size)); i < n; i++) {
                Vec3 at = from.lerp(position, random.nextDouble()).add(anyDirection(random).scale(0.4 * size));
                particle(ParticleTypes.FLAME, at, anyDirection(random).scale(0.03 * size));
            }
            for (int i = 0, n = Fx.scaled(Math.round(3 * size)); i < n; i++) {
                particle(ParticleTypes.LARGE_SMOKE, from.lerp(position, random.nextDouble()).add(anyDirection(random).scale(0.3 * size)), Vec3.ZERO);
            }
            if (age % 2 == 0) particle(ParticleTypes.LAVA, position, Vec3.ZERO);
            // Sparks shed from the head, flung back and out.
            for (int i = 0, n = Fx.scaled(Math.round(3 * size)); i < n; i++) {
                particle(ModParticles.GOLD, position, velocity.scale(-0.06).add(anyDirection(random).scale(0.25 * Math.sqrt(size))));
            }
            // Smoke that hangs in the sky after it, so the swarm leaves its trails behind.
            for (int i = 0, n = Fx.scaled(Math.round(size)); i < n; i++) {
                particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, from.lerp(position, random.nextDouble()), anyDirection(random).scale(0.01));
            }
        }

        private void impactParticles() {
            particle(FLARE, position.add(0, impact, 0), Vec3.ZERO);
            for (int i = 0; i < Math.round(impact * impact); i++) {
                particle(ParticleTypes.EXPLOSION_EMITTER, position.add(upwards(random, 0.3).scale(random.nextDouble() * 2.4 * impact)), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(Math.round(6 * impact)); i < n; i++) {
                particle(ParticleTypes.EXPLOSION, position.add(upwards(random, 0.2).scale(impact * (1 + random.nextDouble() * 2.5))), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(Math.round(30 * impact)); i < n; i++) {
                particle(ParticleTypes.FLAME, position.add(0, 0.5, 0), upwards(random, 0.3).scale(Math.sqrt(impact) * (0.2 + random.nextDouble() * 0.4)));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = Fx.scaled(Math.round(30 * impact)); i < n; i++) {
                particle(debris, position.add(0, 0.5, 0), upwards(random, 0.6).scale(Math.sqrt(impact) * (0.4 + random.nextDouble() * 0.6)));
            }
            for (int i = 0, n = Fx.scaled(Math.round(10 * impact)); i < n; i++) particle(ParticleTypes.LAVA, position, Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(Math.round(4 * impact)); i < n; i++) {
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, position.add(upwards(random, 0).scale(random.nextDouble() * 2 * impact)), new Vec3(0, 0.07, 0));
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
