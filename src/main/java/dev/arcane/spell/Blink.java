package dev.arcane.spell;

import dev.arcane.api.spell.Spell;
import dev.arcane.api.spell.SpellContext;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.leyline.effect.Effects;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Teleports the caster along their aim, stopping short of whatever they're looking at. An afterimage of them stays
 * where they stood, and a moment later bursts, hurting and hurling whatever is near it. Owns every afterimage.
 */
public final class Blink extends Spell {
    private static final double STEP = 0.25;
    private static final List<Afterimage> AFTERIMAGES = new ArrayList<>();

    public Blink() {
        super(new Properties().manaCost(SpellTuning.BLINK_MANA).cooldown(SpellTuning.BLINK_COOLDOWN_TICKS).color(0xFFF3B5FF));
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> AFTERIMAGES.removeIf(Afterimage::tick));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> AFTERIMAGES.clear());
    }

    @Override public boolean cast(SpellContext context) {
        ServerLevel level = context.level();
        ServerPlayer caster = context.caster();
        Vec3 eye = caster.getEyePosition();
        // A miss reports the end of the ray, so this is where the caster aimed either way.
        Vec3 aim = level.clip(new ClipContext(eye, eye.add(caster.getLookAngle().scale(SpellTuning.BLINK_RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster)).getLocation();
        Vec3 destination = landingSpot(level, caster, eye, aim);
        if (destination == null) {
            caster.displayClientMessage(Component.translatable("message.arcane_spells.blink_blocked"), true);
            return false;
        }
        Vec3 origin = caster.position();
        caster.stopRiding();
        caster.teleportTo(destination.x, destination.y, destination.z);
        caster.resetFallDistance();
        level.playSound(null, origin.x, origin.y, origin.z, ModSounds.BLINK, SoundSource.PLAYERS, 0.7f, 1.0f);
        level.playSound(null, destination.x, destination.y, destination.z, ModSounds.BLINK_ARRIVE, SoundSource.PLAYERS, 0.7f, 1.0f);
        Effects.send(level, ModSpellEffects.BLINK, origin, destination.subtract(origin), 1, 0, caster.getId());
        AFTERIMAGES.add(new Afterimage(level, caster, origin, context.spellPower()));
        return true;
    }

    /**
     * Feet position for the farthest point along the aim where the caster fits, or null if that is barely a step away.
     * Each point first tries the caster standing at their usual eye height, so blinking straight ahead over flat
     * ground lands on it, then with their body centred on the ray, which fits through gaps and over ledges.
     */
    private static Vec3 landingSpot(ServerLevel level, ServerPlayer caster, Vec3 eye, Vec3 aim) {
        Vec3 path = aim.subtract(eye);
        double length = path.length();
        if (length < SpellTuning.BLINK_MIN_DISTANCE) return null;
        Vec3 direction = path.scale(1 / length);
        double[] drops = {caster.getEyeHeight(), caster.getBbHeight() / 2};
        for (double distance = length; distance >= SpellTuning.BLINK_MIN_DISTANCE; distance -= STEP) {
            Vec3 point = eye.add(direction.scale(distance));
            for (double drop : drops) {
                Vec3 feet = point.subtract(0, drop, 0);
                if (level.noCollision(caster, caster.getBoundingBox().move(feet.subtract(caster.position())))) return feet;
            }
        }
        return null;
    }

    /** What a blink leaves where the caster stood, until it bursts. */
    private static final class Afterimage {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 feet;
        /** What its damage is multiplied by, fixed at the cast. */
        final float spellPower;
        int age;

        Afterimage(ServerLevel level, ServerPlayer caster, Vec3 feet, float spellPower) {
            this.level = level;
            this.caster = caster;
            this.feet = feet;
            this.spellPower = spellPower;
        }

        /** Returns true once it has burst, or is gone. */
        boolean tick() {
            if (!level.hasChunk(SectionPos.blockToSectionCoord(feet.x), SectionPos.blockToSectionCoord(feet.z))) return true;
            if (++age < SpellTuning.BLINK_AFTERIMAGE_TICKS) return false;
            Vec3 center = feet.add(0, 1, 0);
            double radius = SpellTuning.BLINK_AFTERIMAGE_RADIUS;
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                    e -> SpellTargets.canHarm(caster, e) && e.getBoundingBox().getCenter().distanceTo(center) <= radius)) {
                if (!victim.hurtServer(level, level.damageSources().indirectMagic(caster, caster),
                        SpellTuning.BLINK_AFTERIMAGE_DAMAGE * spellPower)) {
                    continue;
                }
                Vec3 away = victim.position().subtract(feet).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0e-6 ? Vec3.ZERO : away.normalize().scale(SpellTuning.BLINK_AFTERIMAGE_KNOCKBACK);
                victim.push(away.x, 0.3, away.z);
            }
            level.playSound(null, center.x, center.y, center.z, ModSounds.BLINK_AFTERIMAGE, SoundSource.PLAYERS, 0.9f, 1.0f);
            Effects.send(level, ModSpellEffects.AFTERIMAGE, feet, Vec3.ZERO, 1, 0, caster.getId());
            return true;
        }
    }
}
