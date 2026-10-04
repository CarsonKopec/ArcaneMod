package dev.arcane.client.fx;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.NebulonRendering;
import de.nexusrealms.nebulon.api.render.WorldEffect;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.effect.SdfPlane;
import de.nexusrealms.nebulon.api.render.light.PointLight;
import de.nexusrealms.nebulon.api.render.light.client.NebulonLights;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.spell.SpellTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * Shared tools for the Astral Staff's visuals: Nebulon shapes and lights that update in place, particles that follow
 * the Particles setting, and {@link Tracker}s that run each spell's animations.
 *
 * <p>Nebulon 0.1.2 has three traps these work around. Its additive blend ignores alpha, so a {@link MagicCircle} adds
 * most of its colour across its whole disc and turns into a flat plate in daylight: circles' lines are drawn with crisp
 * {@link SdfPlane}s ({@link Planes}), with magic circles kept dim, for runes only. Its billboard renderer can stall the
 * game when a frame draws billboards with more than one texture, so only Celestial Judgment's beam is a billboard. And
 * its volumetric fog softens the whole frame, so nothing uses it.
 *
 * <p>Every Nebulon call must run inside {@link SpellLighting#guarded}.
 */
final class Fx {
    static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);
    static final double RENDER_DISTANCE = SpellTuning.EFFECT_RANGE;
    /** Spells this far from the camera spawn no particles; their lights and shapes still show. */
    static final double PARTICLE_RANGE = 192;
    static final List<Tracker<?>> TRACKERS = new ArrayList<>();

    private Fx() {
    }

    static ClientLevel world() {
        return Minecraft.getInstance().level;
    }

    static Vec3 camera() {
        return Minecraft.getInstance().gameRenderer.getMainCamera().position();
    }

    /** 1 within 48 blocks of the camera, fading to 0 at 192: how hard screen effects hit. */
    static float proximity(Vec3 at) {
        return (float) Mth.clamp(1 - (camera().distanceTo(at) - 48) / 144, 0, 1);
    }

    /** Eases out: fast at first, settling at 1. */
    static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /** The colour at {@code f} of its strength. Additive, so dimming means scaling the colour itself, not alpha alone. */
    static ColorRgba scaled(ColorRgba color, float f) {
        f = Mth.clamp(f, 0, 1);
        return new ColorRgba(color.red() * f, color.green() * f, color.blue() * f, color.alpha() * f);
    }

    /** The colour, {@code amount} of the way to white. */
    static ColorRgba whiter(ColorRgba color, float amount) {
        return new ColorRgba(Mth.lerp(amount, color.red(), 1), Mth.lerp(amount, color.green(), 1), Mth.lerp(amount, color.blue(), 1), color.alpha());
    }

    // Nebulon: each creates its handle on first use, then updates it in place.

    static <E extends WorldEffect> EffectHandle<E> show(EffectHandle<E> handle, E effect) {
        if (handle != null && handle.isAlive()) {
            handle.set(effect);
            return handle;
        }
        return NebulonRendering.effects().spawn(world(), effect);
    }

    static <E extends WorldEffect> EffectHandle<E> hide(EffectHandle<E> handle) {
        if (handle != null) handle.remove();
        return null;
    }

    static MagicCircle circle(Vec3 center, Vec3 normal, double radius, float lineWidth, ColorRgba color, int layers, long seed, float spin) {
        return new MagicCircle(center, normal, Math.max(0.05, radius), lineWidth, color, layers, seed, spin, RENDER_DISTANCE);
    }

    static PointLightHandle light(PointLightHandle handle, Vec3 at, float radius, float brightness, ColorRgba color) {
        PointLight light = NebulonLights.point(at).color(color).radius(Mth.clamp(radius, 0.5f, 128)).brightness(Mth.clamp(brightness, 0, 64))
                .maxRenderDistance(RENDER_DISTANCE).build();
        if (handle != null && handle.isAlive()) {
            handle.set(light);
            return handle;
        }
        return NebulonLights.lights().add(world(), light);
    }

    static PointLightHandle unlight(PointLightHandle handle) {
        if (handle != null) handle.close();
        return null;
    }

    // Particles, following the Particles setting like SpellParticles.

    static boolean particlesVisible(Vec3 at) {
        return Minecraft.getInstance().options.particles().get() != ParticleStatus.MINIMAL
                && camera().distanceToSqr(at) < PARTICLE_RANGE * PARTICLE_RANGE;
    }

    /** {@code count} halved under Decreased particles. */
    static int scaled(int count) {
        return Minecraft.getInstance().options.particles().get() == ParticleStatus.DECREASED ? (count + 1) / 2 : count;
    }

    static void particle(ParticleOptions type, Vec3 at, Vec3 velocity) {
        world().addParticle(type, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    static Vec3 onCircle(Vec3 center, double radius, double angle, double up) {
        return center.add(Math.cos(angle) * radius, up, Math.sin(angle) * radius);
    }

    /** A random direction in the upper half, tilted further up by {@code lift}: for things flung from the ground. */
    static Vec3 upwards(RandomSource random, double lift) {
        Vec3 direction = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) + lift, random.nextGaussian());
        return direction.lengthSqr() < 1.0e-6 ? UP : direction.normalize();
    }

    /** A random direction, any way at all. */
    static Vec3 anyDirection(RandomSource random) {
        Vec3 direction = new Vec3(random.nextGaussian(), random.nextGaussian(), random.nextGaussian());
        return direction.lengthSqr() < 1.0e-6 ? UP : direction.normalize();
    }

    /** A random offset between -spread/2 and spread/2. */
    static double jitter(RandomSource random, double spread) {
        return (random.nextDouble() - 0.5) * spread;
    }

    /** One running animation: a strike, a meteor, a gate. */
    interface Animation {
        /** Advances a tick; returns true once it's over. */
        boolean tick();

        /** Lets go of its Nebulon handles. */
        void release();
    }

    /**
     * A spell's running animations, keyed by the effects' source id. Ticked and cleared through {@link AstralFx};
     * everything belongs to the world it started in, and a dimension change drops it.
     */
    static final class Tracker<T extends Animation> {
        private final Map<Integer, T> live = new HashMap<>();
        private ClientLevel world;

        Tracker() {
            TRACKERS.add(this);
        }

        T get(int id, ClientLevel level, IntFunction<T> create) {
            ensureWorld(level);
            return live.computeIfAbsent(id, create::apply);
        }

        void tick(ClientLevel level) {
            ensureWorld(level);
            for (Iterator<T> it = live.values().iterator(); it.hasNext(); ) {
                T animation = it.next();
                if (animation.tick()) {
                    SpellLighting.guarded(animation::release);
                    it.remove();
                }
            }
        }

        void clear() {
            live.values().forEach(animation -> SpellLighting.guarded(animation::release));
            live.clear();
            world = null;
        }

        private void ensureWorld(ClientLevel level) {
            if (world != level) {
                clear();
                world = level;
            }
        }
    }

    /**
     * Flat shapes redrawn every tick: {@link #begin}, draw this tick's shapes, then {@link #end}, which removes any left
     * over from last tick. Handles are reused in drawing order, so a steady set of shapes just updates in place.
     */
    static final class Planes {
        private final List<EffectHandle<SdfPlane>> handles = new ArrayList<>();
        private int used;

        void begin() {
            used = 0;
        }

        void ring(Vec3 center, Vec3 normal, double radius, float line, ColorRgba color) {
            if (radius <= line) return;
            draw(SdfPlane.ring().at(center).normal(normal).size((float) (radius * 2)).lineWidth(line).color(color)
                    .maxRenderDistance(RENDER_DISTANCE).build());
        }

        void arc(Vec3 center, Vec3 normal, double radius, float line, float from, float to, ColorRgba color) {
            if (radius <= line) return;
            draw(SdfPlane.arc().arc(from, to).at(center).normal(normal).size((float) (radius * 2)).lineWidth(line).color(color)
                    .maxRenderDistance(RENDER_DISTANCE).build());
        }

        void polygon(Vec3 center, Vec3 normal, double radius, int sides, float turn, float line, ColorRgba color) {
            if (radius <= line) return;
            draw(SdfPlane.builder(SdfPlane.Shape.POLYGON).polygon(sides, turn).at(center).normal(normal).size((float) (radius * 2))
                    .lineWidth(line).color(color).maxRenderDistance(RENDER_DISTANCE).build());
        }

        /**
         * A glowing ball: three discs facing the camera, each smaller one adding more light, so the centre is
         * brightest. Turned towards the camera each tick.
         */
        void glow(Vec3 center, float radius, ColorRgba color) {
            if (radius <= 0.05f) return;
            Vec3 toCamera = camera().subtract(center);
            Vec3 facing = toCamera.lengthSqr() < 1.0e-6 ? UP : toCamera.normalize();
            float[] sizes = {1, 0.7f, 0.4f}, strengths = {0.22f, 0.3f, 0.48f};
            for (int i = 0; i < sizes.length; i++) {
                draw(SdfPlane.circle().at(center.add(facing.scale(i * 0.05))).normal(facing).size(radius * 2 * sizes[i])
                        .color(scaled(color, strengths[i])).maxRenderDistance(RENDER_DISTANCE).build());
            }
        }

        /** A ring turned to face the camera: a halo. */
        void halo(Vec3 center, double radius, float line, ColorRgba color) {
            Vec3 toCamera = camera().subtract(center);
            ring(center, toCamera.lengthSqr() < 1.0e-6 ? UP : toCamera.normalize(), radius, line, color);
        }

        /**
         * A seal of radius {@code outer}: a bold outer ring with a fine one inside it, framing a star of {@code points}
         * (two polygons of half as many sides, the second turned between the first's corners) turned by {@code turn}.
         */
        void seal(Vec3 center, Vec3 normal, double outer, float line, int points, float turn, ColorRgba color) {
            if (outer < 0.5) return;
            ring(center, normal, outer, line, color);
            ring(center, normal, outer * 0.9, line * 0.45f, scaled(color, 0.8f));
            int sides = points / 2;
            polygon(center, normal, outer * 0.86, sides, turn, line * 0.6f, scaled(color, 0.85f));
            polygon(center, normal, outer * 0.86, sides, turn + Mth.PI / sides, line * 0.6f, scaled(color, 0.85f));
        }

        private void draw(SdfPlane plane) {
            if (used < handles.size()) handles.set(used, show(handles.get(used), plane));
            else handles.add(show(null, plane));
            used++;
        }

        void end() {
            while (handles.size() > used) handles.removeLast().remove();
        }

        void clear() {
            begin();
            end();
        }
    }
}
