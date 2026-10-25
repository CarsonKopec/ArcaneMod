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
import java.util.ArrayList;
import java.util.List;
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
 * Black holes and stopped time, drawn straight into the finished frame by one full-screen pass
 * ({@code shaders/core/spacetime.fsh}) run from Nebulon's post-processing hook, after the world is drawn and before
 * the hand and HUD. Like {@link PlanetfallShader}'s it reads the world's depth, so what lies in front of a black hole
 * hides it; unlike that one it has no glow, which keeps it clear of the chain of passes that hung Intel's drivers.
 *
 * <p>Each frame, the frame is copied ({@code planetfall_copy}, by a draw, for the reason given there), and the pass
 * draws over the frame from the copy: it traces the light bent past each black hole and reads the copy again where
 * that light comes from, and drains the colour from what lies inside each Chrono Field.
 *
 * <p>Any number of spells can ask for it at once ({@link #show}, every tick they want it); up to {@link View#SLOTS}
 * black holes and as many fields are drawn. It costs nothing while none do. With a shader pack or VulkanMod, or after
 * the pass has failed once, {@link #available} is false and spells draw themselves without it.
 */
final class SpaceTimeShader {
    private static final Identifier ID = ArcaneSpells.id("spacetime");
    private static final Identifier VERTEX = Identifier.fromNamespaceAndPath("nebulon", "core/fullscreen_triangle");
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final boolean VULKAN_MOD = FabricLoader.getInstance().isModLoaded("vulkanmod");

    private static ShaderMaterial copy, effect;
    /** The frame as the world drew it. */
    private static TextureTarget frame;
    private static DynamicUniformBlock<View> uniforms;
    private static PostPassHandle handle;
    private static List<Scene> requested = new ArrayList<>(), scenes = List.of();
    private static final View VIEW = new View();
    /** Set by the first failure; the pass then stays off until the game restarts. */
    private static boolean failed;

    private SpaceTimeShader() {
    }

    /** Something to draw: it adds its black holes or fields to the view for this frame, {@code partialTick} of the way to the next tick. */
    interface Scene {
        void write(View view, float partialTick);
    }

    /** Whether the pass can run here: as for {@link PlanetfallShader#available}. */
    static boolean available() {
        if (failed || SpellLighting.disabled() || VULKAN_MOD) return false;
        try {
            return !NebulonRenderSystem.environment().irisShaderPackActive();
        } catch (RuntimeException | LinkageError e) {
            fail(e);
            return false;
        }
    }

    /** Asks for {@code scene} to be drawn this tick. */
    static void show(Scene scene) {
        requested.add(scene);
    }

    /** After every spell has asked: starts the pass, or stops it once nothing wants it. */
    static void tick() {
        scenes = available() ? requested : List.of();
        requested = new ArrayList<>();
        if (!scenes.isEmpty() && handle == null) {
            SpellLighting.cleanup(() -> handle = NebulonPostProcessing.register(ID, RenderStage.END_MAIN, 90, SpaceTimeShader::render));
        } else if (scenes.isEmpty() && handle != null) {
            close();
        }
    }

    /** Stops the pass. Safe from any thread: a disconnect clears effects from the network thread. */
    static void clear() {
        requested = new ArrayList<>();
        scenes = List.of();
        if (RenderSystem.isOnRenderThread()) close();
        else Minecraft.getInstance().execute(SpaceTimeShader::close);
    }

    private static void close() {
        PostPassHandle closing = handle;
        handle = null;
        if (closing != null) SpellLighting.cleanup(closing::close);
        TextureTarget target = frame;
        if (target != null) SpellLighting.cleanup(target::destroyBuffers);
        frame = null;
    }

    private static void fail(Throwable e) {
        if (failed) return;
        failed = true;
        ArcaneSpells.LOGGER.error("The black hole and Chrono Field screen pass failed; they will draw themselves without it until the game restarts", e);
    }

    /** Runs inside world rendering, drawing over the whole frame. Any failure is logged once and switches the pass off. */
    private static void render(PostPassContext context) {
        try {
            if (copy == null) build();
            RenderTarget main = context.main();
            frame = sized(frame, main.width, main.height);
            Minecraft client = Minecraft.getInstance();
            float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            VIEW.reset(client.gameRenderer.getMainCamera(), partialTick);
            for (Scene scene : scenes) scene.write(VIEW, partialTick);
            uniforms.beginFrame();
            GpuBufferSlice view = uniforms.upload(VIEW);
            GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(IDENTITY, new Vector4f(1), new Vector3f(), IDENTITY);
            var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
            var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            TextureTarget copied = frame;
            draw(copied, copy, transforms, pass -> pass.bindTexture("InSampler", main.getColorTextureView(), nearest));
            draw(main, effect, transforms, pass -> {
                pass.setUniform("SpaceTimeView", view);
                pass.bindTexture("InputColor", copied.getColorTextureView(), linear);
                pass.bindTexture("SceneDepth", main.getDepthTextureView(), nearest);
            });
        } catch (RuntimeException | LinkageError e) {
            fail(e);
        }
    }

    private static void build() {
        copy = fullScreen("spacetime_copy", "planetfall_copy").sampler("InSampler").build();
        effect = fullScreen("spacetime", "spacetime").sampler("InputColor").sampler("SceneDepth").uniformBlock("SpaceTimeView").build();
        for (ShaderMaterial built : new ShaderMaterial[] {copy, effect}) {
            built.precompile();
            // A shader that fails to compile draws nothing at all, rather than throwing: treat it as a failure.
            if (!RenderSystem.getDevice().precompilePipeline(built.pipeline()).isValid()) {
                throw new IllegalStateException("The space-time shader did not compile; see the log above");
            }
        }
        uniforms = new DynamicUniformBlock<>("Space-time view", View.BYTES, 4, View::encode);
    }

    /** A pass over the whole screen, drawn with Nebulon's full-screen triangle, named {@code name} and drawn by {@code shader}. */
    private static ShaderMaterial.Builder fullScreen(String name, String shader) {
        return ShaderMaterial.builder(ArcaneSpells.id(name))
                .shaders(VERTEX, ArcaneSpells.id("core/" + shader))
                .geometry(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .transparency(ShaderMaterial.Transparency.OPAQUE)
                .depth(DepthTestFunction.NO_DEPTH_TEST, false)
                .cull(false);
    }

    /** {@code target}, made or resized to {@code width} by {@code height} (at least 1 by 1). */
    private static TextureTarget sized(TextureTarget target, int width, int height) {
        width = Math.max(1, width);
        height = Math.max(1, height);
        if (target == null) return new TextureTarget("Space-time frame", width, height, false);
        if (target.width != width || target.height != height) target.resize(width, height);
        return target;
    }

    private static void draw(RenderTarget target, ShaderMaterial material, GpuBufferSlice transforms, Consumer<RenderPass> inputs) {
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Space-time", target.getColorTextureView(), OptionalInt.empty())) {
            pass.setPipeline(material.pipeline());
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            inputs.accept(pass);
            pass.draw(0, 3);
        }
    }

    /**
     * Everything the shader is told about one frame. Positions are relative to the camera, so they keep their
     * precision far from the world's origin.
     */
    static final class View {
        /** Black holes, and Chrono Fields, drawn at once; any more are left out. */
        static final int SLOTS = 4;
        /** The {@code SpaceTimeView} block: a matrix and 1 + 5 * {@link #SLOTS} vectors, std140. */
        static final int BYTES = 64 + (1 + 5 * SLOTS) * 16;

        /** Turns a camera-relative position into view space: the camera's rotation, as the world is drawn with. */
        final Matrix4f viewMatrix = new Matrix4f();
        Vec3 camera = Vec3.ZERO;
        /** x: seconds; z: Screen Effect Scale. */
        final Vector4f clock = new Vector4f();
        final Vector4f[] holeCenter = vectors(), holeDisc = vectors(), holeState = vectors();
        final Vector4f[] fieldCenter = vectors(), fieldState = vectors();
        private int holes, fields;

        private static Vector4f[] vectors() {
            Vector4f[] vectors = new Vector4f[SLOTS];
            for (int i = 0; i < SLOTS; i++) vectors[i] = new Vector4f();
            return vectors;
        }

        private void reset(Camera view, float partialTick) {
            camera = view.position();
            viewMatrix.rotation(view.rotation().conjugate(new Quaternionf()));
            Minecraft client = Minecraft.getInstance();
            float ticks = client.level == null ? 0 : client.level.getGameTime() + partialTick;
            clock.set(ticks / 20, 0, client.options.screenEffectScale().get().floatValue(), 0);
            for (Vector4f[] slots : new Vector4f[][] {holeCenter, holeDisc, holeState, fieldCenter, fieldState}) {
                for (Vector4f slot : slots) slot.zero();
            }
            holes = fields = 0;
        }

        /**
         * A black hole at {@code center}, its event horizon {@code horizon} blocks across from its centre. Its disc turns
         * anticlockwise round {@code axis} out to {@code outer} blocks, {@code brightness} from 0 to 1, its inner edge
         * having turned {@code turned} radians; {@code seed} varies its streaks.
         */
        void hole(Vec3 center, float horizon, Vec3 axis, float outer, float brightness, float turned, float seed) {
            if (holes >= SLOTS || horizon <= 0) return;
            relative(holeCenter[holes], center, horizon);
            holeDisc[holes].set((float) axis.x, (float) axis.y, (float) axis.z, outer);
            holeState[holes].set(brightness, turned, 0, seed);
            holes++;
        }

        /**
         * A Chrono Field at {@code center}, {@code radius} blocks across from its centre, with {@code drain} of the colour gone
         * from what it holds, 0 to 1; {@code front}, if above 0, is how far out from the centre colour has come back.
         */
        void field(Vec3 center, float radius, float drain, float front) {
            if (fields >= SLOTS || radius <= 0) return;
            relative(fieldCenter[fields], center, radius);
            fieldState[fields].set(drain, front, 0, 0);
            fields++;
        }

        /** {@code at} relative to the camera, into {@code out}'s xyz. */
        private void relative(Vector4f out, Vec3 at, float w) {
            out.set((float) (at.x - camera.x), (float) (at.y - camera.y), (float) (at.z - camera.z), w);
        }

        private static void encode(Std140Builder out, View view) {
            out.putMat4f(view.viewMatrix).putVec4(view.clock);
            for (Vector4f[] slots : new Vector4f[][] {view.holeCenter, view.holeDisc, view.holeState, view.fieldCenter, view.fieldState}) {
                for (Vector4f slot : slots) out.putVec4(slot);
            }
        }
    }
}
