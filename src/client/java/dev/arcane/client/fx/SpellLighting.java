package dev.arcane.client.fx;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.NebulonRendering;
import de.nexusrealms.nebulon.api.render.effect.ArcChain;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.light.client.NebulonLights;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.ArcaneSpells;
import dev.arcane.network.SpellEffectPayload;
import dev.arcane.registry.ModItems;
import dev.arcane.spell.SpellTuning;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Nebulon lights, rune circles and arcs for each spell phase.
 *
 * <p>Owns and expires every library handle: no permanent lights are left behind by a spell.
 *
 * <p>Nebulon is a beta library driving the GPU directly, so every call into it is {@linkplain #guarded guarded}.
 * The first failure switches lighting off and spells carry on with particles alone; an exception escaping the
 * packet handler would otherwise disconnect the player.
 */
public final class SpellLighting {
    private static final ColorRgba VIOLET = ColorRgba.rgba(0xAD55FFFF);
    private static final ColorRgba CYAN = ColorRgba.rgba(0x7EE8FFFF);
    private static final ColorRgba PINK = ColorRgba.rgba(0xF3B5FFFF);
    private static final ColorRgba ICE = ColorRgba.rgba(0xCFF6FFFF);
    private static final Vec3 UP = new Vec3(0, 1, 0);
    /** Busy scenes evict the oldest glow rather than growing without bound. */
    private static final int MAX_GLOWS = 48;
    private static final long STAFF_GLOW_KEY = Long.MIN_VALUE;
    private static final int WARD_RINGS = 3;
    private static final double RENDER_DISTANCE = SpellTuning.EFFECT_RANGE;

    private static final Map<Long, Glow> GLOWS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static long ticks;
    /** One-off flashes count down from -1, clear of {@link #key} (never negative) and {@link #STAFF_GLOW_KEY}. */
    private static long nextFlashKey = -1;
    /** Set by the first Nebulon failure; lighting then stays off until the game restarts. */
    private static boolean disabled;

    private SpellLighting() {
    }

    public static void play(SpellEffectPayload effect) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return;
        guarded(() -> {
            ensureWorld(level);
            long key = key(effect.kind(), effect.sourceId());
            switch (effect.kind()) {
                case CHARGE -> charge(level, key, effect);
                case BOLT -> bolt(level, key, effect);
                case IMPACT -> impact(level, key, effect);
                case CHAIN -> chain(level, effect);
                case BLINK -> blink(level, effect);
                case WARD -> ward(level, key, effect);
                case WARD_HIT -> flash(effect.position(), 3.5f, 1.6f, PINK, 8);
                case WARD_END -> wardEnd(effect);
                case NOVA -> nova(level, effect);
            }
        });
    }

    public static void tick(Minecraft client) {
        guarded(() -> tickGlows(client));
    }

    public static void clear() {
        guarded(SpellLighting::closeAll);
        GLOWS.clear();
        world = null;
        ticks = 0;
    }

    private static void tickGlows(Minecraft client) {
        ensureWorld(client.level);
        ++ticks;
        LocalPlayer player = client.player;
        if (player != null && (player.getMainHandItem().is(ModItems.ARCANE_STAFF) || player.getOffhandItem().is(ModItems.ARCANE_STAFF))) {
            glow(STAFF_GLOW_KEY, player.getEyePosition().add(player.getLookAngle().scale(0.5)), 3.5f, 0.55f, VIOLET, 2, false);
        }
        Iterator<Glow> iterator = GLOWS.values().iterator();
        while (iterator.hasNext()) {
            Glow glow = iterator.next();
            if (ticks >= glow.expires || !glow.light.isAlive()) {
                glow.close();
                iterator.remove();
                continue;
            }
            if (!glow.wardRings.isEmpty()) followWard(glow);
            if (glow.fadeTicks > 0) {
                float fade = (glow.expires - ticks) / (float) glow.fadeTicks;
                glow.light.update(value -> value.brightness(glow.brightness * fade * fade));
            }
        }
    }

    private static void charge(ClientLevel level, long key, SpellEffectPayload effect) {
        Glow glow = glow(key, effect.position(), 3 + effect.power() * 4, 0.6f + effect.power() * 1.1f, VIOLET, 5, false);
        MagicCircle circle = new MagicCircle(effect.position(), effect.direction(), 0.45 + effect.power() * 0.35,
                0.016f, VIOLET, 3, effect.sourceId(), 0.25f, 128);
        if (glow.circle == null) glow.circle = NebulonRendering.effects().spawn(level, circle);
        else glow.circle.set(circle);
    }

    private static void bolt(ClientLevel level, long key, SpellEffectPayload effect) {
        Vec3 tip = effect.position().add(effect.direction());
        glow(key, tip, 5, 1.5f, CYAN, 4, false);
        if (effect.direction().lengthSqr() > 0.001) {
            NebulonRendering.effects().spawn(level, ArcChain.between(effect.position(), tip, CYAN), 3);
        }
    }

    private static void impact(ClientLevel level, long key, SpellEffectPayload effect) {
        Glow bolt = GLOWS.remove(key(SpellEffectPayload.Kind.BOLT, effect.sourceId()));
        if (bolt != null) bolt.close();
        float power = effect.power();
        Vec3 origin = effect.position().subtract(effect.direction().scale(0.35));
        glow(key, origin.add(0, 0.5, 0), 9 + power * 7, 3.0f, VIOLET, 26, true);
        NebulonRendering.effects().spawn(level,
                new MagicCircle(origin, effect.direction(), 2 + power * 3, 0.035f, CYAN, 4, effect.sourceId(), -0.3f, RENDER_DISTANCE), 18);
        // Eight radial arcs, alternating heights so they read as a crackling dome.
        double reach = 2 + power * 3;
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Vec3 end = origin.add(Math.cos(angle) * reach, 0.8 + (i % 3), Math.sin(angle) * reach);
            NebulonRendering.effects().spawn(level, ArcChain.between(origin, end, VIOLET), 8);
        }
    }

    /** A cyan arc along one leap, with a fainter violet one flowing the other way, and a flash on the target. */
    private static void chain(ClientLevel level, SpellEffectPayload effect) {
        Vec3 to = effect.position().add(effect.direction());
        // Nebulon rejects a chain whose ends coincide.
        if (effect.direction().lengthSqr() > 0.001) {
            NebulonRendering.effects().spawn(level, ArcChain.between(effect.position(), to, CYAN), 8);
            NebulonRendering.effects().spawn(level,
                    new ArcChain(List.of(effect.position(), to), VIOLET, 0.04f, 0.2f, -0.8f, RENDER_DISTANCE), 6);
        }
        flash(to, 6, 2.2f * Math.max(0.4f, effect.power()), CYAN, 10);
    }

    /** Rune circles on the ground where the caster left and where they arrived. */
    private static void blink(ClientLevel level, SpellEffectPayload effect) {
        Vec3 from = effect.position(), to = from.add(effect.direction());
        NebulonRendering.effects().spawn(level, new MagicCircle(from.add(0, 0.05, 0), UP, 1.0, 0.03f, VIOLET, 3,
                effect.sourceId(), 0.8f, RENDER_DISTANCE), 16);
        NebulonRendering.effects().spawn(level, new MagicCircle(to.add(0, 0.05, 0), UP, 1.0, 0.03f, PINK, 3,
                effect.sourceId() + 1L, -0.8f, RENDER_DISTANCE), 16);
        flash(from.add(0, 1, 0), 5, 1.8f, VIOLET, 12);
        flash(to.add(0, 1, 0), 6, 2.4f, PINK, 14);
    }

    /** Three rune rings around the warded entity, kept on it by {@link #followWard}. */
    private static void ward(ClientLevel level, long key, SpellEffectPayload effect) {
        Glow previous = GLOWS.remove(key);
        if (previous != null) previous.close();
        int entityId = effect.sourceId();
        boolean inside = seenFromInside(entityId);
        Glow glow = glow(key, effect.position(), inside ? 3 : 5, inside ? 0.35f : 1.1f, VIOLET, effect.age(), false);
        glow.wardEntity = entityId;
        Entity entity = level.getEntity(entityId);
        double feetY = entity != null ? entity.getY() : effect.position().y - 0.9;
        List<EffectHandle<MagicCircle>> rings = new ArrayList<>();
        for (int i = 0; i < WARD_RINGS; i++) {
            rings.add(NebulonRendering.effects().spawn(level, wardRing(effect.position(), feetY, i, entityId, inside)));
        }
        glow.wardRings = rings;
    }

    private static void wardEnd(SpellEffectPayload effect) {
        Glow ward = GLOWS.remove(key(SpellEffectPayload.Kind.WARD, effect.sourceId()));
        if (ward != null) ward.close();
        if (effect.power() >= 1) flash(effect.position(), 7, 3.0f, PINK, 14);
    }

    /** Moves a ward's light and rings to wherever its entity is now; the ward ends if the entity is gone. */
    private static void followWard(Glow glow) {
        Entity entity = world.getEntity(glow.wardEntity);
        if (entity == null) {
            glow.expires = ticks;
            return;
        }
        Vec3 center = entity.getBoundingBox().getCenter();
        // Re-checked every tick, so toggling the camera (F5) switches between the two looks.
        boolean inside = seenFromInside(glow.wardEntity);
        glow.light.update(light -> light.at(center).radius(inside ? 3 : 5).brightness(inside ? 0.35f : 1.1f));
        for (int i = 0; i < glow.wardRings.size(); i++) {
            glow.wardRings.get(i).set(wardRing(center, entity.getY(), i, glow.wardEntity, inside));
        }
    }

    /**
     * Ring {@code i} of a ward. Seen from outside: tilted 60° and slowly precessing, a third of a turn apart, like a
     * gyroscope. Seen from the caster's own eyes, full-size rings would sweep across the whole screen, so they lie
     * flat at the caster's feet instead: visible when looking down, never in the way.
     */
    private static MagicCircle wardRing(Vec3 center, double feetY, int i, int entityId, boolean inside) {
        ColorRgba color = i == 1 ? CYAN : VIOLET;
        long seed = entityId * 3L + i;
        if (inside) {
            Vec3 feet = new Vec3(center.x, feetY + 0.05 + i * 0.02, center.z);
            return new MagicCircle(feet, UP, 0.8 + i * 0.45, 0.025f, color, 2, seed, i % 2 == 0 ? 0.5f : -0.5f, RENDER_DISTANCE);
        }
        double angle = ticks * 0.06 + i * Math.PI * 2 / WARD_RINGS;
        Vec3 normal = new Vec3(Math.cos(angle) * 0.87, 0.5, Math.sin(angle) * 0.87);
        return new MagicCircle(center, normal, SpellTuning.WARD_RADIUS * 0.92, 0.025f, color, 2, seed, 0.5f, RENDER_DISTANCE);
    }

    /** The local player's own ward, viewed in first person. */
    private static boolean seenFromInside(int entityId) {
        Minecraft client = Minecraft.getInstance();
        return client.player != null && client.player.getId() == entityId && client.options.getCameraType().isFirstPerson();
    }

    /**
     * A rune circle across the ground and a cold flash. (Nebulon's SnowStorm was tried here: from inside it the
     * caster sees a white-out, so the snow is left to particles.)
     */
    private static void nova(ClientLevel level, SpellEffectPayload effect) {
        Vec3 feet = effect.position();
        NebulonRendering.effects().spawn(level, new MagicCircle(feet.add(0, 0.05, 0), UP, SpellTuning.NOVA_RADIUS, 0.05f, ICE, 4,
                effect.sourceId(), 0.6f, RENDER_DISTANCE), 20);
        flash(feet.add(0, 1, 0), 10, 3.0f, ICE, 22);
    }

    /** A one-off light that fades out over {@code lifetime} ticks. */
    private static void flash(Vec3 position, float radius, float brightness, ColorRgba color, int lifetime) {
        glow(nextFlashKey--, position, radius, brightness, color, lifetime, true);
    }

    /** Creates or refreshes the light for {@code key}; it lives {@code lifetime} ticks unless refreshed again. */
    private static Glow glow(long key, Vec3 position, float radius, float brightness, ColorRgba color, int lifetime, boolean fade) {
        var description = NebulonLights.point(position).color(color).radius(radius).brightness(brightness)
                .maxRenderDistance(RENDER_DISTANCE).build();
        Glow glow = GLOWS.get(key);
        if (glow == null) {
            if (GLOWS.size() >= MAX_GLOWS) {
                var oldest = GLOWS.values().iterator();
                oldest.next().close();
                oldest.remove();
            }
            glow = new Glow(NebulonLights.lights().add(world, description));
            GLOWS.put(key, glow);
        } else {
            glow.light.set(description);
        }
        glow.expires = ticks + lifetime;
        glow.fadeTicks = fade ? lifetime : 0;
        glow.brightness = brightness;
        return glow;
    }

    private static long key(SpellEffectPayload.Kind kind, int sourceId) {
        return ((long) kind.ordinal() << 32) | Integer.toUnsignedLong(sourceId);
    }

    private static void ensureWorld(ClientLevel level) {
        if (world != level) {
            closeAll();
            world = level;
            ticks = 0;
        }
    }

    private static void closeAll() {
        GLOWS.values().forEach(Glow::close);
        GLOWS.clear();
    }

    /** Runs Nebulon work. The first failure is logged once and turns lighting off for the rest of the session. */
    private static void guarded(Runnable action) {
        if (disabled) return;
        try {
            action.run();
        } catch (RuntimeException | LinkageError e) { // LinkageError: an installed Nebulon with a different API
            disabled = true;
            ArcaneSpells.LOGGER.error("Nebulon effects failed; spells will use particles only until the game restarts", e);
            for (Glow glow : GLOWS.values()) {
                try {
                    glow.close();
                } catch (RuntimeException | LinkageError ignored) {
                    // Nebulon is already failing: release what still can be released.
                }
            }
            GLOWS.clear();
        }
    }

    private static final class Glow {
        final PointLightHandle light;
        EffectHandle<MagicCircle> circle;
        /** A ward's rune rings, re-aimed every tick around entity {@link #wardEntity}. Empty for other glows. */
        List<EffectHandle<MagicCircle>> wardRings = List.of();
        int wardEntity;
        long expires;
        int fadeTicks;
        float brightness;

        Glow(PointLightHandle light) {
            this.light = light;
        }

        void close() {
            light.close();
            if (circle != null) circle.remove();
            wardRings.forEach(EffectHandle::remove);
        }
    }
}
