package dev.arcane.client.fx;

import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.api.effect.SpellEffectType;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.screen.ScreenEffects;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
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
    private static final int MAX_ANIMATIONS = 64;
    private static final List<Animation> ANIMATIONS = new ArrayList<>();

    private SpellParticles() {
    }

    public static void register() {
        on(ModSpellEffects.CHARGE, SpellParticles::charge);
        on(ModSpellEffects.BOLT, SpellParticles::bolt);
        on(ModSpellEffects.IMPACT, SpellParticles::impact);
        on(ModSpellEffects.BOLT_SHARD, SpellParticles::shard);
        on(ModSpellEffects.CHAIN, SpellParticles::chain);
        on(ModSpellEffects.CHAIN_OVERLOAD, SpellParticles::overload);
        on(ModSpellEffects.BLINK, SpellParticles::blink);
        on(ModSpellEffects.AFTERIMAGE, SpellParticles::afterimage);
        on(ModSpellEffects.WARD, SpellParticles::ward);
        on(ModSpellEffects.WARD_HIT, SpellParticles::wardHit);
        // A ward's end must stop its shimmer even when the ward itself is out of particle range.
        SpellEffectRenderers.register(ModSpellEffects.WARD_END, (effect, level) -> stopShimmer(effect.sourceId()));
        on(ModSpellEffects.WARD_END, SpellParticles::wardEnd);
        on(ModSpellEffects.NOVA, SpellParticles::nova);
        on(ModSpellEffects.BACKLASH, SpellParticles::backlash);
        on(ModSpellEffects.SHATTER, SpellParticles::shatter);
        on(ModSpellEffects.CONDUCTION, SpellParticles::conduction);
    }

    /** Plays {@code type}'s particles when the effect is close enough to the camera, and particles are on. */
    private static void on(SpellEffectType type, Consumer<SpellEffect> play) {
        SpellEffectRenderers.register(type, (effect, level) -> {
            if (visible(effect.position())) play.accept(effect);
        });
    }

    public static void tick(ClientLevel level) {
        // An animation belongs to the world it started in; drop it after a dimension change.
        ANIMATIONS.removeIf(animation -> animation.level != level || animation.tick());
    }

    public static void clear() {
        ANIMATIONS.clear();
    }

    private static void charge(SpellEffect effect) {
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
        // Sparks spiral in from farther out as it charges, faster the fuller it is.
        if (effect.power() > 0.2f) {
            for (int i = 0; i < 2; i++) {
                double angle = effect.age() * 0.7 + i * Math.PI;
                Vec3 radial = u.scale(Math.cos(angle)).add(v.scale(Math.sin(angle)));
                Vec3 at = effect.position().add(radial.scale(radius * 3.2));
                Vec3 inward = effect.position().subtract(at).scale(0.09 + 0.06 * effect.power());
                particle(i == 0 ? PINK : CYAN, at, inward.add(n.cross(radial).scale(0.06)));
            }
        }
    }

    private static void bolt(SpellEffect effect) {
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

    private static void impact(SpellEffect effect) {
        start(new Shockwave(Minecraft.getInstance().level, effect.position(), effect.direction(), effect.power()));
        int count = scaled(150);
        for (int i = 0; i < count; i++) {
            particle(i % 3 == 0 ? ParticleTypes.END_ROD : VIOLET, effect.position(), sphere(i, count).scale(0.3 + effect.power() * 0.65));
        }
        // The blast's kick, felt nearby.
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(effect.position());
        ScreenEffects.shake((0.12f + 0.2f * effect.power()) * (float) Mth.clamp(1 - distance / 24, 0, 1));
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

    /** A shard streaking from the blast into a creature, and a spray of sparks where it strikes. */
    private static void shard(SpellEffect effect) {
        Vec3 from = effect.position(), path = effect.direction();
        if (path.lengthSqr() < 1.0e-6) return;
        int steps = scaled((int) Math.ceil(path.length() * 4));
        for (int i = 0; i < steps; i++) {
            particle(i % 2 == 0 ? PINK : ParticleTypes.END_ROD, from.add(path.scale((i + 0.5) / steps)), path.normalize().scale(0.02));
        }
        burst(from.add(path), scaled(10), ParticleTypes.ELECTRIC_SPARK, 0.2);
    }

    /** A jagged line of sparks along one leap, a crackle where it lands, and static left clinging to what it struck. */
    private static void chain(SpellEffect effect) {
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
        LivingEntity struck = struckAt(from.add(path));
        if (struck != null) start(new Static(Minecraft.getInstance().level, struck.getId()));
    }

    /** The chain's leftover leaps discharging: a crackling cage around the last creature struck, bursting outwards. */
    private static void overload(SpellEffect effect) {
        double radius = 0.8 + 1.2 * effect.power();
        ring(effect.position(), X, Z, radius, 0, 24, CYAN);
        ring(effect.position(), X, Y, radius, 0.4, 24, PINK);
        ring(effect.position(), Z, Y, radius, 0.8, 24, CYAN);
        burst(effect.position(), scaled(30 + Math.round(40 * effect.power())), ParticleTypes.ELECTRIC_SPARK, 0.45);
        start(new Ripple(Minecraft.getInstance().level, effect.position(), SpellTuning.CHAIN_OVERLOAD_RADIUS, 8, CYAN));
    }

    /** The creature a strike at {@code at} hit, if any; never the viewer's own body seen from inside it. */
    private static LivingEntity struckAt(Vec3 at) {
        Minecraft client = Minecraft.getInstance();
        LivingEntity nearest = null;
        double nearestDistance = 2.25;
        for (LivingEntity entity : client.level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(1.5))) {
            if (entity == client.player && client.options.getCameraType().isFirstPerson()) continue;
            double distance = entity.getBoundingBox().getCenter().distanceToSqr(at);
            if (distance < nearestDistance) {
                nearest = entity;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    /** A streak along the path at chest height, and a swirl of portal motes at each end. */
    private static void blink(SpellEffect effect) {
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
        ClientLevel level = Minecraft.getInstance().level;
        start(new Ghost(level, effect.position(), new Vec3(path.x, 0, path.z)));
        start(new Ripple(level, effect.position().add(path).add(0, 0.1, 0), 2.5, 10, PINK));

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

    /** A blink's afterimage bursting where the caster stood. */
    private static void afterimage(SpellEffect effect) {
        Vec3 center = effect.position().add(0, 1, 0);
        int count = scaled(50);
        for (int i = 0; i < count; i++) particle(i % 2 == 0 ? PINK : VIOLET, center, sphere(i, count).scale(0.35));
        burst(center, scaled(12), ParticleTypes.END_ROD, 0.25);
        start(new Ripple(Minecraft.getInstance().level, effect.position().add(0, 0.1, 0), SpellTuning.BLINK_AFTERIMAGE_RADIUS, 8, VIOLET));
    }

    private static void ward(SpellEffect effect) {
        stopShimmer(effect.sourceId());
        start(new WardShimmer(Minecraft.getInstance().level, effect.sourceId(), effect.age()));
    }

    /** A small ripple on the shell where it was struck, with sparks thrown back outwards. */
    private static void wardHit(SpellEffect effect) {
        Vec3 n = effect.direction().lengthSqr() > 1.0e-6 ? effect.direction().normalize() : Y;
        Vec3 u = perpendicular(n), v = n.cross(u);
        ring(effect.position(), u, v, 0.35, 0, 12, PINK);
        ring(effect.position(), u, v, 0.6, 0.3, 16, VIOLET);
        // A hexagon of the shell lights up where it's struck.
        for (int side = 0; side < 6; side++) {
            double a0 = side * Math.PI / 3, a1 = a0 + Math.PI / 3;
            Vec3 c0 = u.scale(Math.cos(a0) * 0.8).add(v.scale(Math.sin(a0) * 0.8));
            Vec3 c1 = u.scale(Math.cos(a1) * 0.8).add(v.scale(Math.sin(a1) * 0.8));
            for (int i = 0; i < 4; i++) particle(PINK, effect.position().add(c0.lerp(c1, i / 4.0)), Vec3.ZERO);
        }
        for (Animation animation : ANIMATIONS) {
            if (animation instanceof WardShimmer shimmer && shimmer.entityId == effect.sourceId()) shimmer.strength = effect.power();
        }
        int sparks = scaled(10);
        for (int i = 0; i < sparks; i++) {
            double a = i * GOLDEN_ANGLE;
            Vec3 direction = n.add(u.scale(Math.cos(a) * 0.6)).add(v.scale(Math.sin(a) * 0.6));
            particle(ParticleTypes.ELECTRIC_SPARK, effect.position(), direction.scale(0.15));
        }
    }

    /** A shattered ward bursts outwards in shards; one that simply ran out collapses gently inwards. */
    private static void wardEnd(SpellEffect effect) {
        boolean shattered = effect.power() >= 1;
        int count = scaled(shattered ? 90 : 30);
        for (int i = 0; i < count; i++) {
            Vec3 direction = sphere(i, count);
            Vec3 at = effect.position().add(direction.scale(SpellTuning.WARD_RADIUS));
            if (shattered) particle(i % 2 == 0 ? VIOLET : PINK, at, direction.scale(0.3));
            else particle(VIOLET, at, direction.scale(-0.06));
        }
        if (shattered) {
            burst(effect.position(), scaled(12), ParticleTypes.END_ROD, 0.2);
            // Its rebound, racing out across the ground.
            start(new Ripple(Minecraft.getInstance().level, effect.position().subtract(0, 0.8, 0), SpellTuning.WARD_REBOUND_RADIUS, 8, PINK));
        }
    }

    /**
     * A ring of frost racing across the ground. Nothing is spawned around the caster's body: the caster's camera is
     * there, and snowflakes that close fill the whole screen.
     */
    private static void nova(SpellEffect effect) {
        ClientLevel level = Minecraft.getInstance().level;
        start(new FrostRing(level, effect.position()));
        // Glints of ice flung outwards, low, starting well clear of the caster's camera: block particles that close
        // fill the view with flat squares.
        int spikes = scaled(32);
        for (int i = 0; i < spikes; i++) {
            double a = i * GOLDEN_ANGLE;
            Vec3 out = new Vec3(Math.cos(a), 0, Math.sin(a));
            Vec3 at = effect.position().add(out.scale(2.5)).add(0, 0.2 + (i % 3) * 0.2, 0);
            particle(CYAN, at, out.scale(0.45));
            if (i % 2 == 0) particle(ParticleTypes.SNOWFLAKE, at.add(out), out.scale(0.25));
        }
        start(new FrostGlitter(level, effect.position()));
    }

    /** Corruption tearing out of its caster: violet sparks and dark wisps burst from them, and a ring scorches their feet. */
    private static void backlash(SpellEffect effect) {
        Vec3 center = effect.position();
        Vec3 feet = center.subtract(0, 0.8, 0);
        ring(feet, X, Z, 1.3, 0, 32, VIOLET);
        ring(feet, X, Z, 0.9, 0.4, 24, PINK);
        // In the caster's own first-person view, start beyond arm's reach, so the burst frames the view, not fills it.
        Minecraft client = Minecraft.getInstance();
        boolean ownView = client.player != null && client.player.getId() == effect.sourceId() && client.options.getCameraType().isFirstPerson();
        int count = scaled(40 + Math.round(40 * effect.power()));
        for (int i = 0; i < count; i++) {
            Vec3 direction = sphere(i, count);
            Vec3 from = center.add(direction.scale(ownView ? 1.6 : 0.4));
            particle(i % 3 == 0 ? PINK : VIOLET, from, direction.scale(0.25));
            if (i % 4 == 0) particle(ParticleTypes.SQUID_INK, from, direction.scale(0.12));
        }
    }

    /** A frozen target's ice bursting off it in shards. */
    private static void shatter(SpellEffect effect) {
        BlockParticleOption ice = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState());
        double size = Math.max(0.5, effect.power());
        int count = scaled(30);
        for (int i = 0; i < count; i++) {
            Vec3 direction = sphere(i, count);
            Vec3 at = effect.position().add(direction.scale(size * 0.5));
            particle(ice, at, direction.scale(0.3));
            if (i % 3 == 0) particle(ParticleTypes.SNOWFLAKE, at, direction.scale(0.15));
        }
        burst(effect.position(), scaled(10), CYAN, 0.25);
    }

    /** Lightning surging over a wet target: a crackling cage of arcs around it. */
    private static void conduction(SpellEffect effect) {
        double radius = Math.max(0.5, effect.power()) * 0.8;
        ring(effect.position(), X, Z, radius, 0, 20, CYAN);
        ring(effect.position(), X, Y, radius, 0.5, 20, CYAN);
        ring(effect.position(), Z, Y, radius, 1.0, 20, PINK);
        burst(effect.position(), scaled(24), ParticleTypes.ELECTRIC_SPARK, 0.35);
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

    /** A random point inside {@code box}. */
    private static Vec3 inside(AABB box, RandomSource random) {
        return new Vec3(Mth.lerp(random.nextDouble(), box.minX, box.maxX), Mth.lerp(random.nextDouble(), box.minY, box.maxY),
                Mth.lerp(random.nextDouble(), box.minZ, box.maxZ));
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

    /**
     * Motes drifting around a ward's shell, following the warded entity until the ward ends: thinner as the ward
     * weakens, and crackling once it's half spent.
     */
    private static final class WardShimmer extends Animation {
        final int entityId;
        final int duration;
        final RandomSource random = RandomSource.create();
        /** The strength the ward has left, 0 to 1. */
        float strength = 1;

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
                int count = scaled(Math.round(14 * (0.35f + 0.65f * strength)));
                for (int i = 0; i < count; i++) {
                    Vec3 p = sphere(i, count);
                    // Turning the points about the vertical axis makes them drift around the shell.
                    Vec3 turned = new Vec3(p.x * cos - p.z * sin, p.y, p.x * sin + p.z * cos);
                    particle(i % 3 == 0 ? CYAN : VIOLET, center.add(turned.scale(SpellTuning.WARD_RADIUS)), Vec3.ZERO);
                }
                // Cracks spark across a ward that's half spent.
                if (strength < 0.5f && age % 6 == 0) {
                    for (int i = 0; i < 2; i++) {
                        Vec3 at = center.add(sphere(random.nextInt(32), 32).scale(SpellTuning.WARD_RADIUS));
                        particle(ParticleTypes.ELECTRIC_SPARK, at, Vec3.ZERO);
                        particle(PINK, at, Vec3.ZERO);
                    }
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
                // Frost glints on the ground the front has crossed.
                particle(CYAN, ground.add(Math.cos(a + 0.4) * radius * 0.7, 0.05, Math.sin(a + 0.4) * radius * 0.7), Vec3.ZERO);
            }
            return false;
        }
    }

    /** Creatures the nova froze glitter with frost until they thaw. */
    private static final class FrostGlitter extends Animation {
        final Vec3 center;
        final RandomSource random = RandomSource.create();

        FrostGlitter(ClientLevel level, Vec3 center) {
            super(level);
            this.center = center;
        }

        @Override boolean tick() {
            if (++age > SpellTuning.NOVA_FREEZE_TICKS + 40) return true;
            if (age % 4 != 0) return false;
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(SpellTuning.NOVA_RADIUS + 2),
                    LivingEntity::isFullyFrozen)) {
                for (int i = 0; i < 2; i++) particle(i == 0 ? ParticleTypes.SNOWFLAKE : CYAN, inside(entity.getBoundingBox(), random), Vec3.ZERO);
            }
            return false;
        }
    }

    /** Static clinging to a creature lightning struck, crackling over it for a while. */
    private static final class Static extends Animation {
        private static final int DURATION = 40;
        final int entityId;
        final RandomSource random = RandomSource.create();

        Static(ClientLevel level, int entityId) {
            super(level);
            this.entityId = entityId;
        }

        @Override boolean tick() {
            Entity entity = level.getEntity(entityId);
            if (entity == null || ++age > DURATION) return true;
            if (age % 2 == 0) particle(age % 6 == 0 ? CYAN : ParticleTypes.ELECTRIC_SPARK, inside(entity.getBoundingBox(), random), Vec3.ZERO);
            return false;
        }
    }

    /** A ring racing out, level, to {@code radius} over {@code duration} ticks. */
    private static final class Ripple extends Animation {
        final Vec3 center;
        final double radius;
        final int duration;
        final ParticleOptions type;

        Ripple(ClientLevel level, Vec3 center, double radius, int duration, ParticleOptions type) {
            super(level);
            this.center = center;
            this.radius = radius;
            this.duration = duration;
            this.type = type;
        }

        @Override boolean tick() {
            if (++age > duration) return true;
            double r = radius * (1 - Math.pow(1 - age / (double) duration, 3)); // ease-out cubic
            ring(center, X, Z, r, age * 0.1, Math.max(12, (int) (r * 10)), type);
            return false;
        }
    }

    /**
     * The afterimage a blink leaves where the caster stood: their outline, flickering in light, facing the way they
     * went, until it bursts.
     */
    private static final class Ghost extends Animation {
        final Vec3 feet, right;
        final RandomSource random = RandomSource.create();

        Ghost(ClientLevel level, Vec3 feet, Vec3 facing) {
            super(level);
            this.feet = feet;
            this.right = facing.lengthSqr() < 1.0e-6 ? X : facing.normalize().cross(Y).normalize();
        }

        @Override boolean tick() {
            if (++age >= SpellTuning.BLINK_AFTERIMAGE_TICKS) return true;
            if (age % 2 != 0) return false;
            // Brighter and more restless as it is about to burst.
            ParticleOptions type = age > SpellTuning.BLINK_AFTERIMAGE_TICKS * 0.7 ? PINK : VIOLET;
            float shown = 0.55f + 0.35f * age / SpellTuning.BLINK_AFTERIMAGE_TICKS;
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI / 5;
                point(Math.cos(a) * 0.22, 1.6 + Math.sin(a) * 0.22, type, shown);
            }
            for (int i = 0; i <= 6; i++) {
                double t = i / 6.0;
                point(-0.28, 0.75 + 0.65 * t, type, shown);
                point(0.28, 0.75 + 0.65 * t, type, shown);
                point(-0.42, 0.8 + 0.6 * t, type, shown);
                point(0.42, 0.8 + 0.6 * t, type, shown);
                point(-0.12, 0.75 * t, type, shown);
                point(0.12, 0.75 * t, type, shown);
            }
            return false;
        }

        /** One point of the outline, {@code across} blocks to the right and {@code up} blocks up, shown {@code shown} of the time. */
        private void point(double across, double up, ParticleOptions type, float shown) {
            if (random.nextFloat() < shown) particle(type, feet.add(right.scale(across)).add(0, up, 0), Vec3.ZERO);
        }
    }
}
