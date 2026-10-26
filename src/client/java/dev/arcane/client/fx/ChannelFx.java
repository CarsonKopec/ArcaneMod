package dev.arcane.client.fx;

import static dev.leyline.client.fx.Fx.UP;
import static dev.leyline.client.fx.Fx.circle;
import static dev.leyline.client.fx.Fx.ease;
import static dev.leyline.client.fx.Fx.light;
import static dev.leyline.client.fx.Fx.onCircle;
import static dev.leyline.client.fx.Fx.particle;
import static dev.leyline.client.fx.Fx.particlesVisible;
import static dev.leyline.client.fx.Fx.scaled;
import static dev.leyline.client.fx.Fx.show;
import static dev.leyline.client.fx.Fx.unlight;
import static dev.leyline.client.fx.Fx.whiter;
import static dev.leyline.client.fx.Fx.world;

import de.nexusrealms.nebulon.api.render.ColorRgba;
import de.nexusrealms.nebulon.api.render.EffectHandle;
import de.nexusrealms.nebulon.api.render.effect.MagicCircle;
import de.nexusrealms.nebulon.api.render.light.client.PointLightHandle;
import dev.arcane.api.client.SpellEffectRenderers;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.registry.ModParticles;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.spell.SpellCasting;
import dev.leyline.client.fx.Fx;
import dev.leyline.client.fx.NebulonGuard;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Any Astral Staff spell being channelled: a circle at the caster's feet, and a ring where the spell will land, its rim
 * an arc that closes as the channel fills, flaring once complete; red where it can't land. Coloured like the spell the
 * caster's staff holds.
 */
final class ChannelFx {
    private static final Fx.Tracker<Channel> CHANNELS = AstralFx.TRACKERS.create();
    private static final ColorRgba GOLD = ColorRgba.rgba(0xFFD27AFF);
    private static final ColorRgba RED = ColorRgba.rgba(0xFF4A4AFF);
    /** A channel not updated for this long has ended: released, or interrupted. */
    private static final int TIMEOUT_TICKS = 4;

    private ChannelFx() {
    }

    static void register() {
        SpellEffectRenderers.register(ModSpellEffects.ASTRAL_CHANNEL,
                (effect, level) -> CHANNELS.get(effect.sourceId(), level, Channel::new).update(effect));
    }

    private static final class Channel implements Fx.Animation {
        final int casterId;
        final Fx.Planes planes = new Fx.Planes();
        EffectHandle<MagicCircle> feet;
        PointLightHandle light;
        int idle;

        Channel(int casterId) {
            this.casterId = casterId;
        }

        @Override public boolean tick() {
            return ++idle > TIMEOUT_TICKS;
        }

        void update(SpellEffect effect) {
            idle = 0;
            double radius = Math.abs(effect.direction().y);
            boolean valid = effect.direction().y > 0;
            float power = Mth.clamp(effect.power(), 0, 1);
            boolean complete = power >= 1;
            Entity caster = world().getEntity(casterId);
            ColorRgba spell = spellColor(caster);
            float pulse = 0.75f + 0.25f * Mth.sin(effect.age() * 0.8f);
            ColorRgba color = !valid ? RED : complete ? scaled(whiter(spell, 0.6f), pulse) : spell;
            // Thick enough to read on a wide ring, thin enough not to swamp a small one.
            float line = (float) Mth.clamp(radius * 0.025, 0.06, 0.3);
            Vec3 mark = effect.position().add(0, 0.08, 0);
            NebulonGuard.guarded(() -> {
                if (caster != null) {
                    // Dim: a magic circle adds most of its colour across its whole disc.
                    feet = show(feet, circle(caster.position().add(0, 0.05, 0), UP, 1.2 + 0.6 * power, 0.04f,
                            scaled(spell, 0.25f + 0.25f * power), 3, casterId, 1.2f));
                }
                planes.begin();
                // The faint full ring shows the spell's reach; the bold arc sweeps round it as the channel fills.
                planes.ring(mark, UP, radius, line * 0.3f, scaled(color, 0.45f));
                float sweep = complete ? Mth.TWO_PI : Mth.TWO_PI * ease(power);
                if (sweep > 0.01f) planes.arc(mark, UP, radius, line, Mth.HALF_PI, Mth.HALF_PI + sweep, color);
                if (complete) planes.ring(mark, UP, radius * 0.9, line * 0.4f, scaled(color, 0.7f));
                planes.end();
                light = valid ? light(light, effect.position().add(0, 1, 0), (float) Math.max(4, radius * 0.5), 0.3f + 0.9f * power, spell)
                        : unlight(light);
            });
            if (caster != null && particlesVisible(caster.position())) {
                RandomSource random = world().random;
                for (int i = 0, n = Fx.scaled(2); i < n; i++) {
                    Vec3 at = onCircle(caster.position(), 1.2 + 0.6 * power, random.nextDouble() * Mth.TWO_PI, 0.1);
                    particle(ModParticles.GOLD, at, new Vec3(0, 0.08 + 0.12 * power, 0));
                }
            }
        }

        /** The colour of the spell on the caster's staff, so each Astral spell's ring is its own colour. */
        private static ColorRgba spellColor(Entity caster) {
            if (!(caster instanceof Player player)) return GOLD;
            ItemStack staff = SpellCasting.heldStaff(player);
            return staff.isEmpty() ? GOLD : ColorRgba.rgb(SpellCasting.selected(staff).color() & 0xFFFFFF);
        }

        @Override public void release() {
            if (feet != null) feet.remove();
            planes.clear();
            if (light != null) light.close();
        }
    }
}
