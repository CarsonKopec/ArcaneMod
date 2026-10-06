package dev.arcane.network;

import dev.arcane.ArcaneSpells;
import dev.arcane.progression.ArcaneTalent;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Requests the next rank in a branch; no client supplied cost or rank is trusted. */
public record LearnTalentPayload(ArcaneTalent talent) implements CustomPacketPayload {
    public static final Type<LearnTalentPayload> TYPE = new Type<>(ArcaneSpells.id("learn_talent"));
    public static final StreamCodec<ByteBuf, LearnTalentPayload> CODEC = ByteBufCodecs.VAR_INT.map(index -> {
        if (index < 0 || index >= ArcaneTalent.values().length) throw new IllegalArgumentException("Unknown arcane talent " + index);
        return new LearnTalentPayload(ArcaneTalent.values()[index]);
    }, payload -> payload.talent().ordinal());

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
