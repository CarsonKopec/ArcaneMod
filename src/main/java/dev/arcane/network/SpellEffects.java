package dev.arcane.network;

import dev.arcane.spell.SpellTuning;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Server side of the effect channel: sends one message to each nearby player who has the mod. */
public final class SpellEffects {
    private SpellEffects() {
    }

    public static void send(ServerLevel level, SpellEffectPayload.Kind kind, Vec3 position, Vec3 direction,
                            float power, int age, int sourceId) {
        SpellEffectPayload payload = new SpellEffectPayload(kind, position, direction, power, age, sourceId);
        double rangeSqr = SpellTuning.EFFECT_RANGE * SpellTuning.EFFECT_RANGE;
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(position) < rangeSqr && ServerPlayNetworking.canSend(player, SpellEffectPayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        }
    }
}
