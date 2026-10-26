package dev.arcane.client.fx;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import de.nexusrealms.nebulon.api.render.client.DynamicUniformBlock;
import de.nexusrealms.nebulon.api.render.client.ShaderMaterial;
import de.nexusrealms.nebulon.api.render.client.post.PostPassContext;
import dev.arcane.ArcaneSpells;
import dev.leyline.client.fx.NebulonGuard;
import dev.leyline.client.screen.ScreenPass;
import dev.leyline.client.screen.ScreenPasses;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

/**
 * Black holes and stopped time, drawn straight into the finished frame by one full-screen pass
 * ({@code shaders/core/spacetime.fsh}), a Leyline {@link ScreenPass} run after the world is drawn and before the hand
 * and HUD. Like {@link PlanetfallShader}'s it reads the world's depth, so what lies in front of a black hole
 * hides it; unlike that one it has no glow, which keeps it clear of the chain of passes that hung Intel's drivers.
 *
 * <p>Each frame, the frame is copied ({@link ScreenPasses#copy}), and the pass draws over the frame from the copy: it
 * traces the light bent past each black hole and reads the copy again where that light comes from, drains the colour
 * from what lies inside each Chrono Field, and lays its clock's hands on the ground.
 *
 * <p>Any number of spells can ask for it at once ({@link #show}, every tick they want it); up to {@link View#SLOTS}
 * black holes and as many fields are drawn. It costs nothing while none do. With a shader pack or VulkanMod, or after
 * the pass has failed once, {@link #available} is false and spells draw themselves without it.
 */
final class SpaceTimeShader {
    private static final ScreenPass PASS = new ScreenPass(ArcaneSpells.id("spacetime"), 90, SpaceTimeShader::render,
            SpaceTimeShader::release, "The black hole and Chrono Field screen pass");

    private static ShaderMaterial effect;
    /** The frame as the world drew it. */
    private static TextureTarget frame;
    private static DynamicUniformBlock<View> uniforms;
    private static List<Scene> requested = new ArrayList<>(), scenes = List.of();
    private static final View VIEW = new View();

    private SpaceTimeShader() {
    }

    /** Something to draw: it adds its black holes or fields to the view for this frame, {@code partialTick} of the way to the next tick. */
    interface Scene {
        void write(View view, float partialTick);
    }

    /** Whether the pass can run here ({@link ScreenPass#available}); if not, black holes and fields draw themselves without it. */
    static boolean available() {
        return PASS.available();
    }

    /** Asks for {@code scene} to be drawn this tick. */
    static void show(Scene scene) {
        requested.add(scene);
    }

    /** After every spell has asked: starts the pass, or stops it once nothing wants it. */
    static void tick() {
        scenes = available() ? requested : List.of();
        requested = new ArrayList<>();
        PASS.run(!scenes.isEmpty());
    }

    /** Stops the pass. Safe from any thread: a disconnect clears effects from the network thread. */
    static void clear() {
        requested = new ArrayList<>();
        scenes = List.of();
        PASS.stop();
    }

    private static void release() {
        TextureTarget target = frame;
        if (target != null) NebulonGuard.cleanup(target::destroyBuffers);
        frame = null;
    }

    /** Runs inside world rendering, drawing over the whole frame. */
    private static void render(PostPassContext context) {
        if (effect == null) build();
        RenderTarget main = context.main();
        frame = ScreenPasses.sized(frame, "Space-time frame", main.width, main.height);
        Minecraft client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        VIEW.reset(client.gameRenderer.getMainCamera(), partialTick);
        for (Scene scene : scenes) scene.write(VIEW, partialTick);
        uniforms.beginFrame();
        GpuBufferSlice view = uniforms.upload(VIEW);
        GpuBufferSlice transforms = ScreenPasses.transforms();
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        TextureTarget copied = frame;
        ScreenPasses.copy(main, copied, transforms);
        ScreenPasses.draw(main, "Space-time", effect, transforms, pass -> {
            pass.setUniform("SpaceTimeView", view);
            pass.bindTexture("InputColor", copied.getColorTextureView(), linear);
            pass.bindTexture("SceneDepth", main.getDepthTextureView(), nearest);
        });
    }

    private static void build() {
        ShaderMaterial built = ScreenPasses.fullScreen(ArcaneSpells.id("spacetime"), ArcaneSpells.id("core/spacetime"))
                .sampler("InputColor").sampler("SceneDepth").uniformBlock("SpaceTimeView").build();
        ScreenPasses.precompile("The space-time shader", built);
        uniforms = new DynamicUniformBlock<>("Space-time view", View.BYTES, 4, View::encode);
        effect = built;
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
         * The hands of its clock lie on the ground at the centre, {@code hands} bright from 0 to 1, the second hand
         * {@code secondHand} radians clockwise from twelve.
         */
        void field(Vec3 center, float radius, float drain, float front, float secondHand, float hands) {
            if (fields >= SLOTS || radius <= 0) return;
            relative(fieldCenter[fields], center, radius);
            fieldState[fields].set(drain, front, secondHand, hands);
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
