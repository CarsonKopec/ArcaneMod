package dev.arcane.api.effect;

import net.minecraft.world.phys.Vec3;

/**
 * One visual event. The server sends it once to nearby players, and each client draws its own particles and lights
 * from it, so individual particles never cross the network. Each {@link SpellEffectType} says what the fields mean;
 * as a rule:
 *
 * @param position where it happens
 * @param direction which way it faces or travels; any length (sent as floats)
 * @param power    strength, usually 0 to 1
 * @param age      ticks since it began, a count, or a duration
 * @param sourceId what it belongs to, usually an entity id, so a client can update or end an earlier effect
 */
public record SpellEffect(SpellEffectType type, Vec3 position, Vec3 direction, float power, int age, int sourceId) {
}
