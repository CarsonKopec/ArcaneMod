package dev.arcane.client.fx;

import static dev.arcane.client.fx.Fx.RENDER_DISTANCE;
import static dev.arcane.client.fx.Fx.UP;
import static dev.arcane.client.fx.Fx.anyDirection;
import static dev.arcane.client.fx.Fx.camera;
import static dev.arcane.client.fx.Fx.circle;
import static dev.arcane.client.fx.Fx.ease;
import static dev.arcane.client.fx.Fx.hide;
import static dev.arcane.client.fx.Fx.jitter;
import static dev.arcane.client.fx.Fx.onCircle;
import static dev.arcane.client.fx.Fx.particle;
import static dev.arcane.client.fx.Fx.particlesVisible;
import static dev.arcane.client.fx.Fx.scaled;
import static dev.arcane.client.fx.Fx.show;
import static dev.arcane.client.fx.Fx.upwards;
import static dev.arcane.client.fx.Fx.whiter;
import static dev.arcane.client.fx.Fx.world;

import com.mojang.math.Transformation;
import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.NebulonRendering;
import de.nexusrealms.nebulon.api.render.effect.ArcChain;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.item.ArcaneStaffItem;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.registry.ModSpells;
import dev.arcane.spell.Planetfall;
import dev.arcane.spell.PlanetfallTerrain;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Brightness;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Planetfall as the client sees it. The server sends one message per phase; everything between is animated here, and
 * most of it drawn straight into the frame by {@link PlanetfallShader}:
 * <ul>
 *   <li>Rift: the sky turns to space ({@link SpaceSky}): nebulae, a galaxy's band and a ringed giant come out, even at
 *       noon. High above the mark the sky tears open onto another universe, bending the world round its rim and
 *       cracking like glass around it, and far beyond it the planet can be seen coming. A seal burns on the ground,
 *       motes stream upwards as gravity fails, and the view is drawn up towards the rift.</li>
 *   <li>Fall: the planet comes through, spinning, its crust splitting along molten faults as it heats, wrapped in a
 *       bow shock and trailing a wake of plasma, lighting the ground beneath it. The view widens, rolls, and is drawn to
 *       it.</li>
 *   <li>Impact: a white flash, an instant of inverted silhouettes, then the planet shatters into tumbling blocks, the
 *       ground is thrown up and a dome of shocked air races outwards. Where it reaches you the view is kicked and the
 *       boom arrives, late, as it would. A mushroom cloud rises, the rift seals and the sky clears over a glowing
 *       crater, where ash drifts down for half a minute and embers rise from the planet's heart.</li>
 * </ul>
 * In the second half of the fall, burning shards break off the planet and streak down to crater the ground around the
 * mark ({@link Shard}).
 * Without the screen pass ({@link PlanetfallShader#available}) the rift is drawn with rings of light and the planet with
 * block displays ({@link VoxelPlanet}) instead. Camera effects go through {@link ScreenEffects}, which applies the
 * accessibility settings.
 */
final class PlanetfallFx {
    private static final ColorRgba VIOLET = ColorRgba.rgba(0x9B4DFFFF);
    private static final ColorRgba MAGENTA = ColorRgba.rgba(0xFF4FD8FF);
    private static final ColorRgba CYAN = ColorRgba.rgba(0x6CF5FFFF);
    private static final ColorRgba FIRE = ColorRgba.rgba(0xFF7A1FFF);
    private static final ColorRgba MOLTEN = ColorRgba.rgba(0xFFC060FF);
    private static final ColorRgba EMBER = ColorRgba.rgba(0xFF4A12FF);
    private static final ParticleOptions FLARE = ColorParticleOption.create(ParticleTypes.FLASH, 0xFFFFE0B8);
    private static final float R = (float) SpellTuning.PLANETFALL_PLANET_RADIUS;
    private static final double CRATER = SpellTuning.PLANETFALL_CRATER_RADIUS;
    private static final double LIFT = SpellTuning.PLANETFALL_LIFT_RADIUS;
    /**
     * The impact's fireball, mushroom cloud, flares and fountain were shaped for a 72-block blast; they're drawn this
     * many times that size, to match the server's. The screen pass is told it too.
     */
    private static final float BLAST = (float) (SpellTuning.PLANETFALL_IMPACT_RADIUS / 72);
    /** The rift's outer radius, drawn with rings of light. */
    private static final double RIFT = R * 3.3;
    /** The rift's radius as the screen pass draws it, a tear wide enough for the planet with room to spare. */
    private static final float RIFT_RADIUS = R * 2.4f;
    /** How far beyond the rift the planet is when it first shows through it. */
    private static final double APPROACH = 1400;
    private static final int AFTERMATH_TICKS = 300;
    /** Ash keeps drifting down over the crater, and embers rising from the core, this long after the impact. */
    private static final int ASH_TICKS = 600;
    /** How long the sky takes to clear after the impact. */
    private static final int SKY_CLEARS = 100;
    /** How long the screen pass's mushroom cloud lingers after the impact, fading over its last few seconds. */
    private static final int CLOUD_TICKS = 260;
    /** The shock dome shows this long, growing on past the pressure front's reach as it fades. */
    private static final float DOME_TICKS = SpellTuning.PLANETFALL_PRESSURE_TICKS + 20;
    /** Blocks a tick the shock front travels once past the pressure front's reach; its boom arrives with it. */
    private static final double SHOCK_SPEED = 8;
    private static final int RIFT_TINT = 0x160628, FIRE_TINT = 0x3A1400;

    private static final Fx.Tracker<Fall> FALLS = new Fx.Tracker<>();
    private static final Fx.Tracker<Shard> SHARDS = new Fx.Tracker<>();

    private PlanetfallFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.PLANETFALL_RIFT, (effect, level) -> fall(effect, level).rift(effect));
        SpellEffectRenderers.register(ModSpellEffects.PLANETFALL_FALL, (effect, level) -> fall(effect, level).fall(effect));
        SpellEffectRenderers.register(ModSpellEffects.PLANETFALL_IMPACT, (effect, level) -> fall(effect, level).impact());
        SpellEffectRenderers.register(ModSpellEffects.PLANETFALL_SHARD, (effect, level) -> SHARDS.get(effect.sourceId(), level, id -> new Shard(level, effect)));
    }

    private static Fall fall(SpellEffect effect, ClientLevel level) {
        return FALLS.get(effect.sourceId(), level, id -> new Fall(id, effect));
    }

    /** Each tick, after the strikes have animated. */
    static void tick(Minecraft client) {
        channel(client);
    }

    /**
     * While you channel Planetfall, the sky starts to give way for you alone: it dims towards space and the stars come
     * out, the view narrows and its edges darken, and it trembles once the channel is complete.
     */
    private static void channel(Minecraft client) {
        LocalPlayer player = client.player;
        if (player == null || !player.isUsingItem()) return;
        ItemStack staff = player.getUseItem();
        if (!(staff.getItem() instanceof ArcaneStaffItem) || SpellCasting.selected(staff) != ModSpells.PLANETFALL) return;
        float progress = Mth.clamp(player.getTicksUsingItem() / (float) SpellTuning.PLANETFALL_CHARGE_TICKS, 0, 1);
        float focus = ease(progress);
        SpaceSky.request(0.4f * focus);
        ScreenEffects.fov(1 - 0.12f * focus);
        ScreenEffects.tint(0.2f * focus, RIFT_TINT);
        if (progress >= 1) ScreenEffects.shake(0.1f);
        PlanetfallShader.show((view, partialTick) -> {
            view.clock.x = (player.tickCount + partialTick) / 20;
            view.lens.set(0.5f * focus, 0.3f * focus * focus, 0, 0);
            view.grade.set(0.86f, 0.82f, 1.14f, 0.5f * focus);
            view.post.set(focus, 0.5f * focus, 0.4f * focus, BLAST);
        }, Integer.MIN_VALUE + 1);
    }

    /** 1 within 96 blocks of the camera, fading to 0 at 352: how hard this spell's screen effects hit. */
    private static float near(Vec3 at) {
        return (float) Mth.clamp(1 - (camera().distanceTo(at) - 96) / 256, 0, 1);
    }

    /** How many ticks after the impact the shock front is {@code distance} blocks from the mark. */
    private static float arrival(double distance) {
        double reach = SpellTuning.PLANETFALL_IMPACT_RADIUS;
        if (distance <= 16) return 0;
        if (distance <= reach) return (float) (SpellTuning.PLANETFALL_PRESSURE_TICKS * (1 - Math.sqrt(1 - (distance - 16) / (reach - 16))));
        return (float) (SpellTuning.PLANETFALL_PRESSURE_TICKS + (distance - reach) / SHOCK_SPEED);
    }

    /** The shock front's radius {@code ticks} after the impact: the pressure front, then running on beyond it. */
    private static double shockRadius(float ticks) {
        float pressure = SpellTuning.PLANETFALL_PRESSURE_TICKS;
        return PlanetfallTerrain.pressureRadius(Math.min(ticks, pressure)) + Math.max(0, ticks - pressure) * SHOCK_SPEED;
    }

    private static final class Fall implements Fx.Animation, PlanetfallShader.Scene {
        final int id;
        final int seed;
        final Vec3 mark;
        final Vec3 riftOffset;
        final Vec3 rift;
        /** The way the planet travels, out of the rift towards the mark; the rift faces along it. */
        final Vec3 heading;
        final Vec3 across, vertical;
        /** The axis the planet spins about, as the screen pass draws it. */
        final Vector3f axis;
        final RandomSource random;
        final PlanetfallTerrain terrain;
        final BlockState debrisMaterial;
        final Fx.Planes planes = new Fx.Planes();
        int riftTicks = SpellTuning.PLANETFALL_RIFT_TICKS;
        int fallTicks = SpellTuning.PLANETFALL_FALL_TICKS;
        int age;
        int fallAge = -1;
        int impactAge = -1;
        /** Whether the screen pass draws this tick; otherwise shapes and block displays do. */
        boolean drawn;
        boolean boomed;
        VoxelPlanet planet;
        PlanetfallDebris groundDebris;
        Vec3 planetAt;
        float spin, spinBefore;
        EffectHandle<MagicCircle> riftRunes, groundRunes;

        Fall(int id, SpellEffect first) {
            this.id = id;
            this.seed = (int) first.power();
            this.mark = first.position();
            Vec3 offset = first.direction().lengthSqr() > 1
                    ? first.direction() : new Vec3(0, SpellTuning.PLANETFALL_RIFT_HEIGHT, SpellTuning.PLANETFALL_RIFT_LEAD);
            this.riftOffset = offset;
            this.rift = mark.add(offset);
            this.heading = mark.subtract(rift).normalize();
            this.across = heading.cross(Math.abs(heading.y) < 0.999 ? UP : new Vec3(1, 0, 0)).normalize();
            this.vertical = heading.cross(across).normalize();
            this.random = RandomSource.create(seed);
            float tilt = 0.45f + random.nextFloat() * 0.3f, azimuth = random.nextFloat() * Mth.TWO_PI;
            this.axis = new Vector3f(Mth.sin(tilt) * Mth.cos(azimuth), Mth.cos(tilt), Mth.sin(tilt) * Mth.sin(azimuth));
            this.terrain = new PlanetfallTerrain(seed);
            this.debrisMaterial = ground();
            this.planetAt = Planetfall.planetAt(mark, riftOffset, 0);
        }

        void rift(SpellEffect effect) {
            riftTicks = Math.max(1, effect.age());
        }

        void fall(SpellEffect effect) {
            if (fallAge >= 0 || impactAge >= 0) return;
            fallAge = 0;
            fallTicks = Math.max(1, effect.age());
            age = Math.max(age, riftTicks);
            float near = near(mark);
            ScreenEffects.shake(0.3f * near);
            if (!PlanetfallShader.available()) ScreenEffects.flash(0.35f * near);
        }

        void impact() {
            if (impactAge >= 0) return;
            impactAge = 0;
            age = Math.max(age, riftTicks + fallTicks);
            // Sample the surface before the first excavation packets replace it with the basin.
            groundDebris = new PlanetfallDebris(world(), seed, mark);
            // The planet breaks into blocks: with the screen pass it was never made of them, so it is built now.
            planetAt = Planetfall.planetAt(mark, riftOffset, 1);
            if (planet == null) planet = new VoxelPlanet(world(), seed, R);
            planet.pose(planetAt, spin, 1);
            planet.shatter(mark);
            float near = near(mark);
            ScreenEffects.flash(PlanetfallShader.available() ? 0.3f * near : near);
            ScreenEffects.shake(near);
            if (particlesVisible(mark)) impactParticles();
        }

        private float progress() {
            return fallAge < 0 ? 0 : Mth.clamp(fallAge / (float) fallTicks, 0, 1);
        }

        @Override public boolean tick() {
            age++;
            if (impactAge >= 0) impactAge++;
            else if (fallAge >= 0) fallAge++;
            drawn = PlanetfallShader.available();
            spinBefore = spin;
            if (fallAge >= 0 && impactAge < 0) {
                planetAt = Planetfall.planetAt(mark, riftOffset, progress());
                spin += 0.018f + 0.035f * progress();
                if (drawn) {
                    if (planet != null) planet.remove();
                    planet = null;
                } else {
                    if (planet == null) planet = new VoxelPlanet(world(), seed, R);
                    planet.pose(planetAt, spin, progress());
                }
            } else if (impactAge < 0) {
                spin += 0.012f;
            }
            // Once the sky has cleared there is nothing left for the screen pass to draw.
            if (drawn && impactAge < CLOUD_TICKS) PlanetfallShader.show(this, -Math.round((float) camera().distanceTo(mark)));
            SpellLighting.guarded(this::animate);
            if (particlesVisible(mark) || particlesVisible(planetAt)) particles();
            screen();
            if (impactAge >= 0) {
                boom();
                boolean debrisGone = planet == null || !planet.shattered() || planet.tickDebris();
                if (debrisGone) planet = null;
                if (groundDebris != null && groundDebris.tick()) groundDebris = null;
                return impactAge > ASH_TICKS && planet == null && groundDebris == null;
            }
            // A strike whose impact never arrives (the server stopped, the chunk unloaded) still ends.
            return age > riftTicks + fallTicks + 60;
        }

        private void animate() {
            boolean falling = fallAge >= 0 && impactAge < 0;
            planes.begin();
            if (drawn) riftRunes = hide(riftRunes);
            else drawRift();
            drawGround();
            if (falling && !drawn) drawFire();
            if (impactAge >= 0) drawImpact();
            planes.end();
        }

        /**
         * How open the rift is, {@code t} ticks after it began (fractional, between ticks): opening over two seconds,
         * then sealing in the impact's first second.
         */
        private float riftOpen(float t) {
            if (impactAge >= 0) return Math.max(0, 1 - (impactAge + t - age) / 20f);
            return ease(t / 40f);
        }

        /**
         * Rings of light turning against each other, facing the way the planet comes: an eight-pointed seal, a heptagon
         * and a ring inside it, and a white-hot inner rim the planet comes through, with runes and lightning.
         */
        private void drawRift() {
            float open = riftOpen(age);
            if (open <= 0) {
                riftRunes = hide(riftRunes);
                return;
            }
            Vec3 facing = heading;
            float pulse = 0.85f + 0.15f * Mth.sin(age * 0.3f);
            double outer = RIFT * open;
            planes.seal(rift, facing, outer, 1.4f, 8, age * 0.01f, scaled(VIOLET, pulse));
            planes.polygon(rift, facing, outer * 0.78, 7, -age * 0.016f, 0.9f, scaled(MAGENTA, 0.9f * pulse));
            planes.ring(rift, facing, outer * 0.62, 0.7f, scaled(CYAN, 0.8f));
            planes.ring(rift, facing, (R + 4) * open, 2.4f, scaled(ColorRgba.WHITE, pulse));
            planes.ring(rift, facing, (R + 7) * open, 0.8f, scaled(MAGENTA, pulse));
            // A throat behind the seal gives the portal depth as the planet emerges.
            for (int i = 1; i <= 3; i++) {
                Vec3 throat = rift.subtract(heading.scale(i * 7 * open));
                planes.ring(throat, facing, (R + 4 + i * 4) * open, 0.5f,
                        scaled(i % 2 == 0 ? CYAN : VIOLET, 0.42f / i));
                float turn = -age * 0.018f + i * 1.7f;
                planes.arc(rift, facing, outer * (0.65 + i * 0.12), 1.2f,
                        turn, turn + 1.7f, scaled(MAGENTA, 0.7f * open));
            }
            for (int i = 0; i < 3; i++) {
                float from = age * 0.04f + i * Mth.TWO_PI / 3;
                planes.arc(rift, facing, outer * 0.9, 1.1f, from, from + 0.9f, scaled(CYAN, 0.9f));
            }
            riftRunes = show(riftRunes, circle(rift, facing, outer * 0.95, 0.3f, scaled(VIOLET, 0.3f * open), 6, id, 0.1f));
            if (age % 3 == 0 && open > 0.5f) lightning(facing, outer);
        }

        /** A bolt of lightning across the rift, between two points on its rings. */
        private void lightning(Vec3 facing, double outer) {
            Vec3 u = facing.cross(Math.abs(facing.y) < 0.9 ? UP : new Vec3(1, 0, 0)).normalize(), v = facing.cross(u).normalize();
            double a = random.nextDouble() * Mth.TWO_PI, b = a + 0.6 + random.nextDouble() * 1.6;
            double ra = outer * (0.6 + random.nextDouble() * 0.4), rb = (R + 4) + random.nextDouble() * outer * 0.4;
            Vec3 from = rift.add(u.scale(Math.cos(a) * ra)).add(v.scale(Math.sin(a) * ra));
            Vec3 to = rift.add(u.scale(Math.cos(b) * rb)).add(v.scale(Math.sin(b) * rb));
            ColorRgba color = random.nextBoolean() ? CYAN : MAGENTA;
            NebulonRendering.effects().spawn(world(), new ArcChain(List.of(from, to), color, 0.45f, 2.2f, 1.5f, RENDER_DISTANCE), 6);
        }

        /** The warning seal on the ground, and the edge of the zone where gravity fails, until the impact. */
        private void drawGround() {
            if (impactAge >= 0) {
                groundRunes = hide(groundRunes);
                return;
            }
            float grow = ease(age / 30f), heat = 0.6f + 0.4f * progress();
            Vec3 ground = mark.add(0, 0.06, 0);
            planes.seal(ground, UP, CRATER * grow, 0.6f, 10, -age * 0.012f, scaled(MAGENTA, heat));
            planes.ring(ground, UP, LIFT * grow, 0.3f, scaled(CYAN, 0.6f));
            planes.ring(ground, UP, CRATER * 0.5 * grow, 0.4f, scaled(whiter(FIRE, 0.3f), heat));
            groundRunes = show(groundRunes, circle(ground, UP, CRATER * 0.95 * grow, 0.1f, scaled(MAGENTA, 0.3f * heat), 5, id + 7L, -0.2f));
        }

        /**
         * The fire of the planet's entry, growing as it speeds up: a halo of burning air round its rim, a white-hot cap
         * ahead of it, and luminous bow waves trailing behind.
         */
        private void drawFire() {
            float heat = progress() * progress();
            // A thin luminous limb keeps the rock, oceans and fractures readable through the heat.
            planes.halo(planetAt, R * (1.1 + 0.12 * heat), 0.4f + 0.9f * heat, scaled(MOLTEN, 0.3f + 0.55f * heat));
            Vec3 nose = planetAt.add(heading.scale(R * 0.9));
            planes.glow(nose, R * 0.65f, scaled(whiter(MOLTEN, 0.35f), 0.12f + 0.3f * heat));
            // Stacked, tilted bow waves: the air compresses ahead and fans out behind it.
            for (int i = 0; i < 4; i++) {
                Vec3 at = planetAt.subtract(heading.scale(R * (0.4 + i * 1.1)));
                double radius = R * (1.05 + i * (0.3 + heat * 0.25));
                float strength = heat * (0.52f - i * 0.09f);
                float angle = age * 0.035f + i * 0.6f;
                planes.arc(at, heading, radius, 0.5f + heat, angle, angle + 2.5f, scaled(i == 0 ? MOLTEN : FIRE, strength));
                planes.arc(at, heading, radius, 0.3f + heat, angle + Mth.PI, angle + Mth.PI + 2.5f, scaled(VIOLET, strength * 0.6f));
            }
        }

        /** The ejecta's glowing heads; without the screen pass, the contact flash and the pressure front too. */
        private void drawImpact() {
            int t = impactAge;
            if (!drawn) {
                if (t < 18) {
                    float fade = 1 - t / 18f;
                    float radius = 12 + 22 * ease(t / 12f);
                    planes.glow(mark.add(0, 6 + t * 0.3, 0), radius, scaled(MOLTEN, fade * 0.55f));
                    if (t < 7) planes.glow(mark.add(0, 3, 0), radius * 0.65f, scaled(ColorRgba.WHITE, (1 - t / 7f) * 0.8f));
                }
                if (t <= SpellTuning.PLANETFALL_PRESSURE_TICKS) {
                    float fade = 1 - t / (float) SpellTuning.PLANETFALL_PRESSURE_TICKS;
                    planes.ring(mark.add(0, 1, 0), UP, PlanetfallTerrain.pressureRadius(t),
                            0.4f + fade * 1.3f, scaled(MOLTEN, fade * 0.28f));
                }
            }
            // Orange light shines through the rising, darker cap.
            if (t >= 10 && t < 50) {
                float rise = (t - 10) / 40f;
                planes.glow(mark.add(0, 14 + 30 * rise, 0), 15 + rise * 9,
                        scaled(FIRE, (1 - rise) * 0.16f));
            }
            ejecta(false);
        }

        private void particles() {
            if (impactAge >= 0) {
                aftermathParticles();
                return;
            }
            riftParticles();
            if (fallAge >= 0) fireParticles();
        }

        /** Stars wheeling into the rift, sparks spilling out of it, and motes rising from the ground as gravity fails. */
        private void riftParticles() {
            float open = riftOpen(age);
            for (int i = 0, n = Fx.scaled(Math.round(12 * open)); i < n; i++) {
                // Four coherent spiral arms in the portal's own plane, rather than a horizontal particle disc.
                double along = random.nextDouble();
                double angle = age * 0.035 + (i % 4) * Math.PI / 2 + along * 3.5;
                double radius = RIFT * open * (0.28 + along * 0.9);
                Vec3 radial = across.scale(Math.cos(angle)).add(vertical.scale(Math.sin(angle)));
                Vec3 tangent = across.scale(-Math.sin(angle)).add(vertical.scale(Math.cos(angle)));
                Vec3 at = rift.add(radial.scale(radius)).subtract(heading.scale(6 * along));
                Vec3 inwards = radial.scale(-0.35).add(tangent.scale(0.65)).add(heading.scale(0.08));
                particle(i % 4 == 0 ? ParticleTypes.END_ROD : i % 2 == 0 ? ModParticles.CYAN : ModParticles.VIOLET, at, inwards);
            }
            for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                particle(ModParticles.PINK, rift.add(jitter(random, R), jitter(random, R), jitter(random, R)), heading.scale(0.8 + random.nextDouble()));
            }
            for (int i = 0, n = Fx.scaled(5); i < n; i++) {
                Vec3 at = surface(onCircle(mark, Math.sqrt(random.nextDouble()) * LIFT, random.nextDouble() * Mth.TWO_PI, 0));
                if (at != null) particle(i % 3 == 0 ? ModParticles.CYAN : ParticleTypes.REVERSE_PORTAL, at, new Vec3(0, 0.1 + random.nextDouble() * 0.2, 0));
            }
        }

        /** Fire and lava streaming off the planet, smoke trailing behind it, and the ground's dust blown away as it nears. */
        private void fireParticles() {
            float heat = progress() * progress();
            Vec3 back = heading.scale(-1);
            for (int i = 0, n = Fx.scaled(6 + Math.round(30 * heat)); i < n; i++) {
                Vec3 out = anyDirection(random);
                // The leading half burns.
                if (out.dot(heading) < 0) out = out.scale(-1);
                Vec3 at = planetAt.add(out.scale(R * (0.95 + random.nextDouble() * 0.2)));
                Vec3 velocity = back.scale(0.4 + 1.6 * heat).add(out.scale(0.2)).add(jitter(random, 0.2), jitter(random, 0.2), jitter(random, 0.2));
                ParticleOptions type = i % 5 == 0 ? ParticleTypes.LAVA : i % 3 == 0 ? ModParticles.GOLD : ParticleTypes.FLAME;
                particle(type, at, velocity);
            }
            for (int i = 0, n = Fx.scaled(2 + Math.round(4 * heat)); i < n; i++) {
                Vec3 at = planetAt.add(back.scale(R * 0.8)).add(jitter(random, R), jitter(random, R), jitter(random, R));
                particle(i % 2 == 0 ? ParticleTypes.CAMPFIRE_SIGNAL_SMOKE : ParticleTypes.LARGE_SMOKE, at, back.scale(0.05));
            }
            // Three twisting plasma streams trail far behind the body as its speed builds.
            for (int i = 0, n = Fx.scaled(4 + Math.round(12 * heat)); i < n; i++) {
                double along = random.nextDouble();
                double angle = age * 0.19 - along * 6 + (i % 3) * Mth.TWO_PI / 3;
                double width = R * (0.9 + along * 0.8);
                Vec3 radial = across.scale(Math.cos(angle)).add(vertical.scale(Math.sin(angle)));
                Vec3 at = planetAt.add(back.scale(R + along * (18 + 65 * heat))).add(radial.scale(width));
                particle(i % 3 == 0 ? ModParticles.VIOLET : i % 2 == 0 ? ModParticles.GOLD : ParticleTypes.FLAME,
                        at, back.scale(0.35 + heat).add(radial.scale(0.08)));
            }
            double above = planetAt.y - R - mark.y;
            if (above < 70) {
                float gust = (float) Mth.clamp(1 - above / 70, 0, 1);
                for (int i = 0, n = Fx.scaled(Math.round(16 * gust)); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 at = surface(onCircle(mark, 4 + random.nextDouble() * LIFT, angle, 0));
                    if (at != null) particle(ParticleTypes.CLOUD, at, new Vec3(Math.cos(angle), 0.05, Math.sin(angle)).scale(0.3 + 0.9 * gust));
                }
            }
        }

        /** The moment of impact: flares, fireballs across a dome, stardust and sparks, a fountain of debris and lava. */
        private void impactParticles() {
            if (!Minecraft.getInstance().options.hideLightningFlash().get()) {
                for (int i = 0, n = Fx.scaled(Math.round(5 * BLAST)); i < n; i++) {
                    particle(FLARE, mark.add(jitter(random, 16 * BLAST), (2 + random.nextDouble() * 12) * BLAST, jitter(random, 16 * BLAST)), Vec3.ZERO);
                }
            }
            // With the screen pass, its raymarched fireball stands in for these.
            if (!drawn) {
                for (int i = 0, n = Fx.scaled(Math.round(4 * BLAST * BLAST)); i < n; i++) {
                    particle(ParticleTypes.EXPLOSION_EMITTER, mark.add(upwards(random, 0.3).scale(random.nextDouble() * 12 * BLAST)), Vec3.ZERO);
                }
                for (int i = 0, n = Fx.scaled(Math.round(24 * BLAST)); i < n; i++) {
                    Vec3 out = upwards(random, 0.3);
                    particle(ModParticles.PLANETFALL_FIRE, mark.add(out.scale((3 + random.nextDouble() * 11) * BLAST)), out.scale(0.8 * BLAST));
                }
            }
            double fling = Math.sqrt(BLAST);
            for (int i = 0, n = Fx.scaled(Math.round(120 * BLAST)); i < n; i++) {
                particle(i % 3 == 0 ? ParticleTypes.LAVA : ModParticles.GOLD,
                        mark.add(0, 3, 0), upwards(random, 0.15).scale((1.2 + random.nextDouble() * 3) * fling));
            }
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, debrisMaterial);
            for (int i = 0, n = Fx.scaled(Math.round(160 * BLAST)); i < n; i++) {
                particle(debris, mark.add(jitter(random, 10 * BLAST), 1, jitter(random, 10 * BLAST)),
                        new Vec3(jitter(random, 2.4 * fling), (0.8 + random.nextDouble() * 1.6) * fling, jitter(random, 2.4 * fling)));
            }
            for (int i = 0, n = Fx.scaled(Math.round(60 * BLAST)); i < n; i++) {
                particle(ParticleTypes.LAVA, mark.add(jitter(random, 12 * BLAST), 1, jitter(random, 12 * BLAST)), Vec3.ZERO);
            }
        }

        /**
         * The shockwave's dust wall racing outwards, the mushroom cloud (a rising column of smoke with a cap rolling out
         * at its top), then embers, smoke and the last of the stardust drifting over the crater. With the screen pass,
         * its raymarched cloud and base surge stand in for the particle ones.
         */
        private void aftermathParticles() {
            int t = impactAge;
            ejecta(true);
            settlingParticles(t);
            if (!drawn) cloudParticles(t);
            // Surface samples follow the crater as the server excavates it, including on uneven ground.
            if (t > 10 && t % 2 == 0) {
                int count = Math.round(10 * (1 - t / (float) AFTERMATH_TICKS));
                for (int i = 0, n = Fx.scaled(count); i < n; i++) {
                    double distance = Math.sqrt(random.nextDouble()) * (CRATER - 2);
                    Vec3 at = surface(onCircle(mark, distance, random.nextDouble() * Mth.TWO_PI, 0));
                    if (at == null) continue;
                    BlockState state = world().getBlockState(BlockPos.containing(at).below());
                    boolean hot = state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.SHROOMLIGHT);
                    ParticleOptions type = hot ? (i % 2 == 0 ? ParticleTypes.LAVA : ModParticles.GOLD)
                            : i % 3 == 0 ? ModParticles.VIOLET : ParticleTypes.SMOKE;
                    particle(type, at, new Vec3(0, 0.06 + random.nextDouble() * 0.1, 0));
                }
            }
        }

        /**
         * The long settling: ash drifting down over the whole crater from high above, thinning out over half a minute,
         * and embers and a thread of smoke rising from the planet's glowing heart at the bottom.
         */
        private void settlingParticles(int t) {
            float left = 1 - t / (float) ASH_TICKS;
            if (t > 40) {
                for (int i = 0, n = Fx.scaled(Math.round(8 * left)); i < n; i++) {
                    Vec3 at = onCircle(mark, Math.sqrt(random.nextDouble()) * CRATER * 1.2, random.nextDouble() * Mth.TWO_PI, 4 + random.nextDouble() * 36);
                    particle(i % 3 == 0 ? ParticleTypes.WHITE_ASH : ParticleTypes.ASH, at, Vec3.ZERO);
                }
            }
            if (t > 30) {
                double core = SpellTuning.PLANETFALL_CORE_RADIUS;
                for (int i = 0, n = Fx.scaled(Math.round(3 * left + 1)); i < n; i++) {
                    Vec3 at = surface(onCircle(mark, Math.sqrt(random.nextDouble()) * core * 1.3, random.nextDouble() * Mth.TWO_PI, 0));
                    // Only down in a crater: where terrain is spared there is none, and no core.
                    if (at == null || at.y > mark.y - SpellTuning.PLANETFALL_CRATER_DEPTH * 0.5) continue;
                    particle(i % 4 == 3 ? ParticleTypes.LAVA : ModParticles.GOLD, at, new Vec3(jitter(random, 0.05), 0.22 + random.nextDouble() * 0.2, jitter(random, 0.05)));
                    if (t % 5 == 0 && i == 0) particle(ParticleTypes.CAMPFIRE_COSY_SMOKE, at, new Vec3(0, 0.04, 0));
                }
            }
        }

        /** Without the screen pass: the fireball's billows, the dust wall and the mushroom cloud, in particles. */
        private void cloudParticles(int t) {
            if (t <= 20) {
                double radius = (7 + 21 * ease(t / 20f)) * BLAST;
                for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                    Vec3 out = upwards(random, 0.2);
                    Vec3 at = mark.add(0, (3 + t * 0.35) * BLAST, 0).add(out.scale(radius * (0.65 + random.nextDouble() * 0.35)));
                    particle(ModParticles.PLANETFALL_FIRE, at, out.scale(0.3 + random.nextDouble() * 0.45).add(0, 0.2, 0));
                }
            }
            if (t <= SpellTuning.PLANETFALL_PRESSURE_TICKS && t % 2 == 0) {
                double radius = PlanetfallTerrain.pressureRadius(t);
                // Overlapping billows form a moving wall, with a low leading skirt and a taller rolling crest.
                for (int i = 0, n = Fx.scaled(24); i < n; i++) {
                    double angle = (i + random.nextDouble() * 0.35) * Mth.TWO_PI / n + t * 0.19;
                    Vec3 out = new Vec3(Math.cos(angle), 0, Math.sin(angle));
                    Vec3 ground = surface(onCircle(mark, radius, angle, 0));
                    if (ground != null) particle(ModParticles.PLANETFALL_DUST,
                            ground.add(0, 1.2 + random.nextDouble() * 5, 0), out.scale(1.3).add(0, 0.18 + random.nextDouble() * 0.22, 0));
                }
            }
            if (t < SpellTuning.PLANETFALL_RUPTURE_TICKS && t % 2 == 0) {
                double radius = PlanetfallTerrain.ruptureRadius(t);
                for (int i = 0, n = Fx.scaled(4); i < n; i++) {
                    double angle = random.nextDouble() * Mth.TWO_PI;
                    Vec3 at = surface(onCircle(mark, radius, angle, 0));
                    if (at != null) particle(ModParticles.PLANETFALL_DUST, at,
                            new Vec3(Math.cos(angle) * 0.35, 0.45, Math.sin(angle) * 0.35));
                }
            }
            if (t >= 8 && t <= 110) {
                float rise = ease((t - 8) / 100f);
                double top = (18 + 64 * rise) * BLAST, spread = (10 + 25 * rise) * BLAST;
                if (t % 2 == 0) {
                    for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                        Vec3 at = onCircle(mark, random.nextDouble() * 9 * BLAST, random.nextDouble() * Mth.TWO_PI,
                                4 + random.nextDouble() * (top - 4));
                        particle(t < 24 && i == 0 ? ModParticles.PLANETFALL_FIRE : ModParticles.PLANETFALL_SMOKE,
                                at, new Vec3(jitter(random, 0.12), 0.35 + random.nextDouble() * 0.3, jitter(random, 0.12)));
                    }
                }
                if (t % 3 == 0) {
                    for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                        double angle = (i + random.nextDouble()) * Mth.TWO_PI / n;
                        Vec3 at = onCircle(mark, spread * (0.6 + random.nextDouble() * 0.45), angle, top + jitter(random, 9 * BLAST));
                        particle(ModParticles.PLANETFALL_SMOKE, at,
                                new Vec3(Math.cos(angle) * 0.32, 0.12, Math.sin(angle) * 0.32));
                    }
                }
            }
        }

        /** Nine seeded fountains: glowing ballistic heads with fine trails, aligned with the terrain's fractures. */
        private void ejecta(boolean emitParticles) {
            if (impactAge < 2 || impactAge > 76) return;
            for (int i = 0; i < terrain.fractureCount(); i++) {
                float t = impactAge - 2 - i % 3 * 3;
                if (t < 0 || t > 64) continue;
                double speed = 0.6 + terrain.fractureReach(i) / 85;
                double distance = 3 + t * speed;
                double angle = terrain.fractureAngle(i, distance);
                // Their reach follows the fractures'; their arcs are as much taller as the blast is bigger.
                double height = (2 + (1.5 + (i % 3) * 0.2) * t - 0.034 * t * t) * BLAST;
                Vec3 at = onCircle(mark, distance, angle, height);
                Vec3 ground = surface(at);
                if (ground == null || at.y < ground.y) continue;
                float fade = Math.min(1, (64 - t) / 15);
                if (emitParticles) {
                    for (int j = 0, n = Fx.scaled(3); j < n; j++) {
                        Vec3 trail = at.add(jitter(random, 1.6), jitter(random, 1.2), jitter(random, 1.6));
                        particle(j == 0 ? ParticleTypes.CAMPFIRE_COSY_SMOKE : j == 1 ? ParticleTypes.FLAME : ModParticles.GOLD,
                                trail, new Vec3(Math.cos(angle) * 0.05, 0.04, Math.sin(angle) * 0.05));
                    }
                } else {
                    planes.glow(at, (1.5f + i % 3 * 0.4f) * fade, scaled(MOLTEN, fade * 0.65f));
                }
            }
        }

        /** Loaded terrain only. This also anchors effects correctly when terrain damage is disabled. */
        private Vec3 surface(Vec3 at) {
            BlockPos pos = BlockPos.containing(at);
            if (!world().hasChunk(SectionPos.blockToSectionCoord(pos.getX()), SectionPos.blockToSectionCoord(pos.getZ()))) return null;
            int y = world().getHeight(Heightmap.Types.WORLD_SURFACE, pos.getX(), pos.getZ());
            if (y <= world().getMinY()) return null;
            // Under an overhang or in the Nether, the heightmap describes the roof above the spell.
            if (y > mark.y + R * 2) {
                int top = Math.min(world().getMaxY() - 1, Mth.floor(mark.y) + 6);
                int bottom = Math.max(world().getMinY(), Mth.floor(mark.y - SpellTuning.PLANETFALL_CRATER_DEPTH - 12));
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(pos.getX(), top, pos.getZ());
                boolean open = world().getBlockState(cursor).isAir();
                for (int scan = top - 1; scan >= bottom; scan--) {
                    cursor.setY(scan);
                    boolean solid = !world().getBlockState(cursor).isAir();
                    if (solid && open) return new Vec3(at.x, scan + 1.12, at.z);
                    open = !solid;
                }
                return null;
            }
            return new Vec3(at.x, y + 0.12, at.z);
        }

        /** The ground at the mark, for debris: the first solid block under it, else stone. */
        private BlockState ground() {
            BlockPos pos = BlockPos.containing(mark);
            for (int i = 0; i < 8; i++, pos = pos.below()) {
                BlockState state = world().getBlockState(pos);
                if (!state.isAir() && state.getFluidState().isEmpty()) return state;
            }
            return Blocks.STONE.defaultBlockState();
        }

        /**
         * The sky turning to space, the darkening, and the camera: drawn up towards the rift, then to the planet, rolling
         * and widening as it falls, punched at the impact and kicked when the shock front arrives.
         */
        private void screen() {
            float sky = impactAge >= 0 ? Math.max(0, 1 - impactAge / (float) SKY_CLEARS) : ease(age / 40f);
            float far = (float) Mth.clamp(1 - (camera().distanceTo(mark) - 192) / 256, 0, 1);
            SpaceSky.request(sky * far);
            float near = near(mark);
            if (near <= 0) return;
            if (impactAge < 0) {
                float p = progress(), open = riftOpen(age);
                ScreenEffects.tint(0.3f * ease(age / 40f) * near * (1 - p), RIFT_TINT);
                if (fallAge >= 0) {
                    ScreenEffects.tint(0.45f * p * p * near, FIRE_TINT);
                    ScreenEffects.shake((0.12f + 0.55f * p * p) * near);
                    ScreenEffects.fov(1 + 0.22f * p * p * p * near);
                    ScreenEffects.roll((0.6f + 3.4f * p * p) * Mth.sin(age * 0.83f) * Mth.sin(age * 0.29f + 1) * near);
                    ScreenEffects.pull(planetAt, (0.25f + 0.35f * p) * near);
                } else {
                    if (age > riftTicks - 20) ScreenEffects.shake(0.12f * near);
                    ScreenEffects.fov(1 - 0.05f * open * near);
                    ScreenEffects.roll(1.5f * Mth.sin(age * 0.06f) * open * near);
                    ScreenEffects.pull(rift, 0.3f * open * near);
                }
            } else if (impactAge < 85) {
                ScreenEffects.tint(0.36f * Math.max(0, 1 - impactAge / 65f) * near, FIRE_TINT);
                if (impactAge < 2) ScreenEffects.fov(1 - 0.16f * near);
                // The shock front reaching the camera kicks it away from the blast.
                if (Math.abs(impactAge - arrival(camera().distanceTo(mark))) < 3) {
                    Vector3fc left = Minecraft.getInstance().gameRenderer.getMainCamera().leftVector();
                    Vec3 toward = mark.subtract(camera());
                    float side = left.x() * toward.x + left.z() * toward.z > 0 ? -1 : 1;
                    ScreenEffects.shake(0.95f * near);
                    ScreenEffects.fov(1 + 0.1f * near);
                    ScreenEffects.roll(6 * side * near);
                }
                float rumble = (0.2f + 0.13f * Math.abs(Mth.sin(impactAge * 0.21f))) * (1 - impactAge / 85f);
                ScreenEffects.shake(rumble * near);
            }
            if (lifted()) {
                // Weightless: the view drifts and sways.
                ScreenEffects.roll(2.5f * Mth.sin(age * 0.045f));
                ScreenEffects.fov(1.04f + 0.02f * Mth.sin(age * 0.07f));
            }
        }

        /** Whether gravity has failed for this client's own player: lifted by a Planetfall, near this one's mark. */
        private boolean lifted() {
            LocalPlayer player = Minecraft.getInstance().player;
            if (player == null || impactAge >= 0) return false;
            MobEffectInstance levitation = player.getEffect(MobEffects.LEVITATION);
            return levitation != null && levitation.isAmbient() && !levitation.isVisible()
                    && Mth.square(player.getX() - mark.x) + Mth.square(player.getZ() - mark.z) < Mth.square(LIFT + 8);
        }

        /**
         * When the shock front reaches the camera, its boom arrives: light outruns sound, so the flash came first and
         * a long silence may follow it. Played close by, from the mark's direction, so it is as loud as it is late.
         */
        private void boom() {
            Vec3 camera = camera(), toward = mark.subtract(camera);
            double distance = toward.length();
            if (boomed || impactAge < arrival(distance)) return;
            boomed = true;
            Vec3 at = distance < 6 ? mark : camera.add(toward.scale(Math.min(distance, 6 + distance * 0.06) / distance));
            float pitch = 1 - 0.15f * (float) Mth.clamp((distance - SpellTuning.PLANETFALL_IMPACT_RADIUS) / 300, 0, 1);
            Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.PLANETFALL_IMPACT, SoundSource.PLAYERS,
                    2.0f, pitch, SoundInstance.createUnseededRandom(), at.x, at.y, at.z));
            // Close enough, and the boom leaves your ears ringing: a sound inside your head, not out in the world.
            float deafened = (float) Mth.clamp(1 - (distance - 30) / 90, 0, 1);
            if (deafened > 0) {
                Minecraft.getInstance().getSoundManager().play(new SimpleSoundInstance(ModSounds.PLANETFALL_RINGING.location(),
                        SoundSource.PLAYERS, 0.7f * deafened, 1, SoundInstance.createUnseededRandom(), false, 0,
                        SoundInstance.Attenuation.NONE, 0, 0, 0, true));
            }
        }

        @Override public void write(PlanetfallShader.View view, float partialTick) {
            float t = age + partialTick;
            float near = near(mark);
            view.clock.x = t / 20;
            view.relative(view.riftCenter, rift, riftOpen(t));
            view.riftAxis.set((float) heading.x, (float) heading.y, (float) heading.z, RIFT_RADIUS);
            view.planetHeat.w = seed;
            if (impactAge < 0) {
                float p = fallAge < 0 ? 0 : Mth.clamp((fallAge + partialTick) / fallTicks, 0, 1);
                Vec3 center = Planetfall.planetAt(mark, riftOffset, p);
                if (fallAge < 0) {
                    // Still far beyond the rift, slowing as it comes up to it.
                    float left = 1 - Mth.clamp(t / riftTicks, 0, 1);
                    center = center.subtract(heading.scale(APPROACH * left * left));
                }
                view.relative(view.planetCenter, center, R);
                view.planetSpin.set(axis.x, axis.y, axis.z, Mth.lerp(partialTick, spinBefore, spin));
                view.planetHeat.set(p * p, p * p * p, 0, seed);
                float open = riftOpen(t);
                if (fallAge < 0) {
                    view.lens.set(0.3f * open * near, 0, 0, 0);
                    view.grade.set(0.86f, 0.82f, 1.12f, 0.45f * open);
                    view.glow.set(0, 0.35f * open, 0, 0);
                    view.post.set(1, 0.6f, 0.5f, BLAST);
                } else {
                    view.lens.set((0.3f + 0.4f * p * p) * near, 0.6f * p * p * p * near, 0.9f * p * p * p * p * near, 0.6f * p * p);
                    view.grade.set(Mth.lerp(p, 0.86f, 1.15f), Mth.lerp(p, 0.82f, 0.86f), Mth.lerp(p, 1.12f, 0.7f), 0.45f);
                    view.glow.set(0.2f + 1.2f * p * p, 0.35f * open * (1 - p), 0, 0);
                    view.post.set(1, 0.6f + 0.4f * p, 0.5f + 0.3f * p, BLAST);
                }
            } else {
                float ti = impactAge + partialTick;
                float dome = ti < DOME_TICKS ? (float) Math.pow(1 - ti / DOME_TICKS, 1.5) : 0;
                float flash = ti < 8 ? (1 - ti / 8) * (1 - ti / 8) * near : 0;
                float frame = ti >= 0.5f && ti < 3.5f && near > 0.3f ? 1 : 0;
                float heat = Math.max(0, 1 - ti / SKY_CLEARS);
                view.relative(view.shock, mark, (float) shockRadius(ti));
                view.shockState.set(dome, flash, frame, heat);
                view.lens.set(0.75f * Math.max(0, 1 - ti / 60) * near, 1.6f * (float) Math.exp(-ti / 12) * near, 0, 0.8f * heat);
                view.grade.set(1.2f, 0.86f, 0.66f, 0.5f * Math.max(0, 1 - ti / 100));
                float blast = Math.max(0, 1 - ti / 30);
                view.glow.set(0, 0, 2.2f * blast * blast, 1.4f * heat);
                // The fire burns out in a few seconds; the smoke lingers, then everything fades with the pass.
                float lingering = Mth.clamp((CLOUD_TICKS - ti) / 80, 0, 1);
                view.blast.set(ti, (float) Math.exp(-ti / 30), Math.min(1, ti / 4) * lingering,
                        Math.min(1, ti / 3) * Mth.clamp((170 - ti) / 70, 0, 1));
                view.post.set(lingering, 0.9f * lingering, 0.6f * lingering, BLAST);
            }
        }

        @Override public void releaseLocal() {
            if (planet != null) planet.remove();
            planet = null;
            if (groundDebris != null) groundDebris.remove();
            groundDebris = null;
        }

        @Override public void release() {
            planes.clear();
            for (EffectHandle<?> handle : new EffectHandle<?>[] {riftRunes, groundRunes}) {
                if (handle != null) SpellLighting.cleanup(handle::remove);
            }
        }
    }
    /**
     * A burning shard flung from the planet's face: a tumbling, glowing block wrapped in fire, streaking down with a
     * trail of flame and smoke and lighting the ground as it comes. Its blast and crater are the server's explosion.
     * The block is a display that exists only on this client, like the planet's.
     */
    private static final class Shard implements Fx.Animation {
        private static final Brightness GLOWING = new Brightness(15, 15);
        private static int nextId = -0x4000_0000;
        final ClientLevel level;
        final Vec3 from, to;
        final float size;
        final int flight;
        final RandomSource random;
        final Vector3f tumbleAxis;
        final Display.BlockDisplay display;
        final Fx.Planes planes = new Fx.Planes();
        Vec3 at;
        int age;

        Shard(ClientLevel level, SpellEffect effect) {
            this.level = level;
            this.from = effect.position();
            this.to = from.add(effect.direction());
            this.size = Math.max(0.3f, effect.power());
            this.flight = Math.max(1, effect.age());
            this.random = RandomSource.create(effect.sourceId());
            this.tumbleAxis = new Vector3f(random.nextFloat() - 0.5f, random.nextFloat() - 0.5f, random.nextFloat() - 0.5f).normalize();
            this.at = from;
            display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            display.setId(nextId--);
            display.setBlockState((random.nextInt(3) == 0 ? Blocks.CRYING_OBSIDIAN : Blocks.MAGMA_BLOCK).defaultBlockState());
            display.setBrightnessOverride(GLOWING);
            display.setViewRange(8);
            display.setPos(from.x, from.y, from.z);
            level.addEntity(display);
            pose();
        }

        @Override public boolean tick() {
            age++;
            Vec3 last = at;
            // Gathering speed as it falls.
            float t = Math.min(1, age / (float) flight);
            at = from.lerp(to, t * (0.55f + 0.45f * t));
            pose();
            SpellLighting.guarded(this::animate);
            if (particlesVisible(at)) trail(last);
            if (age < flight) return false;
            if (particlesVisible(to)) strike();
            ScreenEffects.shake(0.3f * Fx.proximity(to));
            return true;
        }

        /** The display stays where it started and is moved by its transformation, which the renderer interpolates. */
        private void pose() {
            Quaternionf turn = new Quaternionf().rotationAxis(age * 0.35f, tumbleAxis);
            float side = 1.4f * size;
            Vector3f corner = turn.transform(new Vector3f(-side / 2));
            Vec3 offset = at.subtract(from);
            display.setTransformation(new Transformation(new Vector3f((float) offset.x, (float) offset.y, (float) offset.z).add(corner),
                    turn, new Vector3f(side), null));
            display.setTransformationInterpolationDelay(0);
            if (age > 0) display.setTransformationInterpolationDuration(1);
        }

        private void animate() {
            Vec3 back = from.subtract(to).normalize();
            planes.begin();
            planes.glow(at, 1.8f * size, scaled(MOLTEN, 0.9f));
            for (int i = 1; i <= 3; i++) planes.glow(at.add(back.scale(i * 2.2 * size)), (3.2f - i * 0.6f) * size, scaled(FIRE, 0.5f / i));
            planes.end();
        }

        private void trail(Vec3 last) {
            for (int i = 0, n = Fx.scaled(6); i < n; i++) {
                Vec3 point = last.lerp(at, random.nextDouble()).add(jitter(random, size), jitter(random, size), jitter(random, size));
                particle(i % 3 == 0 ? ParticleTypes.LARGE_SMOKE : i == 1 ? ParticleTypes.LAVA : ParticleTypes.FLAME, point, Vec3.ZERO);
            }
        }

        private void strike() {
            if (!Minecraft.getInstance().options.hideLightningFlash().get()) particle(FLARE, to.add(0, 1, 0), Vec3.ZERO);
            for (int i = 0, n = Fx.scaled(3); i < n; i++) {
                Vec3 out = upwards(random, 0.5);
                particle(ModParticles.PLANETFALL_FIRE, to.add(out.scale(1.5)), out.scale(0.25));
            }
            for (int i = 0, n = Fx.scaled(16); i < n; i++) {
                particle(i % 2 == 0 ? ParticleTypes.LAVA : ModParticles.GOLD, to.add(0, 0.5, 0), upwards(random, 0.3).scale(0.4 + random.nextDouble() * 0.6));
            }
        }

        @Override public void releaseLocal() {
            level.removeEntity(display.getId(), Entity.RemovalReason.DISCARDED);
        }

        @Override public void release() {
            planes.clear();
        }
    }
}
