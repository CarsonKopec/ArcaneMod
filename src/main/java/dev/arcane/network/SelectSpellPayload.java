package dev.arcane.network;

import dev.arcane.ArcaneSpells;
import dev.arcane.api.spell.Spell;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client to server: the player picked {@code spell} on the spell wheel. */
public record SelectSpellPayload(Spell spell) implements CustomPacketPayload {
    public static final Type<SelectSpellPayload> TYPE = new Type<>(ArcaneSpells.id("select_spell"));
    public static final StreamCodec<ByteBuf, SelectSpellPayload> CODEC =
            Spell.STREAM_CODEC.map(SelectSpellPayload::new, SelectSpellPayload::spell);

    @Override public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
