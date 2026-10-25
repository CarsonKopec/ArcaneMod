package dev.arcane.spell;

import dev.arcane.api.spell.SpellContext;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Chrono Field, an Astral Staff spell: time stops in a dome around where the caster stood, for eight seconds. Creatures
 * the caster could hurt, projectiles (the caster's own included) and lit TNT are frozen where they are, and anything
 * that enters is frozen too; the caster moves freely, and while inside their own field fights with Speed II, Strength
 * III and Haste II. Damage dealt to frozen creatures is held back, then lands all at once,
 * {@link SpellTuning#CHRONO_DAMAGE_MULTIPLIER} times as hard, when time resumes. Owns every field.
 *
 * <p>Mobs are frozen with No AI, and creatures other than players without gravity, so their own AI and physics don't
 * fight the hold; players are only ever held in place, so nothing is written into their saved data. Everything thaws
 * before the server stops (and saves), and anything unloaded mid-field thaws first.
 */
public final class ChronoField extends MarkedSpell {
    private static final List<Field> FIELDS = new ArrayList<>();
    /** Every frozen entity, in any field; an entity is only ever frozen by one. */
    private static final Map<Entity, Frozen> FROZEN = new IdentityHashMap<>();
    private static int nextSourceId = -1;

    public ChronoField() {
        super(new Properties().manaCost(SpellTuning.CHRONO_MANA).cooldown(SpellTuning.CHRONO_COOLDOWN_TICKS)
                .charged(SpellTuning.CHRONO_CHARGE_TICKS).color(0xFF7FFFE6), SpellTuning.CHRONO_RADIUS);
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> FIELDS.removeIf(Field::tick));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            FIELDS.forEach(Field::release);
            FIELDS.clear();
        });
        ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
            Frozen frozen = FROZEN.remove(entity);
            if (frozen != null) frozen.thaw();
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(ChronoField::holdDamage);
    }

    /** Whether the entity is frozen in a field. */
    public static boolean isFrozen(Entity entity) {
        return FROZEN.containsKey(entity);
    }

    /** The dome is centred on the caster, so there's always somewhere for it. */
    @Override protected Aim aim(ServerLevel level, ServerPlayer caster) {
        return Aim.at(caster.position());
    }

    @Override protected boolean land(SpellContext context, Vec3 mark) {
        if (FIELDS.size() >= SpellTuning.MAX_ACTIVE_CHRONO_FIELDS) {
            context.level().playSound(null, context.caster().blockPosition(), ModSounds.FIZZLE, SoundSource.PLAYERS, 0.6f, 1.0f);
            return false;
        }
        FIELDS.add(new Field(context.level(), context.caster(), mark, nextSourceId--));
        return true;
    }

    /** Damage to a frozen creature is banked rather than taken, except damage nothing may resist, such as /kill. */
    private static boolean holdDamage(LivingEntity entity, DamageSource source, float amount) {
        Frozen frozen = FROZEN.get(entity);
        if (frozen == null || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
        frozen.heldDamage += amount;
        frozen.heldSource = source;
        return false;
    }

    private static final class Field {
        final ServerLevel level;
        final ServerPlayer caster;
        final Vec3 center;
        final int id;
        final List<Frozen> frozen = new ArrayList<>();
        int age;

        Field(ServerLevel level, ServerPlayer caster, Vec3 center, int id) {
            this.level = level;
            this.caster = caster;
            this.center = center;
            this.id = id;
            SpellEffects.send(level, ModSpellEffects.CHRONO_FIELD, center, Vec3.ZERO, (float) SpellTuning.CHRONO_RADIUS,
                    SpellTuning.CHRONO_DURATION_TICKS, id);
            level.playSound(null, center.x, center.y, center.z, ModSounds.CHRONO_START, SoundSource.PLAYERS, 3.0f, 1.0f);
        }

        /** Returns true once time has resumed. */
        boolean tick() {
            if (!level.hasChunk(SectionPos.blockToSectionCoord(center.x), SectionPos.blockToSectionCoord(center.z))) {
                release();
                return true;
            }
            capture();
            empowerCaster();
            // Unloaded or dead entries were thawed or are gone; let them go.
            frozen.removeIf(entry -> {
                if (!entry.entity.isRemoved() && FROZEN.get(entry.entity) == entry) {
                    entry.hold();
                    return false;
                }
                return true;
            });
            if (++age < SpellTuning.CHRONO_DURATION_TICKS) return false;
            release();
            SpellEffects.send(level, ModSpellEffects.CHRONO_END, center, Vec3.ZERO, (float) SpellTuning.CHRONO_RADIUS, 0, id);
            level.playSound(null, center.x, center.y, center.z, ModSounds.CHRONO_END, SoundSource.PLAYERS, 3.0f, 1.0f);
            return true;
        }

        /**
         * The caster, inside their own field, moves and strikes in stopped time. Renewed every tick for a moment only,
         * so it ends the instant they step out or time resumes.
         */
        private void empowerCaster() {
            if (caster.level() != level || !caster.isAlive() || caster.position().distanceTo(center) > SpellTuning.CHRONO_RADIUS) return;
            caster.addEffect(new MobEffectInstance(MobEffects.SPEED, 10, SpellTuning.CHRONO_SPEED_AMPLIFIER, true, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.STRENGTH, 10, SpellTuning.CHRONO_STRENGTH_AMPLIFIER, true, false, true));
            caster.addEffect(new MobEffectInstance(MobEffects.HASTE, 10, SpellTuning.CHRONO_HASTE_AMPLIFIER, true, false, true));
        }

        /** Freezes everything newly inside the dome. */
        private void capture() {
            double radius = SpellTuning.CHRONO_RADIUS;
            for (Entity entity : level.getEntities((Entity) null, new AABB(center, center).inflate(radius), this::freezable)) {
                if (entity.getBoundingBox().getCenter().distanceTo(center) > radius) continue;
                Frozen entry = new Frozen(entity);
                frozen.add(entry);
                FROZEN.put(entity, entry);
            }
        }

        private boolean freezable(Entity entity) {
            if (entity == caster || entity.isSpectator() || FROZEN.containsKey(entity) || caster.isPassengerOfSameVehicle(entity)) return false;
            if (entity instanceof Player player && player.isCreative()) return false;
            if (entity instanceof LivingEntity living) return SpellTargets.canHarm(caster, living);
            return entity instanceof Projectile || entity instanceof PrimedTnt;
        }

        /** Time resumes: everything moves on as it was, then takes the damage it was dealt while frozen. */
        void release() {
            for (Frozen entry : frozen) {
                if (FROZEN.remove(entry.entity) != entry || entry.entity.isRemoved()) continue;
                entry.thaw();
                if (entry.entity instanceof Projectile projectile && projectile.getOwner() != caster) rewind(projectile, entry.velocity, caster);
                if (entry.heldDamage > 0 && entry.entity instanceof LivingEntity living) {
                    living.hurtServer(level, entry.heldSource, entry.heldDamage * SpellTuning.CHRONO_DAMAGE_MULTIPLIER);
                }
            }
            frozen.clear();
        }
    }

    /** Rewind: a projectile frozen on its way flies back the way it came, as the caster's own. */
    private static void rewind(Projectile projectile, Vec3 velocity, ServerPlayer caster) {
        projectile.setOwner(caster);
        projectile.setDeltaMovement(velocity.reverse());
        projectile.hurtMarked = true;
    }

    /** One frozen entity, and how it was moving, so it carries on exactly as before. */
    private static final class Frozen {
        final Entity entity;
        final Vec3 position;
        final Vec3 velocity;
        final boolean noGravity;
        final boolean noAi;
        float heldDamage;
        DamageSource heldSource;

        Frozen(Entity entity) {
            this.entity = entity;
            this.position = entity.position();
            this.velocity = entity.getDeltaMovement();
            this.noGravity = entity.isNoGravity();
            this.noAi = entity instanceof Mob mob && mob.isNoAi();
            if (entity instanceof Mob mob) mob.setNoAi(true);
            if (!(entity instanceof Player)) entity.setNoGravity(true);
        }

        /** Keeps it in place, still, and, for lit TNT, its fuse unburnt. */
        void hold() {
            entity.setDeltaMovement(Vec3.ZERO);
            if (entity.position().distanceToSqr(position) > 1.0e-4) {
                if (entity instanceof ServerPlayer player) {
                    player.teleportTo(player.level(), position.x, position.y, position.z, Set.of(), player.getYRot(), player.getXRot(), false);
                } else {
                    entity.setPos(position.x, position.y, position.z);
                }
            }
            if (entity instanceof PrimedTnt tnt) tnt.setFuse(tnt.getFuse() + 1);
            entity.resetFallDistance();
        }

        void thaw() {
            if (entity instanceof Mob mob) mob.setNoAi(noAi);
            if (!(entity instanceof Player)) entity.setNoGravity(noGravity);
            entity.setDeltaMovement(velocity);
            entity.hurtMarked = true;
        }
    }
}
