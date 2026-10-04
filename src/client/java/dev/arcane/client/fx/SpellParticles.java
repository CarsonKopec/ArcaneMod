package dev.arcane.client.fx;

import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModParticles;
import dev.arcane.spell.SpellTuning;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Particle choreography for each spell phase. Everything is computed locally from one effect message.
 *
 * <p>Distance and the Particles video setting are applied to whole effects ({@link #visible}, {@link #scaled}),
 * so shapes stay intact; vanilla's per-particle checks would clip rings at 32 blocks and punch random gaps.
 */
public final class SpellParticles {
    private static final ParticleOptions VIOLET = ModParticles.VIOLET;
    private static final ParticleOptions PINK = ModParticles.PINK;
    private static final ParticleOptions CYAN = ModParticles.CYAN;
    private static final Vec3 X = new Vec3(1, 0, 0), Y = new Vec3(0, 1, 0), Z = new Vec3(0, 0, 1);
    /** Golden angle in radians: successive points never line up, giving even spreads without randomness. */
    private static final double GOLDEN_ANGLE = 2.3999632297;
    /** Effects farther than this from the camera spawn no particles; their Nebulon light and arcs still show. */
    private static final double PARTICLE_RANGE = 64;
    private static final int MAX_ANIMATIONS = 24;
    private static final List<Animation> ANIMATIONS = new ArrayList<>();

    private SpellParticles() {
    }

    public static void play(SpellEffectPayload effect) {
        // A ward's end must stop its shimmer even when the ward itself is out of particle range.
        if (effect.kind() == SpellEffectPayload.Kind.WARD_END) stopShimmer(effect.sourceId());
        if (!visible(effect.position())) return;
        switch (effect.kind()) {
            case CHARGE -> charge(effect);
            case BOLT -> bolt(effect);
            case IMPACT -> impact(effect);
            case CHAIN -> chain(effect);
            case BLINK -> blink(effect);
            case WARD -> ward(effect);
            case WARD_HIT -> wardHit(effect);
            case WARD_END -> wardEnd(effect);
            case NOVA -> nova(effect);
        }
    }

    public static void tick(ClientLevel level) {
        // An animation belongs to the world it started in; drop it after a dimension change.
        ANIMATIONS.removeIf(animation -> animation.level != level || animation.tick());
    }

    public static void clear() {
        ANIMATIONS.clear();
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
        int samples = scaled(14);
        for (int i = 0; i < samples; i++) {
            double t = i / (double) samples;
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
        start(new Shockwave(Minecraft.getInstance().level, effect.position(), effect.direction(), effect.power()));
        int count = scaled(150);
        for (int i = 0; i < count; i++) {
            particle(i % 3 == 0 ? ParticleTypes.END_ROD : VIOLET, effect.position(), sphere(i, count).scale(0.3 + effect.power() * 0.65));
        }
        // No explosion emitter here: the server's vanilla terrain blast already sends one.
        particle(ColorParticleOption.create(ParticleTypes.FLASH, 0xFFE4BCFF), effect.position(), Vec3.ZERO);
        int puffs = scaled(32);
        for (int i = 0; i < puffs; i++) {
            double a = i * GOLDEN_ANGLE;
            Vec3 velocity = new Vec3(Math.cos(a) * 0.25, 0.05 + (i % 5) * 0.035, Math.sin(a) * 0.25);
            particle(ParticleTypes.LARGE_SMOKE, effect.position().add(velocity.scale(3)), velocity);
            particle(ParticleTypes.FIREWORK, effect.position(), velocity.scale(3));
        }
    }

    /** A jagged line of sparks along one leap, and a crackle where it lands. */
    private static void chain(SpellEffectPayload effect) {
        Vec3 from = effect.position(), path = effect.direction();
        double length = path.length();
        if (length < 1.0e-3) return;
        Vec3 n = path.scale(1 / length), u = perpendicular(n), v = n.cross(u);
        int sparks = scaled((int) Math.ceil(length * 3));
        for (int i = 0; i < sparks; i++) {
            // A fixed zigzag rather than random jitter, so every client draws the same bolt.
            Vec3 zigzag = u.scale(Math.sin(i * 2.3 + effect.age() * 1.7) * 0.15).add(v.scale(Math.cos(i * 1.9) * 0.15));
            particle(i % 3 == 0 ? PINK : CYAN, from.add(path.scale((i + 0.5) / sparks)).add(zigzag), Vec3.ZERO);
        }
        burst(from.add(path), scaled(14), ParticleTypes.ELECTRIC_SPARK, 0.25);
    }

    /** A streak along the path at chest height, and a swirl of portal motes at each end. */
    private static void blink(SpellEffectPayload effect) {
        Vec3 from = effect.position().add(0, 1, 0), path = effect.direction();
        if (path.lengthSqr() < 1.0e-6) return;
        Vec3 drift = path.normalize().scale(0.02);
        int steps = scaled((int) Math.ceil(path.length() * 4));
        for (int i = 0; i < steps; i++) {
            particle(i % 2 == 0 ? PINK : VIOLET, from.add(path.scale((i + 0.5) / steps)), drift);
        }
        burst(from, scaled(24), ParticleTypes.REVERSE_PORTAL, 0.12);
        burst(from.add(path), scaled(24), ParticleTypes.REVERSE_PORTAL, 0.12);
        burst(from.add(path), scaled(12), PINK, 0.2);

        // Everything above is at the caster's feet or behind them. On their own screen, mark the arrival with
        // rings framing the view. The teleport packet arrived first, so the player is already at the destination.
        var player = Minecraft.getInstance().player;
        if (player != null && player.getId() == effect.sourceId()) {
            Vec3 look = player.getLookAngle(), u = perpendicular(look), v = look.cross(u);
            Vec3 ahead = player.getEyePosition().add(look.scale(1.8));
            ring(ahead, u, v, 0.9, 0, 36, PINK);
            ring(ahead, u, v, 0.6, 0.2, 24, VIOLET);
        }
    }

    private static void ward(SpellEffectPayload effect) {
        stopShimmer(effect.sourceId());
        start(new WardShimmer(Minecraft.getInstance().level, effect.sourceId(), effect.age()));
    }

    /** A small ripple on the shell where it was struck, with sparks thrown back outwards. */
    private static void wardHit(SpellEffectPayload effect) {
        Vec3 n = effect.direction().lengthSqr() > 1.0e-6 ? effect.direction().normalize() : Y;
        Vec3 u = perpendicular(n), v = n.cross(u);
        ring(effect.position(), u, v, 0.35, 0, 12, PINK);
        ring(effect.position(), u, v, 0.6, 0.3, 16, VIOLET);
        int sparks = scaled(10);
        for (int i = 0; i < sparks; i++) {
            double a = i * GOLDEN_ANGLE;
            Vec3 direction = n.add(u.scale(Math.cos(a) * 0.6)).add(v.scale(Math.sin(a) * 0.6));
            particle(ParticleTypes.ELECTRIC_SPARK, effect.position(), direction.scale(0.15));
        }
    }

    /** A shattered ward bursts outwards in shards; one that simply ran out collapses gently inwards. */
    private static void wardEnd(SpellEffectPayload effect) {
        boolean shattered = effect.power() >= 1;
        int count = scaled(shattered ? 60 : 30);
        for (int i = 0; i < count; i++) {
            Vec3 direction = sphere(i, count);
            Vec3 at = effect.position().add(direction.scale(SpellTuning.WARD_RADIUS));
            if (shattered) particle(i % 2 == 0 ? VIOLET : PINK, at, direction.scale(0.3));
            else particle(VIOLET, at, direction.scale(-0.06));
        }
        if (shattered) burst(effect.position(), scaled(12), ParticleTypes.END_ROD, 0.2);
    }

    /**
     * A ring of frost racing across the ground. Nothing is spawned around the caster's body: the caster's camera is
     * there, and snowflakes that close fill the whole screen.
     */
    private static void nova(SpellEffectPayload effect) {
        start(new FrostRing(Minecraft.getInstance().level, effect.position()));
    }

    private static void start(Animation animation) {
        if (ANIMATIONS.size() >= MAX_ANIMATIONS) ANIMATIONS.removeFirst();
        ANIMATIONS.add(animation);
    }

    private static void stopShimmer(int entityId) {
        ANIMATIONS.removeIf(animation -> animation instanceof WardShimmer shimmer && shimmer.entityId == entityId);
    }

    private static Vec3 perpendicular(Vec3 forward) {
        return forward.cross(Math.abs(forward.y) > 0.9 ? X : Y).normalize();
    }

    /** Point {@code i} of {@code count} spread evenly over the unit sphere (a Fibonacci sphere). */
    private static Vec3 sphere(int i, int count) {
        double y = 1 - 2 * (i + 0.5) / count;
        double radius = Math.sqrt(1 - y * y);
        double angle = i * GOLDEN_ANGLE;
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    /** {@code count} particles flying out evenly in every direction. */
    private static void burst(Vec3 at, int count, ParticleOptions type, double speed) {
        for (int i = 0; i < count; i++) particle(type, at, sphere(i, count).scale(speed));
    }

    /** Whether an effect at {@code at} gets particles: never with Particles on Minimal, and only near the camera. */
    private static boolean visible(Vec3 at) {
        Minecraft client = Minecraft.getInstance();
        return client.options.particles().get() != ParticleStatus.MINIMAL
                && client.gameRenderer.getMainCamera().position().distanceToSqr(at) < PARTICLE_RANGE * PARTICLE_RANGE;
    }

    /** A particle count thinned for the Particles video setting: Decreased halves it. */
    private static int scaled(int count) {
        return Minecraft.getInstance().options.particles().get() == ParticleStatus.DECREASED ? (count + 1) / 2 : count;
    }

    /** Spawns past vanilla's distance and density checks, which {@link #visible} and {@link #scaled} replace. */
    private static void particle(ParticleOptions type, Vec3 at, Vec3 velocity) {
        var level = Minecraft.getInstance().level;
        if (level != null) level.addParticle(type, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    /** Evenly spaced particles on a circle in the plane spanned by unit vectors {@code u} and {@code v}. */
    private static void ring(Vec3 center, Vec3 u, Vec3 v, double radius, double rotation, int count, ParticleOptions type) {
        int n = scaled(count);
        for (int i = 0; i < n; i++) {
            double a = rotation + Math.PI * 2 * i / n;
            particle(type, center.add(u.scale(Math.cos(a) * radius)).add(v.scale(Math.sin(a) * radius)), Vec3.ZERO);
        }
    }

    /** A particle effect that plays out over several ticks. */
    private abstract static class Animation {
        final ClientLevel level;
        int age;

        Animation(ClientLevel level) {
            this.level = level;
        }

        /** Returns true when the animation has finished. */
        abstract boolean tick();
    }

    /** Multi-tick impact animation: a fast primary blast, a slower echo ring, and rising embers. */
    private static final class Shockwave extends Animation {
        final Vec3 center, u, v;
        final float power;

        Shockwave(ClientLevel level, Vec3 center, Vec3 direction, float power) {
            super(level);
            this.center = center;
            this.power = power;
            Vec3 n = direction.normalize();
            u = perpendicular(n);
            v = n.cross(u);
        }

        @Override boolean tick() {
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
                int embers = scaled(10);
                for (int i = 0; i < embers; i++) {
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

    /** Motes drifting around a ward's shell, following the warded entity until the ward ends. */
    private static final class WardShimmer extends Animation {
        final int entityId;
        final int duration;

        WardShimmer(ClientLevel level, int entityId, int duration) {
            super(level);
            this.entityId = entityId;
            this.duration = duration;
        }

        @Override boolean tick() {
            Entity entity = level.getEntity(entityId);
            if (entity == null || ++age > duration) return true;
            if (age % 2 == 0) {
                Vec3 center = entity.getBoundingBox().getCenter();
                double cos = Math.cos(age * 0.07), sin = Math.sin(age * 0.07);
                int count = scaled(14);
                for (int i = 0; i < count; i++) {
                    Vec3 p = sphere(i, count);
                    // Turning the points about the vertical axis makes them drift around the shell.
                    Vec3 turned = new Vec3(p.x * cos - p.z * sin, p.y, p.x * sin + p.z * cos);
                    particle(i % 3 == 0 ? CYAN : VIOLET, center.add(turned.scale(SpellTuning.WARD_RADIUS)), Vec3.ZERO);
                }
            }
            return false;
        }
    }

    /** A ring of frost racing out across the ground, shedding snowflakes. */
    private static final class FrostRing extends Animation {
        private static final int DURATION = 12;
        final Vec3 ground;

        FrostRing(ClientLevel level, Vec3 feet) {
            super(level);
            ground = feet.add(0, 0.15, 0);
        }

        @Override boolean tick() {
            if (++age > DURATION) return true;
            double t = age / (double) DURATION;
            double radius = SpellTuning.NOVA_RADIUS * (1 - Math.pow(1 - t, 3)); // ease-out cubic
            if (age % 2 == 0) ring(ground, X, Z, radius, age * 0.1, 48, CYAN);
            int flakes = scaled(8);
            for (int i = 0; i < flakes; i++) {
                double a = Math.PI * 2 * i / flakes + age * 0.2;
                Vec3 at = ground.add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
                particle(i % 4 == 0 ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE, at, new Vec3(Math.cos(a) * 0.05, 0.04, Math.sin(a) * 0.05));
            }
            return false;
        }
    }
}
