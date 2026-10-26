package dev.arcane.client.fx;

import static dev.leyline.client.fx.Fx.DOWN;
import static dev.leyline.client.fx.Fx.RENDER_DISTANCE;
import static dev.leyline.client.fx.Fx.UP;
import static dev.leyline.client.fx.Fx.circle;
import static dev.leyline.client.fx.Fx.ease;
import static dev.leyline.client.fx.Fx.hide;
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
import de.nexusrealms.nebulon.api.render.effect.BillboardSprite;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.light.BeamLight;
import de.nexusrealms.nebulon.api.render.light.client.BeamLightHandle;
import de.nexusrealms.nebulon.api.render.light.client.NebulonLights;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellTuning;
import dev.leyline.client.fx.Fx;
import dev.leyline.client.fx.NebulonGuard;
import dev.leyline.client.screen.ScreenEffects;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Celestial Judgment as the client sees it. The server sends one message per phase; everything between is animated
 * here, per strike (the channel is {@link ChannelFx}):
 * <ul>
 *   <li>Gather: storm clouds swirl in around a great seal in the sky, three rings form on the ground, motes rise into
 *       the sky, and the view darkens.</li>
 *   <li>Beam: a pillar of light slams down and bores into the ground, splitting it with cracks of gold that race
 *       outwards, flinging sparks and debris; the view shakes.</li>
 *   <li>Detonation: a fireball, a shockwave racing out across the ground, explosions and a smoke column, the hardest
 *       shake; then the sky clears, embers rain down, and the crater is left glowing and smoking, its rim smouldering
 *       longest.</li>
 * </ul>
 */
