package dev.arcane.network;

import dev.arcane.ArcaneSpells;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * One compact message per effect; particles are generated locally, never individually networked.
 *
 * <p>The wire format is unchanged since 0.4.0 ({@code effect_v2}); bump the id if it ever changes.
 */
public record SpellEffectPayload(Kind kind, Vec3 position, Vec3 direction, float power, int age, int sourceId)
        implements CustomPacketPayload {
    public static final Type<SpellEffectPayload> TYPE = new Type<>(ArcaneSpells.id("effect_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellEffectPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.kind.ordinal());
                buf.writeDouble(value.position.x);
                buf.writeDouble(value.position.y);
                buf.writeDouble(value.position.z);
                buf.writeFloat((float) value.direction.x);
                buf.writeFloat((float) value.direction.y);
                buf.writeFloat((float) value.direction.z);
                buf.writeFloat(value.power);
                buf.writeVarInt(value.age);
                buf.writeVarInt(value.sourceId);
            },
            buf -> new SpellEffectPayload(Kind.byId(buf.readVarInt()),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readFloat(), buf.readVarInt(), buf.readVarInt())
    );

    /** Ordinals are the wire ids: only ever append new kinds. */
    public enum Kind {
        /** The staff is being charged; {@code sourceId} is the caster's entity id. */
        CHARGE,
        /** A bolt moved; {@code direction} spans this tick's travel. */
        BOLT,
        /** A bolt detonated; {@code direction} is its flight direction. */
        IMPACT;

        private static final Kind[] VALUES = values();

        static Kind byId(int id) {
            if (id < 0 || id >= VALUES.length) throw new DecoderException("Unknown spell effect kind " + id);
            return VALUES[id];
        }
    }

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
