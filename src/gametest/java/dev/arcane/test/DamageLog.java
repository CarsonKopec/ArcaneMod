package dev.arcane.test;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Every blow aimed at an entity tagged {@link #TAG}, logged at the amount it was dealt, before armour, invulnerability
 * frames or fire get a say, and then cancelled: the entity takes nothing, so it stays put and can be hit again and again.
 * For counting a spell's hits and comparing their amounts exactly. Blows that bypass invulnerability, {@code /kill}'s and
 * the void's, still land. Server thread only.
 */
final class DamageLog {
    static final String TAG = "damage_log";
    private static final List<Blow> BLOWS = new ArrayList<>();

    static {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (!entity.getTags().contains(TAG) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return true;
            BLOWS.add(new Blow(entity, source, amount));
            return false;
        });
    }

    private DamageLog() {
    }

    /** Forgets every blow so far, and starts listening if it wasn't already. */
    static void clear() {
        BLOWS.clear();
    }

    /** A still mob of {@code type}, its feet at {@code at}, whose every blow is logged. */
    static <T extends Mob> T spawn(ServerLevel level, EntityType<T> type, Vec3 at) {
        T mob = type.create(level, EntitySpawnReason.COMMAND);
        if (mob == null) throw new AssertionError("No " + type);
        mob.setNoAi(true);
        mob.snapTo(at.x, at.y, at.z, 0, 0);
        mob.addTag(TAG);
        level.addFreshEntity(mob);
        return mob;
    }

    /** The blows aimed at {@code target}, oldest first. */
    static List<Blow> of(Entity target) {
        return BLOWS.stream().filter(blow -> blow.target() == target).toList();
    }

    record Blow(Entity target, DamageSource source, float amount) {
    }
}
