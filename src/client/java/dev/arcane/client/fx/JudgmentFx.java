package dev.arcane.client.fx;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.NebulonRendering;
import de.nexusrealms.nebulon.api.render.WorldEffect;
import de.nexusrealms.nebulon.api.render.effect.BillboardSprite;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.effect.SdfPlane;
import de.nexusrealms.nebulon.api.render.light.BeamLight;
import de.nexusrealms.nebulon.api.render.light.PointLight;
import de.nexusrealms.nebulon.api.render.light.client.BeamLightHandle;
import de.nexusrealms.nebulon.api.render.light.client.NebulonLights;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Celestial Judgment as the client sees it. The server sends one message per phase; everything between is animated
 * here, per strike:
 * <ul>
 *   <li>Channel: a circle at the caster's feet, and the mark on the ground growing to the crater's size as the channel
 *       fills (red where it can't strike), flaring white once complete.</li>
 *   <li>Gather: storm clouds swirl in around a great rune circle in the sky, three circles form on the ground, motes
 *       rise into the sky, and the view darkens.</li>
 *   <li>Beam: a pillar of light slams down and bores into the ground, flinging sparks and debris; the view shakes.</li>
 *   <li>Detonation: a flash, a shockwave racing out across the ground, an explosion and a smoke column, the hardest
 *       shake; then the sky clears, leaving a glowing, smoking crater.</li>
 * </ul>
 *
 * <p>Nebulon work goes through {@link SpellLighting#guarded}, so a Nebulon failure leaves the particles and screen
 * effects running. Particles follow the Particles video setting; shake and flash follow {@link ScreenEffects}.
 *
 * <p>Nebulon's {@code VolumetricFog} would make a natural storm cloud, but in 0.1.2 it softens the whole frame while it
 * is on screen and is heavy on the GPU, so the clouds are particles. Its additive blend also ignores alpha, so a
 * {@link MagicCircle} adds most of its colour across its whole disc: in daylight a bright one is a flat white plate.
 * The circles' shape therefore comes from crisp {@link SdfPlane} rings and lines, with dim magic circles inside them
 * only for the runes.
 */
public final class JudgmentFx {
    /**
     * The only billboard texture. Nebulon 0.1.2's billboard renderer draws one batch per texture and can stall the
     * game waiting on its own GPU buffers when a frame needs more than a couple of batches, so every billboard here
     * shares this one; glows are done with lights and flash particles instead.
     */
    private static final Identifier BEAM_TEXTURE = ArcaneSpells.id("textures/effect/judgment_beam.png");
    /** Frames side by side in the beam texture, played at {@link #BEAM_FPS}. */
    private static final int BEAM_FRAMES = 8;
    private static final float BEAM_FPS = 24;
    private static final ColorRgba GOLD = ColorRgba.rgba(0xFFD27AFF);
    private static final ColorRgba PALE_GOLD = ColorRgba.rgba(0xFFF0C8FF);
    private static final ColorRgba ICE = ColorRgba.rgba(0xBDF3FFFF);
    private static final ColorRgba EMBER = ColorRgba.rgba(0xFF6A1FFF);
    private static final ColorRgba RED = ColorRgba.rgba(0xFF4A4AFF);
    /** Vanilla's firework flash, tinted: a big, brief, camera-facing burst of light. */
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE8B0);
    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);
    private static final double RENDER_DISTANCE = SpellTuning.EFFECT_RANGE;
    /** Strikes this far from the camera spawn no particles; their light, beam and circles still show. */
    private static final double PARTICLE_RANGE = 192;
    /** How long the crater smoulders after the detonation. */
    private static final int AFTERMATH_TICKS = 200;
    private static final double CRATER = SpellTuning.JUDGMENT_CRATER_RADIUS;

    private static final Map<Integer, Strike> STRIKES = new HashMap<>();
    private static final Map<Integer, Channel> CHANNELS = new HashMap<>();
    private static ClientLevel world;
    private static long ticks;

    private JudgmentFx() {
    }

    public static void register() {
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_CHANNEL, (effect, level) -> {
            ensureWorld(level);
            CHANNELS.computeIfAbsent(effect.sourceId(), Channel::new).update(effect);
        });
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_MARK, (effect, level) -> strike(effect, level).mark(effect));
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_BEAM, (effect, level) -> strike(effect, level).beam(effect));
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_BLAST, (effect, level) -> strike(effect, level).blast());
    }

    public static void tick(Minecraft client) {
        ensureWorld(client.level);
        ticks++;
        for (Iterator<Strike> it = STRIKES.values().iterator(); it.hasNext(); ) {
            Strike strike = it.next();
            if (strike.tick()) {
                strike.release();
                it.remove();
            }
        }
        for (Iterator<Channel> it = CHANNELS.values().iterator(); it.hasNext(); ) {
            Channel channel = it.next();
            if (ticks - channel.lastUpdate > 4) {
                channel.release();
                it.remove();
            }
        }
    }

    public static void clear() {
        STRIKES.values().forEach(Strike::release);
        STRIKES.clear();
        CHANNELS.values().forEach(Channel::release);
        CHANNELS.clear();
        world = null;
    }

    private static Strike strike(SpellEffect effect, ClientLevel level) {
        ensureWorld(level);
        return STRIKES.computeIfAbsent(effect.sourceId(), id -> new Strike(id, effect));
    }

    /** Everything belongs to the world it started in; a dimension change drops it. */
    private static void ensureWorld(ClientLevel level) {
        if (world != level) {
            clear();
            world = level;
        }
    }

    /** 1 within 48 blocks of the camera, fading to 0 at 192: how hard the screen effects hit. */
    private static float proximity(Vec3 at) {
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().position().distanceTo(at);
        return (float) Mth.clamp(1 - (distance - 48) / 144, 0, 1);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    private static ColorRgba scaled(ColorRgba color, float f) {
        f = Mth.clamp(f, 0, 1);
        return new ColorRgba(color.red() * f, color.green() * f, color.blue() * f, color.alpha() * f);
    }

    // Nebulon helpers: each creates the handle on first use, then updates it in place.

    private static <E extends WorldEffect> EffectHandle<E> show(EffectHandle<E> handle, E effect) {
        if (handle != null && handle.isAlive()) {
            handle.set(effect);
            return handle;
        }
        return NebulonRendering.effects().spawn(world, effect);
    }

    private static <E extends WorldEffect> EffectHandle<E> hide(EffectHandle<E> handle) {
        if (handle != null) handle.remove();
        return null;
    }

    private static MagicCircle circle(Vec3 center, Vec3 normal, double radius, float lineWidth, ColorRgba color, int layers, long seed, float spin) {
        return new MagicCircle(center, normal, Math.max(0.05, radius), lineWidth, color, layers, seed, spin, RENDER_DISTANCE);
    }

    private static BillboardSprite beamSprite(Vec3 center, float width, float height, ColorRgba color) {
        return BillboardSprite.builder(BEAM_TEXTURE).at(center).size(Math.max(0.05f, width), height).color(color)
                .blend(BillboardSprite.Blend.ADDITIVE).facing(BillboardSprite.Facing.Y_AXIS)
                .flipbook(BEAM_FRAMES, 1, BEAM_FPS).maxRenderDistance(RENDER_DISTANCE).build();
    }

    private static PointLightHandle light(PointLightHandle handle, Vec3 at, float radius, float brightness, ColorRgba color) {
        PointLight light = NebulonLights.point(at).color(color).radius(Mth.clamp(radius, 0.5f, 128)).brightness(Mth.clamp(brightness, 0, 64))
                .maxRenderDistance(RENDER_DISTANCE).build();
        if (handle != null && handle.isAlive()) {
            handle.set(light);
            return handle;
        }
        return NebulonLights.lights().add(world, light);
    }

    private static PointLightHandle unlight(PointLightHandle handle) {
        if (handle != null) handle.close();
        return null;
    }

    // Particle helpers, following the Particles setting like SpellParticles.

    private static boolean particlesVisible(Vec3 at) {
        Minecraft client = Minecraft.getInstance();
        return client.options.particles().get() != ParticleStatus.MINIMAL
                && client.gameRenderer.getMainCamera().position().distanceToSqr(at) < PARTICLE_RANGE * PARTICLE_RANGE;
    }

    private static int scaled(int count) {
        return Minecraft.getInstance().options.particles().get() == ParticleStatus.DECREASED ? (count + 1) / 2 : count;
    }

    private static void particle(ParticleOptions type, Vec3 at, Vec3 velocity) {
        world.addParticle(type, true, false, at.x, at.y, at.z, velocity.x, velocity.y, velocity.z);
    }

    private static Vec3 onCircle(Vec3 center, double radius, double angle, double up) {
        return center.add(Math.cos(angle) * radius, up, Math.sin(angle) * radius);
    }

    private static final class Strike {
        final int id;
        final Vec3 impact;
        final Vec3 sky;
        final RandomSource random = RandomSource.create();
        int gatherTicks = SpellTuning.JUDGMENT_GATHER_TICKS;
        int beamTicks = SpellTuning.JUDGMENT_BEAM_TICKS;
        int age;
        int beamAge = -1;
        int blastAge = -1;
        EffectHandle<MagicCircle> groundRunes, skyRunes;
        final Planes planes = new Planes();
        EffectHandle<BillboardSprite> beamCore, beamBody, beamHalo;
        PointLightHandle groundLight, skyLight, blastLight, emberLight;
        BeamLightHandle beamLight;

        Strike(int id, SpellEffect first) {
            this.id = id;
            this.impact = first.position();
            // A strike first seen at its detonation has no sky offset; put the fading sky where it would have been.
            Vec3 offset = first.direction().y > 2 ? first.direction() : new Vec3(0, SpellTuning.JUDGMENT_SKY_HEIGHT, 0);
            this.sky = impact.add(offset);
        }

        void mark(SpellEffect effect) {
            gatherTicks = Math.max(1, effect.age());
        }

        void beam(SpellEffect effect) {
            if (beamAge >= 0) return;
            beamAge = 0;
            beamTicks = Math.max(1, effect.age());
            age = Math.max(age, gatherTicks);
            float near = proximity(impact);
            ScreenEffects.flash(0.5f * near);
            ScreenEffects.shake(0.5f * near);
        }

        void blast() {
            if (blastAge >= 0) return;
            blastAge = 0;
            float near = proximity(impact);
            ScreenEffects.flash(near);
            ScreenEffects.shake(near);
            if (particlesVisible(impact)) explosionParticles();
        }

        /** Advances a tick; returns true once the strike is over, or has lost track of its later phases. */
        boolean tick() {
            age++;
            if (blastAge >= 0) blastAge++;
            else if (beamAge >= 0) beamAge++;
            SpellLighting.guarded(this::animate);
            if (particlesVisible(impact)) particles();
            screen(proximity(impact));
            if (blastAge >= 0) return blastAge > AFTERMATH_TICKS;
            return age > gatherTicks + beamTicks + 40;
        }

        private float gathered() {
            return Mth.clamp(age / (float) gatherTicks, 0, 1);
        }

        private void animate() {
            float g = gathered();
            boolean beaming = beamAge >= 0 && blastAge < 0;
            boolean blasted = blastAge >= 0;
            planes.begin();

            // The ground circle, drawn ring by ring as the strike gathers, burning white-hot under the beam.
            if (!blasted) {
                float heat = beaming ? 1 : 0.55f + 0.45f * g;
                Vec3 ground = impact.add(0, 0.06, 0);
                double outer = CRATER * ease(age / 18f);
                groundRunes = show(groundRunes, circle(ground, UP, outer * 0.95, 0.06f, scaled(GOLD, 0.3f * heat), 4, id, 0.35f));
                seal(ground, UP, outer, 0.22f, age * 0.012f, scaled(GOLD, heat), heat);
                if (age >= 8) planes.ring(ground, UP, 7.5 * ease((age - 8) / 14f), 0.16f, scaled(ICE, heat));
                if (age >= 16) planes.ring(ground, UP, 3.6 * ease((age - 16) / 12f), 0.14f, scaled(ColorRgba.WHITE, heat));
                groundLight = light(groundLight, impact.add(0, 1.5, 0), beaming ? 26 : 6 + 10 * g, beaming ? 5 : 0.4f + 1.8f * g, PALE_GOLD);
            } else {
                groundRunes = hide(groundRunes);
                groundLight = unlight(groundLight);
            }

            // The sky: the same seal, larger, turning the other way at the heart of the storm (see stormParticles). It
            // fades after the detonation.
            float skyFade = blasted ? 1 - blastAge / 30f : 1;
            if (skyFade > 0) {
                double outer = 22 * ease(age / 30f);
                float bright = skyFade * (0.6f + 0.4f * g);
                skyRunes = show(skyRunes, circle(sky, DOWN, outer * 0.95, 0.12f, scaled(GOLD, 0.3f * bright), 5, id + 3L, 0.12f));
                seal(sky, DOWN, outer, 0.5f, -age * 0.008f, scaled(GOLD, bright), bright);
                if (age >= 10) planes.ring(sky.subtract(0, 0.3, 0), DOWN, 10 * ease((age - 10) / 20f), 0.4f, scaled(ColorRgba.WHITE, skyFade));
                skyLight = light(skyLight, sky.subtract(0, 3, 0), 28, (0.5f + 1.5f * g) * skyFade, PALE_GOLD);
            } else {
                skyRunes = hide(skyRunes);
                skyLight = unlight(skyLight);
            }

            // The beam: three nested billboards, slamming down then boring into the shaft, with a flare at its foot.
            if (beaming) {
                float slam = beamAge < 3 ? 0.25f + 0.75f * beamAge / 3f : 1 + 0.06f * Mth.sin(age * 1.7f);
                double bottom = impact.y - SpellTuning.JUDGMENT_SHAFT_DEPTH * Math.min(1, beamAge / (float) Math.max(1, beamTicks - 4));
                double top = sky.y + 1;
                Vec3 middle = new Vec3(impact.x, (top + bottom) / 2, impact.z);
                float height = (float) (top - bottom);
                beamHalo = show(beamHalo, beamSprite(middle, 15 * slam, height, scaled(GOLD, 0.4f)));
                beamBody = show(beamBody, beamSprite(middle, 6.5f * slam, height, PALE_GOLD));
                beamCore = show(beamCore, beamSprite(middle, 2.4f * slam, height, ColorRgba.WHITE));
                planes.glow(impact.add(0, 1.2, 0), 4.5f + 0.6f * Mth.sin(age * 2.1f), PALE_GOLD);
                BeamLight light = NebulonLights.beam(impact.subtract(0, 2, 0), new Vec3(impact.x, Math.min(top, impact.y + 200), impact.z))
                        .color(PALE_GOLD).radius(10).brightness(4.5f).maxRenderDistance(RENDER_DISTANCE).build();
                if (beamLight != null && beamLight.isAlive()) beamLight.set(light);
                else beamLight = NebulonLights.lights().add(world, light);
            } else {
                beamCore = hide(beamCore);
                beamBody = hide(beamBody);
                beamHalo = hide(beamHalo);
                if (beamLight != null) beamLight.close();
                beamLight = null;
            }

            // The detonation: two shockwave rings race outwards, thinning as they go, a blinding light fades, the crater
            // glows.
            if (blasted) {
                if (blastAge <= 18) {
                    float size = blastAge < 4 ? 4 + 12 * blastAge / 4f : 16 + (blastAge - 4) * 0.4f;
                    float fade = blastAge < 4 ? 1 : 1 - (blastAge - 4) / 14f;
                    planes.glow(impact.add(0, 3, 0), size, scaled(PALE_GOLD, fade));
                }
                if (blastAge <= 16) {
                    float fade = 1 - blastAge / 16f;
                    planes.ring(impact.add(0, 0.2, 0), UP, 3 + 27 * ease(blastAge / 14f), 1.6f * fade + 0.1f, scaled(ColorRgba.WHITE, fade));
                    planes.ring(impact.add(0, 0.3, 0), UP, 2 + 18 * ease(blastAge / 16f), 0.8f * fade + 0.1f, scaled(GOLD, fade));
                }
                float flash = 1 - blastAge / 30f;
                blastLight = flash > 0 ? light(blastLight, impact.add(0, 4, 0), 56, 7 * flash * flash, PALE_GOLD) : unlight(blastLight);
                emberLight = light(emberLight, impact.subtract(0, 4, 0), 18, 2.2f * (1 - blastAge / (float) AFTERMATH_TICKS), EMBER);
            }
            planes.end();
        }

        /**
         * A seal of radius {@code outer}: a bold outer ring with a fine one inside it, framing a hexagram of two
         * triangles turned by {@code turn} radians. Line widths are in blocks; {@code bright} dims the fine lines.
         */
        private void seal(Vec3 center, Vec3 normal, double outer, float line, float turn, ColorRgba color, float bright) {
            if (outer < 0.5) return;
            planes.ring(center, normal, outer, line, color);
            planes.ring(center, normal, outer * 0.9, line * 0.45f, scaled(color, 0.8f));
            planes.polygon(center, normal, outer * 0.86, 3, turn, line * 0.6f, scaled(color, 0.85f * Math.max(0.4f, bright)));
            planes.polygon(center, normal, outer * 0.86, 3, turn + Mth.PI / 3, line * 0.6f, scaled(color, 0.85f * Math.max(0.4f, bright)));
        }

        private void particles() {
            if (blastAge < 0) stormParticles();
            if (blastAge >= 0) {
                aftermathParticles();
            } else if (beamAge >= 0) {
                beamParticles();
            } else {
                gatherParticles();
            }
        }

        /**
         * Storm clouds wheeling around the sky circle, thickening as the strike gathers: big dark puffs drifting
         * sideways around it, so from below they read as a vortex.
         */
        private void stormParticles() {
            int count = scaled(beamAge >= 0 ? 3 : 1 + Math.round(3 * gathered()));
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI, radius = 12 + random.nextDouble() * 26;
                Vec3 at = onCircle(sky, radius, angle, 2 + random.nextDouble() * 5);
                // Tangential, so the cloud turns around the circle.
                Vec3 swirl = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.12 + random.nextDouble() * 0.1);
                // Signal smoke: the largest vanilla smoke, so it still reads as cloud from the ground.
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, swirl.add(0, -0.02, 0));
            }
        }

        /** Motes streaming up from the outer circle into the sky, glints falling from it, dust drawn inwards. */
        private void gatherParticles() {
            double rim = CRATER * ease(age / 18f);
            for (int i = 0, n = scaled(5); i < n; i++) {
                Vec3 at = onCircle(impact, rim * (0.9 + random.nextDouble() * 0.1), random.nextDouble() * Mth.TWO_PI, 0.2);
                particle(i % 2 == 0 ? ModParticles.GOLD : ParticleTypes.END_ROD, at, new Vec3(0, 0.25 + random.nextDouble() * 0.35, 0));
            }
            for (int i = 0, n = scaled(2); i < n; i++) {
                Vec3 at = onCircle(sky, Math.sqrt(random.nextDouble()) * 20, random.nextDouble() * Mth.TWO_PI, -1);
                particle(ModParticles.GOLD, at, new Vec3(0, -0.25, 0));
            }
            if (age > gatherTicks - 25) {
                for (int i = 0, n = scaled(3); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 at = onCircle(impact, CRATER + 2, angle, 0.3);
                    particle(ParticleTypes.CLOUD, at, new Vec3(-Math.cos(angle) * 0.25, 0.02, -Math.sin(angle) * 0.25));
                }
            }
        }

        /** A flare at the beam's foot, sparks and debris flung from it, light streaming down it, smoke rolling out. */
        private void beamParticles() {
            Vec3 foot = impact.add(0, 0.5, 0);
            if (beamAge % 3 == 0) particle(FLARE, impact.add(0, 1.5, 0), Vec3.ZERO);
            for (int i = 0, n = scaled(10); i < n; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI, speed = 0.5 + random.nextDouble() * 0.6;
                particle(ModParticles.GOLD, foot, new Vec3(Math.cos(angle) * speed, 0.15 + random.nextDouble() * 0.45, Math.sin(angle) * speed));
            }
            for (int i = 0, n = scaled(4); i < n; i++) {
                Vec3 at = onCircle(impact, random.nextDouble() * 3, random.nextDouble() * Mth.TWO_PI, 5 + random.nextDouble() * 55);
                particle(ParticleTypes.END_ROD, at, new Vec3(0, -1.5, 0));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = scaled(6); i < n; i++) {
                particle(debris, foot, new Vec3(jitter(1), 0.5 + random.nextDouble() * 0.7, jitter(1)));
            }
            for (int i = 0, n = scaled(3); i < n; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                Vec3 at = onCircle(impact, 4 + random.nextDouble() * 3, angle, 0.3);
                particle(ParticleTypes.LARGE_SMOKE, at, new Vec3(Math.cos(angle) * 0.15, 0.05, Math.sin(angle) * 0.15));
            }
            particle(ParticleTypes.LAVA, foot, Vec3.ZERO);
        }

        /** The detonation itself: flares, fireballs, a sphere of sparks, a fountain of debris and lava, the first smoke. */
        private void explosionParticles() {
            for (int i = 0; i < 5; i++) particle(FLARE, impact.add(jitter(6), 1 + random.nextDouble() * 4, jitter(6)), Vec3.ZERO);
            explosionBursts(4);
            for (int i = 0, n = scaled(24); i < n; i++) {
                Vec3 direction = randomDirection(0.2);
                particle(ParticleTypes.EXPLOSION, impact.add(direction.scale(2 + random.nextDouble() * 5)), Vec3.ZERO);
            }
            for (int i = 0, n = scaled(180); i < n; i++) {
                particle(ModParticles.GOLD, impact.add(0, 1, 0), randomDirection(0.3).scale(0.6 + random.nextDouble() * 1.2));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = scaled(100); i < n; i++) {
                particle(debris, impact.add(0, 0.5, 0), new Vec3(jitter(1.4), 0.6 + random.nextDouble() * 1.2, jitter(1.4)));
            }
            for (int i = 0, n = scaled(30); i < n; i++) particle(ParticleTypes.LAVA, impact.add(0, 0.5, 0), Vec3.ZERO);
            for (int i = 0, n = scaled(14); i < n; i++) {
                Vec3 at = onCircle(impact, random.nextDouble() * 5, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * 2);
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, new Vec3(jitter(0.05), 0.06 + random.nextDouble() * 0.08, jitter(0.05)));
            }
        }

        /** Fireballs across a dome over the crater. */
        private void explosionBursts(int count) {
            for (int i = 0; i < count; i++) {
                Vec3 at = impact.add(randomDirection(0.4).scale(random.nextDouble() * 8));
                particle(ParticleTypes.EXPLOSION_EMITTER, at, Vec3.ZERO);
            }
        }

        /** The shockwave racing outwards across the ground, the smoke column, then embers rising from the crater. */
        private void aftermathParticles() {
            if (blastAge <= 3) explosionBursts(2);
            if (blastAge <= 12) {
                double radius = 2 + 28 * ease(blastAge / 12f);
                for (int i = 0, n = scaled(28); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    particle(i % 4 == 0 ? ModParticles.GOLD : ParticleTypes.CLOUD, onCircle(impact, radius, angle, 0.3), out.scale(0.4).add(0, 0.05, 0));
                }
            }
            if (blastAge <= 60) {
                for (int i = 0, n = scaled(2); i < n; i++) {
                    Vec3 at = onCircle(impact, random.nextDouble() * 3, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * 4);
                    particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, new Vec3(0, 0.1, 0));
                }
            }
            if (blastAge % 2 == 0) {
                for (int i = 0, n = scaled(2); i < n; i++) {
                    Vec3 at = onCircle(impact, Math.sqrt(random.nextDouble()) * (CRATER - 2), random.nextDouble() * Mth.TWO_PI, -random.nextDouble() * 6);
                    particle(i == 0 ? ParticleTypes.LAVA : ModParticles.GOLD, at, new Vec3(0, 0.05, 0));
                }
            }
            if (blastAge % 4 == 0) {
                Vec3 at = onCircle(impact, Math.sqrt(random.nextDouble()) * (CRATER - 2), random.nextDouble() * Mth.TWO_PI, -random.nextDouble() * 4);
                particle(ParticleTypes.SMOKE, at, new Vec3(0, 0.06, 0));
            }
        }

        /** A random offset between -spread/2 and spread/2. */
        private double jitter(double spread) {
            return (random.nextDouble() - 0.5) * spread;
        }

        private Vec3 randomDirection(double lift) {
            Vec3 direction = new Vec3(random.nextGaussian(), Math.abs(random.nextGaussian()) + lift, random.nextGaussian());
            return direction.lengthSqr() < 1.0e-6 ? UP : direction.normalize();
        }

        /** The block the beam is digging into, for debris: the first solid one under the mark, else stone. */
        private BlockState ground() {
            BlockPos pos = BlockPos.containing(impact);
            for (int i = 0; i < 8; i++, pos = pos.below()) {
                BlockState state = world.getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) return state;
            }
            return Blocks.STONE.defaultBlockState();
        }

        private void screen(float near) {
            if (near <= 0) return;
            if (blastAge < 0) {
                ScreenEffects.tint(0.65f * ease(gathered()) * near);
                if (beamAge >= 0) ScreenEffects.shake((0.32f + 0.3f * beamAge / beamTicks) * near);
                else if (age > gatherTicks - 20) ScreenEffects.shake(0.15f * near);
            } else if (blastAge < 20) {
                ScreenEffects.tint(0.65f * (1 - blastAge / 20f) * near);
            }
        }

        void release() {
            SpellLighting.guarded(() -> {
                planes.clear();
                for (EffectHandle<?> handle : new EffectHandle<?>[] {groundRunes, skyRunes, beamCore, beamBody, beamHalo}) {
                    if (handle != null) handle.remove();
                }
                for (PointLightHandle light : new PointLightHandle[] {groundLight, skyLight, blastLight, emberLight}) {
                    if (light != null) light.close();
                }
                if (beamLight != null) beamLight.close();
            });
        }
    }

    /**
     * One caster channelling: a circle at their feet, and the mark where the strike will land, its rim an arc that
     * closes as the channel fills.
     */
    private static final class Channel {
        final int casterId;
        long lastUpdate;
        EffectHandle<MagicCircle> feet;
        final Planes planes = new Planes();
        PointLightHandle light;

        Channel(int casterId) {
            this.casterId = casterId;
        }

        void update(SpellEffect effect) {
            lastUpdate = ticks;
            boolean valid = effect.direction().lengthSqr() > 0.5;
            float power = Mth.clamp(effect.power(), 0, 1);
            boolean complete = power >= 1;
            float pulse = 0.75f + 0.25f * Mth.sin(effect.age() * 0.8f);
            ColorRgba markColor = !valid ? RED : complete ? scaled(PALE_GOLD, pulse) : GOLD;
            Entity caster = world.getEntity(casterId);
            Vec3 mark = effect.position().add(0, 0.08, 0);
            SpellLighting.guarded(() -> {
                if (caster != null) {
                    // Dim: a magic circle adds most of its colour across its whole disc (see the class comment).
                    feet = show(feet, circle(caster.position().add(0, 0.05, 0), UP, 1.2 + 0.6 * power, 0.04f,
                            scaled(GOLD, 0.25f + 0.25f * power), 3, casterId, 1.2f));
                }
                planes.begin();
                // The faint full circle shows where the crater will be; the bold arc sweeps round it as the channel fills.
                planes.ring(mark, UP, CRATER, 0.08f, scaled(markColor, 0.45f));
                float sweep = complete ? Mth.TWO_PI : Mth.TWO_PI * power;
                if (sweep > 0.01f) planes.arc(mark, UP, CRATER, 0.3f, Mth.HALF_PI, Mth.HALF_PI + sweep, markColor);
                if (complete) planes.ring(mark, UP, CRATER * 0.9, 0.12f, scaled(markColor, 0.7f));
                planes.end();
                light = valid ? light(light, effect.position().add(0, 1, 0), 6, 0.3f + 0.9f * power, complete ? PALE_GOLD : GOLD) : unlight(light);
            });
            if (caster != null && particlesVisible(caster.position())) {
                RandomSource random = world.random;
                for (int i = 0, n = scaled(2); i < n; i++) {
                    Vec3 at = onCircle(caster.position(), 1.2 + 0.6 * power, random.nextDouble() * Mth.TWO_PI, 0.1);
                    particle(ModParticles.GOLD, at, new Vec3(0, 0.08 + 0.12 * power, 0));
                }
            }
        }

        void release() {
            SpellLighting.guarded(() -> {
                if (feet != null) feet.remove();
                planes.clear();
                if (light != null) light.close();
            });
        }
    }

    /**
     * Flat shapes redrawn every tick: {@link #begin}, draw this tick's shapes, then {@link #end}, which removes any left
     * over from last tick. Handles are reused in drawing order, so a steady set of shapes just updates in place.
     */
    private static final class Planes {
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

        /**
         * A glowing ball: three discs facing the camera, each smaller one adding more light, so the centre is
         * brightest. Turned towards the camera each tick.
         */
        void glow(Vec3 center, float radius, ColorRgba color) {
            Vec3 toCamera = Minecraft.getInstance().gameRenderer.getMainCamera().position().subtract(center);
            Vec3 facing = toCamera.lengthSqr() < 1.0e-6 ? UP : toCamera.normalize();
            float[] sizes = {1, 0.7f, 0.4f}, strengths = {0.22f, 0.3f, 0.48f};
            for (int i = 0; i < sizes.length; i++) {
                draw(SdfPlane.circle().at(center.add(facing.scale(i * 0.05))).normal(facing).size(radius * 2 * sizes[i])
                        .color(scaled(color, strengths[i])).maxRenderDistance(RENDER_DISTANCE).build());
            }
        }

        void polygon(Vec3 center, Vec3 normal, double radius, int sides, float turn, float line, ColorRgba color) {
            if (radius <= line) return;
            draw(SdfPlane.builder(SdfPlane.Shape.POLYGON).polygon(sides, turn).at(center).normal(normal).size((float) (radius * 2))
                    .lineWidth(line).color(color).maxRenderDistance(RENDER_DISTANCE).build());
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
