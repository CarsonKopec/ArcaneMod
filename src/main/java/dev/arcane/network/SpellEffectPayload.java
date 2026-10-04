package dev.arcane.network;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.effect.SpellEffect;
import dev.arcane.api.effect.SpellEffectType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * One compact message per effect; particles are generated locally, never individually networked.
 *
 * <p>{@code effect_v3} since 0.5.0. Effect types are sent by registry id, which registry sync keeps in step between
 * client and server, so the id only needs bumping when the message's layout changes.
 */
public record SpellEffectPayload(SpellEffect effect) implements CustomPacketPayload {
    public static final Type<SpellEffectPayload> TYPE = new Type<>(ArcaneSpells.id("effect_v3"));
    private static final StreamCodec<RegistryFriendlyByteBuf, SpellEffectType> TYPE_CODEC = ByteBufCodecs.idMapper(
            SpellRegistries.SPELL_EFFECTS::byIdOrThrow, SpellRegistries.SPELL_EFFECTS::getIdOrThrow).cast();
    public static final StreamCodec<RegistryFriendlyByteBuf, SpellEffectPayload> CODEC = StreamCodec.of(
            (buf, value) -> {
                SpellEffect effect = value.effect;
                TYPE_CODEC.encode(buf, effect.type());
                buf.writeDouble(effect.position().x);
                buf.writeDouble(effect.position().y);
                buf.writeDouble(effect.position().z);
                buf.writeFloat((float) effect.direction().x);
                buf.writeFloat((float) effect.direction().y);
                buf.writeFloat((float) effect.direction().z);
                buf.writeFloat(effect.power());
                buf.writeVarInt(effect.age());
                buf.writeVarInt(effect.sourceId());
            },
            buf -> new SpellEffectPayload(new SpellEffect(TYPE_CODEC.decode(buf),
                    new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()),
                    buf.readFloat(), buf.readVarInt(), buf.readVarInt()))
    );

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