final class JudgmentFx {
    /** The mod's only billboard texture (see {@link Fx}). */
    private static final Identifier BEAM_TEXTURE = ArcaneSpells.id("textures/effect/judgment_beam.png");
    /** Frames side by side in the beam texture, played at {@link #BEAM_FPS}. */
    private static final int BEAM_FRAMES = 8;
    private static final float BEAM_FPS = 24;
    private static final ColorRgba GOLD = ColorRgba.rgba(0xFFD27AFF);
    private static final ColorRgba PALE_GOLD = ColorRgba.rgba(0xFFF0C8FF);
    private static final ColorRgba ICE = ColorRgba.rgba(0xBDF3FFFF);
    private static final ColorRgba EMBER = ColorRgba.rgba(0xFF6A1FFF);
    /** Vanilla's firework flash, tinted: a big, brief, camera-facing burst of light. */
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE8B0);
    /**
     * Solid motes of gold for the cracks. The soft sparks fade to nothing a few dozen blocks off, where the caster
     * stands, so these carry the line there.
     */
    private static final ParticleOptions CRACK_DUST = new DustParticleOptions(0xFFC840, 2);
    /** How long the crater smoulders after the detonation. */
    private static final int AFTERMATH_TICKS = 300;
    /** Cracks the beam splits the ground with. */
    private static final int CRACKS = 9;
    private static final double CRATER = SpellTuning.JUDGMENT_CRATER_RADIUS;
    /** The beam's half-width, and the unit the rest of the strike's shapes are sized in. */
    private static final float BEAM = (float) SpellTuning.JUDGMENT_BEAM_RADIUS;
    /** The sky seal's radius. */
    private static final double SKY = CRATER * 1.7;
    /** How far the detonation's shockwave races. */
    private static final double SHOCKWAVE = SpellTuning.JUDGMENT_BLAST_RADIUS * 1.8;
    private static final int TINT = 0x0C0620;

    private static final Fx.Tracker<Strike> STRIKES = AstralFx.TRACKERS.create();

    private JudgmentFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_MARK, (effect, level) -> strike(effect, level).mark(effect));
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_BEAM, (effect, level) -> strike(effect, level).beam(effect));
        SpellEffectRenderers.register(ModSpellEffects.JUDGMENT_BLAST, (effect, level) -> strike(effect, level).blast());
    }

    private static Strike strike(SpellEffect effect, ClientLevel level) {
        return STRIKES.get(effect.sourceId(), level, id -> new Strike(id, effect));
    }

    static BillboardSprite beamSprite(Vec3 center, float width, float height, ColorRgba color) {
        return BillboardSprite.builder(BEAM_TEXTURE).at(center).size(Math.max(0.05f, width), height).color(color)
                .blend(BillboardSprite.Blend.ADDITIVE).facing(BillboardSprite.Facing.Y_AXIS)
                .flipbook(BEAM_FRAMES, 1, BEAM_FPS).maxRenderDistance(RENDER_DISTANCE).build();
    }

    private static final class Strike implements Fx.Animation {
        final int id;
        final Vec3 impact;
        final Vec3 sky;
        final RandomSource random = RandomSource.create();
        final Fx.Planes planes = new Fx.Planes();
        int gatherTicks = SpellTuning.JUDGMENT_GATHER_TICKS;
        int beamTicks = SpellTuning.JUDGMENT_BEAM_TICKS;
        int age;
        int beamAge = -1;
        int blastAge = -1;
        EffectHandle<MagicCircle> groundRunes, skyRunes;
        EffectHandle<BillboardSprite> beamCore, beamBody, beamHalo;
        /** Each crack's path over the ground, from the beam's edge out to about the crater's rim, laid when the beam lands. */
        final List<List<Vec3>> cracks = new ArrayList<>();
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
            layCracks();
            float near = proximity(impact);
            ScreenEffects.flash(0.5f * near);
            ScreenEffects.shake(0.5f * near);
        }

        /** Every client splits the ground the same way: the cracks are seeded by the strike. */
        private void layCracks() {
            RandomSource seeded = RandomSource.create(id * 7919L);
            for (int i = 0; i < CRACKS; i++) {
                double angle = (i + seeded.nextDouble() * 0.6) * Mth.TWO_PI / CRACKS;
                double reach = CRATER * (0.8 + seeded.nextDouble() * 0.45);
                List<Vec3> path = new ArrayList<>();
                for (double r = BEAM + 0.5; r < reach; r += 2.5 + seeded.nextDouble() * 2) {
                    angle += (seeded.nextDouble() - 0.5) * 0.2;
                    path.add(onGround(impact.x + Math.cos(angle) * r, impact.z + Math.sin(angle) * r));
                }
                if (path.size() >= 2) cracks.add(path);
            }
        }

        /** Just above the top of the ground at x, z. */
        private static Vec3 onGround(double x, double z) {
            return new Vec3(x, world().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z)) + 0.08, z);
        }

        /** The first {@code fraction} of a path's length, or null while that's too short to draw. */
        private static List<Vec3> partial(List<Vec3> path, float fraction) {
            double total = 0;
            for (int i = 1; i < path.size(); i++) total += path.get(i - 1).distanceTo(path.get(i));
            double left = total * fraction;
            if (left < 0.3) return null;
            List<Vec3> part = new ArrayList<>(List.of(path.getFirst()));
            for (int i = 1; i < path.size(); i++) {
                double step = path.get(i - 1).distanceTo(path.get(i));
                if (step >= left) {
                    part.add(path.get(i - 1).lerp(path.get(i), left / step));
                    return part;
                }
                part.add(path.get(i));
                left -= step;
            }
            return part;
        }

        void blast() {
            if (blastAge >= 0) return;
            blastAge = 0;
            float near = proximity(impact);
            ScreenEffects.flash(near);
            ScreenEffects.shake(near);
            if (particlesVisible(impact)) explosionParticles();
        }

        @Override public boolean tick() {
            age++;
            if (blastAge >= 0) blastAge++;
            else if (beamAge >= 0) beamAge++;
            NebulonGuard.guarded(this::animate);
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

            // The ground seal, drawn ring by ring as the strike gathers, burning white-hot under the beam.
            if (!blasted) {
                float heat = beaming ? 1 : 0.55f + 0.45f * g;
                Vec3 ground = impact.add(0, 0.06, 0);
                double outer = CRATER * ease(age / 18f);
                groundRunes = show(groundRunes, circle(ground, UP, outer * 0.95, 0.06f, scaled(GOLD, 0.3f * heat), 4, id, 0.35f));
                planes.seal(ground, UP, outer, 0.4f, 6, age * 0.012f, scaled(GOLD, heat));
                if (age >= 8) planes.ring(ground, UP, CRATER * 0.62 * ease((age - 8) / 14f), 0.3f, scaled(ICE, heat));
                if (age >= 16) planes.ring(ground, UP, CRATER * 0.3 * ease((age - 16) / 12f), 0.25f, scaled(ColorRgba.WHITE, heat));
                groundLight = light(groundLight, impact.add(0, 2, 0), beaming ? 48 : 10 + 20 * g, beaming ? 6 : 0.6f + 2.4f * g, PALE_GOLD);
            } else {
                groundRunes = hide(groundRunes);
                groundLight = unlight(groundLight);
            }

            // The sky: the same seal, larger, turning the other way at the heart of the storm. It fades after the
            // detonation.
            float skyFade = blasted ? 1 - blastAge / 30f : 1;
            if (skyFade > 0) {
                double outer = SKY * ease(age / 30f);
                float bright = skyFade * (0.6f + 0.4f * g);
                skyRunes = show(skyRunes, circle(sky, DOWN, outer * 0.95, 0.2f, scaled(GOLD, 0.3f * bright), 5, id + 3L, 0.12f));
                planes.seal(sky, DOWN, outer, 0.9f, 6, -age * 0.008f, scaled(GOLD, bright));
                if (age >= 10) planes.ring(sky.subtract(0, 0.3, 0), DOWN, SKY * 0.45 * ease((age - 10) / 20f), 0.7f, scaled(ColorRgba.WHITE, skyFade));
                skyLight = light(skyLight, sky.subtract(0, 4, 0), 48, (0.8f + 2.2f * g) * skyFade, PALE_GOLD);
            } else {
                skyRunes = hide(skyRunes);
                skyLight = unlight(skyLight);
            }

            // The beam: three nested billboards, slamming down then boring into the shaft, with a glow at its foot.
            if (beaming) {
                float slam = beamAge < 3 ? 0.25f + 0.75f * beamAge / 3f : 1 + 0.06f * Mth.sin(age * 1.7f);
                double bottom = Mth.lerp(Math.min(1, beamAge / (float) Math.max(1, beamTicks - 4)), impact.y, world().getMinY());
                double top = sky.y + 1;
                Vec3 middle = new Vec3(impact.x, (top + bottom) / 2, impact.z);
                float height = (float) (top - bottom);
                // The body is the shaft's width; a halo glows far wider, a white-hot core runs down the middle.
                beamHalo = show(beamHalo, beamSprite(middle, BEAM * 4.4f * slam, height, scaled(GOLD, 0.4f)));
                beamBody = show(beamBody, beamSprite(middle, BEAM * 2 * slam, height, PALE_GOLD));
                beamCore = show(beamCore, beamSprite(middle, BEAM * 0.7f * slam, height, ColorRgba.WHITE));
                planes.glow(impact.add(0, 2, 0), BEAM * 1.4f + Mth.sin(age * 2.1f), PALE_GOLD);
                BeamLight light = NebulonLights.beam(impact.subtract(0, 2, 0), new Vec3(impact.x, Math.min(top, impact.y + 200), impact.z))
                        .color(PALE_GOLD).radius(BEAM * 2.5f).brightness(6).maxRenderDistance(RENDER_DISTANCE).build();
                if (beamLight != null && beamLight.isAlive()) beamLight.set(light);
                else beamLight = NebulonLights.lights().add(world(), light);
            } else {
                beamCore = hide(beamCore);
                beamBody = hide(beamBody);
                beamHalo = hide(beamHalo);
                if (beamLight != null) beamLight.close();
                beamLight = null;
            }

            // The detonation: a fireball, two shockwave rings racing outwards and thinning as they go, a blinding light
            // fading, the crater glowing.
            if (blasted) {
                if (blastAge <= 20) {
                    float fireball = (float) CRATER * 1.3f;
                    float size = blastAge < 5 ? fireball * (0.25f + 0.75f * blastAge / 5f) : fireball + (blastAge - 5) * 0.5f;
                    float fade = blastAge < 5 ? 1 : 1 - (blastAge - 5) / 15f;
                    planes.glow(impact.add(0, CRATER * 0.25, 0), size, scaled(PALE_GOLD, fade));
                }
                if (blastAge <= 18) {
                    float fade = 1 - blastAge / 18f;
                    planes.ring(impact.add(0, 0.2, 0), UP, 4 + SHOCKWAVE * ease(blastAge / 16f), 3f * fade + 0.15f, scaled(ColorRgba.WHITE, fade));
                    planes.ring(impact.add(0, 0.3, 0), UP, 3 + SHOCKWAVE * 0.65 * ease(blastAge / 18f), 1.5f * fade + 0.15f, scaled(GOLD, fade));
                }
                float flash = 1 - blastAge / 30f;
                blastLight = flash > 0 ? light(blastLight, impact.add(0, 6, 0), 110, 9 * flash * flash, PALE_GOLD) : unlight(blastLight);
                // The crater cools slowly, the rim last: a ring of embers round its lip, lit once the dust has cleared.
                float cooling = Math.max(0, 1 - blastAge / (float) AFTERMATH_TICKS);
                emberLight = light(emberLight, impact.subtract(0, CRATER * 0.3, 0), (float) CRATER * 1.2f, 3 * (float) Math.sqrt(cooling), EMBER);
                if (blastAge >= 10) {
                    float rim = cooling * ease((blastAge - 10) / 20f) * (0.85f + 0.15f * Mth.sin(age * 0.3f));
                    planes.ring(impact.add(0, 0.15, 0), UP, CRATER, 0.5f + 0.5f * cooling, scaled(EMBER, 0.6f * rim));
                }
            }
            planes.end();
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
         * Storm clouds wheeling around the sky seal, thickening as the strike gathers: big dark puffs drifting sideways
         * around it, so from below they read as a vortex.
         */
        private void stormParticles() {
            int count = Fx.scaled(beamAge >= 0 ? 3 : 1 + Math.round(3 * gathered()));
            for (int i = 0; i < count; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI, radius = SKY * (0.55 + random.nextDouble() * 1.2);
                Vec3 at = onCircle(sky, radius, angle, 2 + random.nextDouble() * 5);
                // Tangential, so the cloud turns around the seal.
                Vec3 swirl = new Vec3(-Math.sin(angle), 0, Math.cos(angle)).scale(0.12 + random.nextDouble() * 0.1);
                // Signal smoke: the largest vanilla smoke, so it still reads as cloud from the ground.
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, swirl.add(0, -0.02, 0));
            }
        }

        /** Motes streaming up from the outer ring into the sky, glints falling from it, dust drawn inwards. */
        private void gatherParticles() {
            double rim = CRATER * ease(age / 18f);
            for (int i = 0, n = Fx.scaled(5); i < n; i++) {
                Vec3 at = onCircle(impact, rim * (0.9 + random.nextDouble() * 0.1), random.nextDouble() * Mth.TWO_PI, 0.2);
                particle(i % 2 == 0 ? ModParticles.GOLD : ParticleTypes.END_ROD, at, new Vec3(0, 0.25 + random.nextDouble() * 0.35, 0));
            }
            for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                Vec3 at = onCircle(sky, Math.sqrt(random.nextDouble()) * SKY * 0.9, random.nextDouble() * Mth.TWO_PI, -1);
                particle(ModParticles.GOLD, at, new Vec3(0, -0.25, 0));
            }
            if (age > gatherTicks - 25) {
                for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 at = onCircle(impact, CRATER + 2, angle, 0.3);
                    particle(ParticleTypes.CLOUD, at, new Vec3(-Math.cos(angle) * 0.25, 0.02, -Math.sin(angle) * 0.25));
                }
            }
        }

        /**
         * The cracks, racing out from the beam's foot over most of its life: lines of gold sparks, redrawn every other tick
         * as they lengthen, flames licking up out of them, and a spray of sparks and grit at each advancing tip.
         */
        private void crackParticles() {
            float split = ease(beamAge / (beamTicks * 0.7f));
            for (List<Vec3> crack : cracks) {
                List<Vec3> path = partial(crack, split);
                if (path == null) continue;
                if (beamAge % 2 == 0) {
                    for (int i = 1; i < path.size(); i++) {
                        Vec3 from = path.get(i - 1), to = path.get(i);
                        int dots = Math.max(1, (int) (from.distanceTo(to) / 0.4));
                        for (int j = 0; j < dots; j++) {
                            Vec3 at = from.lerp(to, j / (double) dots).add(0, 0.22, 0);
                            particle(ModParticles.GOLD, at, Vec3.ZERO);
                            // The motes last a second or more, so they're laid less often.
                            if (beamAge % 4 == 0 && j % 2 == 0) particle(CRACK_DUST, at, Vec3.ZERO);
                        }
                    }
                }
                Vec3 lick = path.get(random.nextInt(path.size()));
                particle(ParticleTypes.SMALL_FLAME, lick, new Vec3(0, 0.04, 0));
                if (split < 1) {
                    Vec3 tip = path.getLast();
                    for (int i = 0; i < 2; i++) particle(ModParticles.GOLD, tip, new Vec3(jitter(random, 0.3), 0.3 + random.nextDouble() * 0.2, jitter(random, 0.3)));
                    particle(ParticleTypes.LAVA, tip, Vec3.ZERO);
                }
            }
        }

        /** A flare at the beam's foot, sparks and debris flung from it, light streaming down it, smoke rolling out. */
        private void beamParticles() {
            crackParticles();
            Vec3 foot = impact.add(0, 0.5, 0);
            if (beamAge % 3 == 0) particle(FLARE, impact.add(0, 1.5, 0), Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(10); i < n; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI, speed = 0.5 + random.nextDouble() * 0.6;
                particle(ModParticles.GOLD, foot, new Vec3(Math.cos(angle) * speed, 0.15 + random.nextDouble() * 0.45, Math.sin(angle) * speed));
            }
            for (int i = 0, n = Fx.scaled(4); i < n; i++) {
                Vec3 at = onCircle(impact, random.nextDouble() * BEAM, random.nextDouble() * Mth.TWO_PI, 5 + random.nextDouble() * 70);
                particle(ParticleTypes.END_ROD, at, new Vec3(0, -1.5, 0));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                particle(debris, foot, new Vec3(jitter(random, 1), 0.5 + random.nextDouble() * 0.7, jitter(random, 1)));
            }
            for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                double angle = random.nextDouble() * Mth.TWO_PI;
                Vec3 at = onCircle(impact, BEAM + 1 + random.nextDouble() * 4, angle, 0.3);
                particle(ParticleTypes.LARGE_SMOKE, at, new Vec3(Math.cos(angle) * 0.2, 0.05, Math.sin(angle) * 0.2));
            }
            particle(ParticleTypes.LAVA, foot, Vec3.ZERO);
        }

        /** The detonation itself: flares, fireballs, a sphere of sparks, a fountain of debris and lava, the first smoke. */
        private void explosionParticles() {
            for (int i = 0; i < 5; i++) particle(FLARE, impact.add(jitter(random, 6), 1 + random.nextDouble() * 4, jitter(random, 6)), Vec3.ZERO);
            explosionBursts(8);
            for (int i = 0, n = Fx.scaled(24); i < n; i++) {
                particle(ParticleTypes.EXPLOSION, impact.add(upwards(random, 0.2).scale(2 + random.nextDouble() * CRATER * 0.5)), Vec3.ZERO);
            }
            for (int i = 0, n = Fx.scaled(180); i < n; i++) {
                particle(ModParticles.GOLD, impact.add(0, 1, 0), upwards(random, 0.3).scale(0.6 + random.nextDouble() * 1.2));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, ground());
            for (int i = 0, n = Fx.scaled(100); i < n; i++) {
                particle(debris, impact.add(0, 0.5, 0), new Vec3(jitter(random, 1.4), 0.6 + random.nextDouble() * 1.2, jitter(random, 1.4)));
            }
            for (int i = 0, n = Fx.scaled(30); i < n; i++) particle(ParticleTypes.LAVA, impact.add(0, 0.5, 0), Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(14); i < n; i++) {
                Vec3 at = onCircle(impact, random.nextDouble() * 5, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * 2);
                particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, new Vec3(jitter(random, 0.05), 0.06 + random.nextDouble() * 0.08, jitter(random, 0.05)));
            }
        }

        /** Fireballs across a dome over the crater. */
        private void explosionBursts(int count) {
            for (int i = 0; i < count; i++) {
                particle(ParticleTypes.EXPLOSION_EMITTER, impact.add(upwards(random, 0.4).scale(random.nextDouble() * CRATER * 0.6)), Vec3.ZERO);
            }
        }

        /** The shockwave racing outwards across the ground, the smoke column, then embers rising from the crater. */
        private void aftermathParticles() {
            if (blastAge <= 5) explosionBursts(3);
            if (blastAge <= 14) {
                double radius = 4 + SHOCKWAVE * ease(blastAge / 14f);
                for (int i = 0, n = Fx.scaled(48); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    particle(i % 4 == 0 ? ModParticles.GOLD : ParticleTypes.CLOUD, onCircle(impact, radius, angle, 0.3), out.scale(0.4).add(0, 0.05, 0));
                }
            }
            if (blastAge <= 60) {
                for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                    Vec3 at = onCircle(impact, random.nextDouble() * BEAM, random.nextDouble() * Mth.TWO_PI, random.nextDouble() * 6);
                    particle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, at, new Vec3(0, 0.12, 0));
                }
            }
            if (blastAge % 2 == 0) {
                for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                    Vec3 at = onCircle(impact, Math.sqrt(random.nextDouble()) * (CRATER - 2), random.nextDouble() * Mth.TWO_PI, -random.nextDouble() * 6);
                    particle(i == 0 ? ParticleTypes.LAVA : ModParticles.GOLD, at, new Vec3(0, 0.05, 0));
                }
            }
            if (blastAge % 4 == 0) {
                Vec3 at = onCircle(impact, Math.sqrt(random.nextDouble()) * (CRATER - 2), random.nextDouble() * Mth.TWO_PI, -random.nextDouble() * 4);
                particle(ParticleTypes.SMOKE, at, new Vec3(0, 0.06, 0));
            }
            // Embers raining down out of the clearing sky, thinning as it clears: glowing drops that fall all the way,
            // and gold glints sinking among them.
            if (blastAge >= 8 && blastAge <= 160) {
                for (int i = 0, n = Fx.scaled(blastAge < 70 ? 4 : 1); i < n; i++) {
                    Vec3 at = onCircle(impact, Math.sqrt(random.nextDouble()) * CRATER * 1.3, random.nextDouble() * Mth.TWO_PI, 6 + random.nextDouble() * 30);
                    particle(i % 2 == 0 ? ParticleTypes.FALLING_LAVA : ModParticles.GOLD, at, new Vec3(jitter(random, 0.04), -0.22, jitter(random, 0.04)));
                }
            }
            // Sparks still spitting off the rim.
            if (blastAge >= 20 && blastAge % 3 == 0 && blastAge < AFTERMATH_TICKS - 40) {
                Vec3 at = onCircle(impact, CRATER + jitter(random, 1), random.nextDouble() * Mth.TWO_PI, 0.2);
                particle(random.nextInt(3) == 0 ? ParticleTypes.LAVA : ModParticles.GOLD, at, new Vec3(0, 0.18 + random.nextDouble() * 0.1, 0));
            }
        }

        /** The block the beam is digging into, for debris: the first solid one under the mark, else stone. */
        private BlockState ground() {
            BlockPos pos = BlockPos.containing(impact);
            for (int i = 0; i < 8; i++, pos = pos.below()) {
                BlockState state = world().getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) return state;
            }
            return Blocks.STONE.defaultBlockState();
        }

        private void screen(float near) {
            if (near <= 0) return;
            if (blastAge < 0) {
                ScreenEffects.tint(0.65f * ease(gathered()) * near, TINT);
                if (beamAge >= 0) ScreenEffects.shake((0.32f + 0.3f * beamAge / beamTicks) * near);
                else if (age > gatherTicks - 20) ScreenEffects.shake(0.15f * near);
            } else if (blastAge < 20) {
                ScreenEffects.tint(0.65f * (1 - blastAge / 20f) * near, TINT);
            }
        }

        @Override public void release() {
            planes.clear();
            for (EffectHandle<?> handle : new EffectHandle<?>[] {groundRunes, skyRunes, beamCore, beamBody, beamHalo}) {
                if (handle != null) handle.remove();
            }
            for (PointLightHandle light : new PointLightHandle[] {groundLight, skyLight, blastLight, emberLight}) {
                if (light != null) light.close();
            }
            if (beamLight != null) beamLight.close();
        }
    }
}
