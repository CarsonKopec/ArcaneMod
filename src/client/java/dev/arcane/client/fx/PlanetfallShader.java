package dev.arcane.client.fx;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import de.nexusrealms.nebulon.api.render.client.DynamicUniformBlock;
import de.nexusrealms.nebulon.api.render.client.NebulonRenderSystem;
import de.nexusrealms.nebulon.api.render.client.RenderStage;
import de.nexusrealms.nebulon.api.render.client.ShaderMaterial;
import de.nexusrealms.nebulon.api.render.client.post.NebulonPostProcessing;
import de.nexusrealms.nebulon.api.render.client.post.PostPassContext;
import de.nexusrealms.nebulon.api.render.client.post.PostPassHandle;
import dev.arcane.ArcaneSpells;
import java.util.Arrays;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Planetfall drawn straight into the finished frame, by a chain of full-screen shader passes run from Nebulon's
 * post-processing hook after the world is drawn and before the hand and HUD. They read the world's depth, so what they
 * draw sits properly in the world: terrain in front hides it, and it hides what is behind it. They draw what flat
 * shapes and block displays can't: deep space over the sky, the rift as a black hole torn into another universe, the
 * planet itself (raytraced, with its air and the plasma of its entry), its light on the ground, the shock dome, the
 * fireball and mushroom cloud, and what happens to the eye.
 *
 * <p>Each frame, in order ({@code shaders/core/}), after {@code planetfall_noise} has drawn the tile of random values
 * the others' noise is read from:
 * <ol>
 *   <li>{@code planetfall_copy}: a copy of the frame, for the rest to read while the last draws over it. Copying
 *       textures directly ({@code copyTextureToTexture}, as Nebulon's compositor and scene snapshots do) loses the
 *       colour on the tested setup, so the frame is only ever copied with a draw.</li>
 *   <li>{@code planetfall_volume}: the volumes (the planet's burning sheath, the fireball, cloud and base surge),
 *       raymarched at half size.</li>
 *   <li>{@code planetfall}: everything else, over the frame, in high dynamic range: light can be many times brighter
 *       than white. There are no floating-point textures to hold it, so it is RGBM-encoded.</li>
 *   <li>{@code planetfall_bloom_extract}, {@code _down}, {@code _up}: the glow of everything brighter than white, blurred
 *       down a chain of ever smaller copies and added back up it. Not on Intel's integrated graphics: see {@link #glow}.</li>
 *   <li>{@code planetfall_streak}: that glow smeared sideways, as an anamorphic lens does.</li>
 *   <li>{@code planetfall_composite}: all of it together, highlights rolled off rather than clipped, with what the
 *       moment does to the eye, written back over the frame.</li>
 * </ol>
 *
 * <p>The passes only run while a spell asks for them ({@link #show}, every tick it wants them), so they cost nothing the
 * rest of the time. With a shader pack or VulkanMod, or after a pass has failed once, {@link #available} is false and
 * Planetfall draws itself the older way, with shapes and block displays.
 */
final class PlanetfallShader {
    private static final Identifier ID = ArcaneSpells.id("planetfall");
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("nebulon", "core/fullscreen_triangle");
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final boolean VULKAN_MOD = FabricLoader.getInstance().isModLoaded("vulkanmod");
    /** The {@code PlanetfallView} block: a matrix and thirteen vectors, std140. */
    private static final int VIEW_BYTES = 64 + 13 * 16;
    private static final int BLOOM_LEVELS = 5;
    /** Spreads, in texels, of the lens streak's passes: each blurs the last further. */
    private static final float[] STREAK_SPREADS = {1, 4, 12};

    private static ShaderMaterial noise, copy, volume, effect, extract, down, up, streak, composite;
    /** The frame as the world drew it; the volumes, at half size; and the frame drawn over, in high dynamic range. */
    private static TextureTarget frame, volumes, hdr;
    /** Random values the shaders' noise is read from, drawn once whenever it is made. */
    private static TextureTarget noiseTile;
    /** The bloom's chain, each level half the size of the last, and the streak's two buffers. */
    private static final TextureTarget[] BLOOM = new TextureTarget[BLOOM_LEVELS];
    private static final TextureTarget[] STREAK = new TextureTarget[2];
    private static DynamicUniformBlock<View> uniforms;
    private static DynamicUniformBlock<Vector4f> steps;
    private static PostPassHandle handle;
    private static Scene requested, scene;
    private static int requestedPriority;
    private static final View VIEW = new View();
    /** Set by the first failure; the pass then stays off until the game restarts. */
    private static boolean failed;
    /** Whether to draw the glow, the bloom and lens streaks: decided once, on the first frame drawn. */
    private static Boolean glow;

    private PlanetfallShader() {
    }

    /** Something to draw: it fills in the view for this frame, {@code partialTick} of the way to the next tick. */
    interface Scene {
        void write(View view, float partialTick);
    }

    /**
     * Whether the pass can run here; if not, spells fall back to drawing themselves with shapes and displays. Not with
     * VulkanMod, which replaces the OpenGL renderer this pass is drawn through.
     */
    static boolean available() {
        if (failed || SpellLighting.disabled() || VULKAN_MOD) return false;
        try {
            return !NebulonRenderSystem.environment().irisShaderPackActive();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Asks for {@code scene} to be drawn this tick; when several ask, the highest priority wins. */
    static void show(Scene scene, int priority) {
        if (requested == null || priority > requestedPriority) {
            requested = scene;
            requestedPriority = priority;
        }
    }

    /** After every spell has asked: starts the pass, or stops it once nothing wants it. */
    static void tick() {
        scene = available() ? requested : null;
        requested = null;
        requestedPriority = Integer.MIN_VALUE;
        if (scene != null && handle == null) {
            SpellLighting.cleanup(() -> handle = NebulonPostProcessing.register(ID, RenderStage.END_MAIN, 100, PlanetfallShader::render));
        } else if (scene == null && handle != null) {
            close();
        }
    }

    /** Stops the passes. Safe from any thread: a disconnect clears effects from the network thread. */
    static void clear() {
        requested = scene = null;
        if (RenderSystem.isOnRenderThread()) close();
        else Minecraft.getInstance().execute(PlanetfallShader::close);
    }

    private static void close() {
        PostPassHandle closing = handle;
        handle = null;
        if (closing != null) SpellLighting.cleanup(closing::close);
        for (TextureTarget target : targets()) {
            if (target != null) SpellLighting.cleanup(target::destroyBuffers);
        }
        frame = volumes = hdr = noiseTile = null;
        Arrays.fill(BLOOM, null);
        Arrays.fill(STREAK, null);
    }

    private static TextureTarget[] targets() {
        return new TextureTarget[] {frame, volumes, hdr, noiseTile, BLOOM[0], BLOOM[1], BLOOM[2], BLOOM[3], BLOOM[4], STREAK[0], STREAK[1]};
    }

    private static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        ArcaneSpells.LOGGER.error("Planetfall's screen pass failed; it will draw itself without it until the game restarts", e);
    }

    /** Runs inside world rendering, drawing over the whole frame. Any failure is logged once and switches the passes off. */
    private static void render(PostPassContext context) {
        try {
            if (copy == null) build();
            RenderTarget main = context.main();
            int width = main.width, height = main.height;
            frame = sized(frame, "frame", width, height);
            volumes = sized(volumes, "volumes", width / 2, height / 2);
            hdr = sized(hdr, "light", width, height);
            boolean glowing = glow();
            if (glowing) {
                for (int i = 0; i < BLOOM_LEVELS; i++) BLOOM[i] = sized(BLOOM[i], "bloom " + i, width >> (i + 1), height >> (i + 1));
                for (int i = 0; i < STREAK.length; i++) STREAK[i] = sized(STREAK[i], "streak " + i, width >> 2, height >> 2);
            }

            Minecraft client = Minecraft.getInstance();
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            VIEW.reset(client.gameRenderer.getMainCamera());
            Scene current = scene;
            if (current != null) current.write(VIEW, partialTick);
            if (!glowing) VIEW.post.set(0, 0, VIEW.post.z, VIEW.post.w);
            uniforms.beginFrame();
            steps.beginFrame();
            GpuBufferSlice view = uniforms.upload(VIEW);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(IDENTITY, new Vector4f(1), new Vector3f(), IDENTITY);
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            var tiled = RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR);
            if (noiseTile == null) {
                noiseTile = new TextureTarget("Planetfall noise", 256, 256, false);
                draw(noiseTile, noise, transforms, pass -> { });
            }
            TextureTarget tile = noiseTile;

            draw(frame, copy, transforms, pass -> pass.bindTexture("InSampler", main.getColorTextureView(), nearest));
            draw(volumes, volume, transforms, pass -> {
                pass.setUniform("PlanetfallView", view);
                pass.bindTexture("SceneDepth", main.getDepthTextureView(), nearest);
                pass.bindTexture("NoiseTile", tile.getColorTextureView(), tiled);
            });
            draw(hdr, effect, transforms, pass -> {
                pass.setUniform("PlanetfallView", view);
                pass.bindTexture("InputColor", frame.getColorTextureView(), linear);
                pass.bindTexture("SceneDepth", main.getDepthTextureView(), nearest);
                pass.bindTexture("Volume", volumes.getColorTextureView(), linear);
                pass.bindTexture("NoiseTile", tile.getColorTextureView(), tiled);
            });
            // Without the glow its strengths are 0 (above), so the composite adds none, and any texture can stand in.
            TextureTarget bloomed = hdr, streaked = hdr;
            if (glowing) {
                draw(BLOOM[0], extract, transforms, pass -> pass.bindTexture("InSampler", hdr.getColorTextureView(), nearest));
                for (int i = 1; i < BLOOM_LEVELS; i++) {
                    TextureTarget source = BLOOM[i - 1];
                    draw(BLOOM[i], down, transforms, pass -> pass.bindTexture("InSampler", source.getColorTextureView(), linear));
                }
                TextureTarget streakSource = BLOOM[1];
                for (int i = 0; i < STREAK_SPREADS.length; i++) {
                    TextureTarget source = i == 0 ? streakSource : STREAK[(i + 1) % 2];
                    GpuBufferSlice spread = steps.upload(new Vector4f(STREAK_SPREADS[i], 0, 0, 0));
                    draw(STREAK[i % 2], streak, transforms, pass -> {
                        pass.setUniform("PlanetfallStep", spread);
                        pass.bindTexture("InSampler", source.getColorTextureView(), linear);
                    });
                }
                streaked = STREAK[(STREAK_SPREADS.length - 1) % 2];
                for (int i = BLOOM_LEVELS - 1; i > 0; i--) {
                    TextureTarget source = BLOOM[i];
                    draw(BLOOM[i - 1], up, transforms, pass -> pass.bindTexture("InSampler", source.getColorTextureView(), linear));
                }
                bloomed = BLOOM[0];
            }
            TextureTarget bloom = bloomed, lensStreak = streaked;
            draw(main, composite, transforms, pass -> {
                pass.setUniform("PlanetfallView", view);
                pass.bindTexture("InSampler", hdr.getColorTextureView(), nearest);
                pass.bindTexture("Bloom", bloom.getColorTextureView(), linear);
                pass.bindTexture("Streak", lensStreak.getColorTextureView(), linear);
            });
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    /**
     * Whether to draw the glow. Not on Intel's integrated graphics: there, the bloom's chain of passes hung the GPU
     * during a session's first Planetfall, about two runs in three, until Windows reset the driver and the game lost its
     * window (UHD Graphics on an i5-11400H, driver 30.0.101.1340). Without the glow, eighteen runs in a row were clean;
     * nothing else in the pass needed to change.
     */
    private static boolean glow() {
        if (glow == null) {
            var device = RenderSystem.getDevice();
            String renderer = device.getRenderer();
            boolean intel = (device.getVendor() + " " + renderer).toLowerCase(Locale.ROOT).contains("intel");
            // Arc cards are discrete, with drivers of their own.
            glow = !intel || renderer.contains("Arc");
            if (!glow) ArcaneSpells.LOGGER.info("Planetfall draws without its glow on {}, whose drivers have hung drawing it", renderer);
        }
        return glow;
    }

    private static void build() {
        noise = fullScreen("noise").build();
        copy = fullScreen("copy").sampler("InSampler").build();
        volume = fullScreen("volume").sampler("SceneDepth").sampler("NoiseTile").uniformBlock("PlanetfallView").build();
        effect = fullScreen(null).sampler("InputColor").sampler("SceneDepth").sampler("Volume").sampler("NoiseTile")
                .uniformBlock("PlanetfallView").build();
        extract = fullScreen("bloom_extract").sampler("InSampler").build();
        down = fullScreen("bloom_down").sampler("InSampler").build();
        up = fullScreen("bloom_up").sampler("InSampler").transparency(ShaderMaterial.Transparency.ADDITIVE).build();
        streak = fullScreen("streak").sampler("InSampler").uniformBlock("PlanetfallStep").build();
        composite = fullScreen("composite").sampler("InSampler").sampler("Bloom").sampler("Streak").uniformBlock("PlanetfallView").build();
        for (ShaderMaterial built : new ShaderMaterial[] {noise, copy, volume, effect, extract, down, up, streak, composite}) {
            built.precompile();
            // A shader that fails to compile draws nothing at all, rather than throwing: treat it as a failure.
            if (!RenderSystem.getDevice().precompilePipeline(built.pipeline()).isValid()) {
                throw new IllegalStateException("A Planetfall shader did not compile; see the log above");
            }
        }
        uniforms = new DynamicUniformBlock<>("Planetfall view", VIEW_BYTES, 4, View::encode);
        steps = new DynamicUniformBlock<>("Planetfall step", 16, 4, (out, step) -> out.putVec4(step));
    }

    /** A pass over the whole screen, drawn with Nebulon's full-screen triangle; its shader is {@code planetfall_<stage>}. */
    private static ShaderMaterial.Builder fullScreen(String stage) {
        String name = stage == null ? "planetfall" : "planetfall_" + stage;
        return ShaderMaterial.builder(ArcaneSpells.id(name))
                .shaders(VERTEX, ArcaneSpells.id("core/" + name))
                .geometry(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .transparency(ShaderMaterial.Transparency.OPAQUE)
                .depth(DepthTestFunction.NO_DEPTH_TEST, false)
                .cull(false);
    }

    /** {@code target}, made or resized to {@code width} by {@code height} (at least 1 by 1). */
    private static TextureTarget sized(TextureTarget target, String label, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (target == null) return new TextureTarget("Planetfall " + label, width, height, false);
        if (target.width != width || target.height != height) target.resize(width, height);
        return target;
    }

    private static void draw(RenderTarget target, ShaderMaterial material, GpuBufferSlice transforms, Consumer<RenderPass> inputs) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Planetfall", target.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(material.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            inputs.accept(pass);
            pass.draw(0, 3);
        }
    }

    /**
     * Everything the shader is told about one frame. Positions are relative to the camera, so they keep their
     * precision far from the world's origin. With no scene it draws nothing but the frame itself.
     */
    static final class View {
        /** Turns a camera-relative position into view space: the camera's rotation, as the world is drawn with. */
        final Matrix4f viewMatrix = new Matrix4f();
        Vec3 camera = Vec3.ZERO;
        /** x: seconds; y: how far the sky is to space; z: Screen Effect Scale; w: 1 if flashes are allowed. */
        final Vector4f clock = new Vector4f();
        /** xyz: the rift's centre; w: how open it is, 0 to 1. */
        final Vector4f riftCenter = new Vector4f();
        /** xyz: the rift's facing, out of it towards the mark; w: its radius when fully open. */
        final Vector4f riftAxis = new Vector4f();
        /** xyz: the planet's centre; w: its radius, 0 for no planet. */
        final Vector4f planetCenter = new Vector4f();
        /** xyz: the planet's spin axis; w: how far it has turned, in radians. */
        final Vector4f planetSpin = new Vector4f();
        /** x: the heat of its entry, 0 to 1; y: how far its crust has split, 0 to 1; z: unused; w: the seed. */
        final Vector4f planetHeat = new Vector4f();
        /** xyz: the impact point; w: the shock front's radius, 0 for none. */
        final Vector4f shock = new Vector4f();
        /** x: the shock dome's brightness; y: the impact's flash; z: the impact frame; w: the crater's heat. */
        final Vector4f shockState = new Vector4f();
        /** x: vignette; y: chromatic aberration; z: rush towards the planet; w: heat haze. */
        final Vector4f lens = new Vector4f();
        /** rgb: a colour grade to multiply the frame by; a: how much of it. */
        final Vector4f grade = new Vector4f();
        /** How brightly the ground is lit by x: the planet; y: the rift; z: the impact's flash; w: the crater's heart. */
        final Vector4f glow = new Vector4f();
        /** x: ticks since the impact, below 0 before it; y: the fireball's fire; z: the cloud's smoke; w: the surge's dust. */
        final Vector4f blast = new Vector4f();
        /** x: bloom; y: lens streaks; z: film grain. */
        final Vector4f post = new Vector4f();

        private void reset(Camera view) {
            camera = view.position();
            viewMatrix.rotation(view.rotation().conjugate(new Quaternionf()));
            Minecraft client = Minecraft.getInstance();
            clock.set(0, SpaceSky.strength(client.getDeltaTracker().getGameTimeDeltaPartialTick(true)),
                    client.options.screenEffectScale().get().floatValue(), client.options.hideLightningFlash().get() ? 0 : 1);
            riftCenter.zero();
            riftAxis.set(0, -1, 0, 0);
            planetCenter.zero();
            planetSpin.set(0, 1, 0, 0);
            planetHeat.zero();
            shock.zero();
            shockState.zero();
            lens.zero();
            grade.set(1, 1, 1, 0);
            glow.zero();
            blast.set(-1, 0, 0, 0);
            post.zero();
        }

        /** {@code at} relative to the camera, into {@code out}'s xyz. */
        void relative(Vector4f out, Vec3 at, float w) {
            out.set((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), w);
        }

        private static void encode(Std140Builder out, View view) {
            out.putMat4f(view.viewMatrix).putVec4(view.clock).putVec4(view.riftCenter).putVec4(view.riftAxis)
                    .putVec4(view.planetCenter).putVec4(view.planetSpin).putVec4(view.planetHeat).putVec4(view.shock)
                    .putVec4(view.shockState).putVec4(view.lens).putVec4(view.grade).putVec4(view.glow)
                    .putVec4(view.blast).putVec4(view.post);
        }
    }
}
