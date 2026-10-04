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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
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
    /** Busy scenes evict the oldest glow rather than growing without bound. */
    private static final int MAX_GLOWS = 48;
    private static final long STAFF_GLOW_KEY = Long.MIN_VALUE;
    private static final double RENDER_DISTANCE = SpellTuning.EFFECT_RANGE;

    private static final Map<Long, Glow> GLOWS = new LinkedHashMap<>();
    private static ClientLevel world;
    private static long ticks;
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
            } else if (glow.fadeTicks > 0) {
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
        long expires;
        int fadeTicks;
        float brightness;

        Glow(PointLightHandle light) {
            this.light = light;
        }

        void close() {
            light.close();
            if (circle != null) circle.remove();
        }
    }
}
