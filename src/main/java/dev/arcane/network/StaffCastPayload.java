package dev.arcane.network;

import dev.arcane.ArcaneSpells;
import dev.arcane.item.StaffCastingProfile;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.InteractionHand;

/** Server-confirmed release poses, including instantaneous spells, for the caster and nearby observers. */
public record StaffCastPayload(int entityId, InteractionHand hand, StaffCastingProfile profile) implements CustomPacketPayload {
    public static final Type<StaffCastPayload> TYPE = new Type<>(ArcaneSpells.id("staff_cast"));
    private static final StreamCodec<ByteBuf, StaffCastingProfile> PROFILE_CODEC = ByteBufCodecs.VAR_INT.map(index -> {
        if (index < 0 || index >= StaffCastingProfile.values().length) throw new IllegalArgumentException("Unknown casting profile " + index);
        return StaffCastingProfile.values()[index];
    }, StaffCastingProfile::ordinal);
    public static final StreamCodec<ByteBuf, StaffCastPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, StaffCastPayload::entityId,
            ByteBufCodecs.BOOL.map(offhand -> offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                    hand -> hand == InteractionHand.OFF_HAND), StaffCastPayload::hand,
            PROFILE_CODEC, StaffCastPayload::profile, StaffCastPayload::new);

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
