package dev.arcane.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.arcane.registry.ModAttachments;
import io.netty.buffer.ByteBuf;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;

/**
 * A player's mana and spell cooldowns. Both are stored against absolute game time, so nothing ticks per player and
 * the client is only re-synced when a spell is cast.
 *
 * @param manaAt   mana at game time {@code manaTick}, before regeneration since then
 * @param readyAt  game time each spell comes off cooldown; spells with no entry are ready
 */
public record CasterState(float manaAt, long manaTick, Map<Spell, Long> readyAt) {
    /** Players who have never cast: a full pool and nothing on cooldown. */
    public static final CasterState FULL = new CasterState(SpellTuning.MAX_MANA, 0, Map.of());

    public static final Codec<CasterState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("mana").forGetter(CasterState::manaAt),
            Codec.LONG.fieldOf("mana_tick").forGetter(CasterState::manaTick),
            Codec.unboundedMap(Spell.CODEC, Codec.LONG).fieldOf("ready_at").forGetter(CasterState::readyAt)
    ).apply(instance, CasterState::new));
    public static final StreamCodec<ByteBuf, CasterState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CasterState::manaAt,
            ByteBufCodecs.VAR_LONG, CasterState::manaTick,
            ByteBufCodecs.<ByteBuf, Spell, Long, Map<Spell, Long>>map(HashMap::new, Spell.STREAM_CODEC, ByteBufCodecs.VAR_LONG),
            CasterState::readyAt,
            CasterState::new);

    public static CasterState of(Player player) {
        return player.getAttachedOrElse(ModAttachments.CASTER, FULL);
    }

    /** Mana at game time {@code now}, regenerating linearly up to the maximum. */
    public float mana(long now) {
        // Game time only runs backwards when player data moves to another world; start such players full.
        if (now < manaTick) return SpellTuning.MAX_MANA;
        return (float) Math.min(SpellTuning.MAX_MANA, manaAt + (now - manaTick) * (double) SpellTuning.MANA_REGEN_PER_TICK);
    }

    /** Ticks until {@code spell} can be cast again; never more than its full cooldown, for the same reason. */
    public int cooldownLeft(Spell spell, long now) {
        return Math.clamp(readyAt.getOrDefault(spell, 0L) - now, 0, spell.cooldownTicks());
    }

    CasterState afterCast(Spell spell, float manaCost, long now) {
        Map<Spell, Long> ready = new EnumMap<>(Spell.class);
        ready.putAll(readyAt);
        // Forget finished cooldowns so the saved map stays small.
        ready.values().removeIf(time -> time <= now);
        ready.put(spell, now + spell.cooldownTicks());
        return new CasterState(mana(now) - manaCost, now, ready);
    }
}
