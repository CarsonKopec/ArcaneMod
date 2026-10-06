package dev.arcane.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.arcane.api.SpellRegistries;
import dev.arcane.api.spell.Spell;
import dev.arcane.progression.ArcaneProgression;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;

/**
 * A player's mana and spell cooldowns. Both are stored against absolute game time, so nothing ticks per player and
 * the client is only re-synced when they change.
 *
 * <p>Mana regenerates linearly from {@code manaTick}, using the limits stored with it rather than the player's
 * attributes as they are now. A change to the attributes {@linkplain ManaLimits settles} the pool at the old limits
 * first, so it never applies retroactively: swapping in regeneration gear just before a cast gains nothing.
 *
 * @param manaAt    mana at game time {@code manaTick}, before regeneration since then
 * @param maxMana   the pool's size since {@code manaTick}
 * @param manaRegen mana regenerated per second since {@code manaTick}
 * @param cooldowns each spell's cooldown; spells with no entry are ready
 */
public record CasterState(float manaAt, long manaTick, float maxMana, float manaRegen, Map<Spell, Cooldown> cooldowns) {
    /** Players who have never cast: a full pool at the base limits, and nothing on cooldown. */
    public static final CasterState FULL = new CasterState(SpellTuning.BASE_MAX_MANA, 0, SpellTuning.BASE_MAX_MANA,
            SpellTuning.BASE_MANA_REGEN, Map.of());

    /** Saved by spell id. Cooldowns of spells that no longer exist, because their mod was removed, are dropped. */
    private static final Codec<Map<Spell, Cooldown>> COOLDOWNS_CODEC = Codec.unboundedMap(Identifier.CODEC, Cooldown.CODEC).xmap(
            saved -> {
                Map<Spell, Cooldown> cooldowns = new HashMap<>();
                saved.forEach((id, cooldown) -> SpellRegistries.SPELLS.getOptional(id).ifPresent(spell -> cooldowns.put(spell, cooldown)));
                return cooldowns;
            },
            cooldowns -> {
                Map<Identifier, Cooldown> saved = new HashMap<>();
                cooldowns.forEach((spell, cooldown) -> saved.put(spell.id(), cooldown));
                return saved;
            });
    public static final Codec<CasterState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.fieldOf("mana").forGetter(CasterState::manaAt),
            Codec.LONG.fieldOf("mana_tick").forGetter(CasterState::manaTick),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf("max_mana").forGetter(CasterState::maxMana),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf("mana_regen").forGetter(CasterState::manaRegen),
            COOLDOWNS_CODEC.fieldOf("cooldowns").forGetter(CasterState::cooldowns)
    ).apply(instance, CasterState::new));
    public static final StreamCodec<ByteBuf, CasterState> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, CasterState::manaAt,
            ByteBufCodecs.VAR_LONG, CasterState::manaTick,
            ByteBufCodecs.FLOAT, CasterState::maxMana,
            ByteBufCodecs.FLOAT, CasterState::manaRegen,
            ByteBufCodecs.<ByteBuf, Spell, Cooldown, Map<Spell, Cooldown>>map(HashMap::new, Spell.STREAM_CODEC, Cooldown.STREAM_CODEC),
            CasterState::cooldowns,
            CasterState::new);

    public static CasterState of(Player player) {
        return ArcaneProgression.get(player).casterState();
    }

    /** Updates the server-owned component and synchronizes the owner's HUD. Client writes are ignored. */
    public static void set(Player player, CasterState state) {
        ArcaneProgression.get(player).setCasterState(state);
    }

    /** Mana at game time {@code now}, regenerating linearly up to the maximum. */
    public float mana(long now) {
        // Game time only runs backwards when player data moves to another world; start such players full.
        if (now < manaTick) return maxMana;
        return (float) Math.min(maxMana, manaAt + (now - manaTick) * (double) manaRegen / 20);
    }

    /** Ticks until {@code spell} can be cast again; never more than the cooldown's full length, for the same reason. */
    public int cooldownLeft(Spell spell, long now) {
        Cooldown cooldown = cooldowns.get(spell);
        return cooldown == null ? 0 : Math.clamp(cooldown.readyAt - now, 0, cooldown.length);
    }

    /** How much of {@code spell}'s cooldown is left, from 1 just after casting down to 0 when it's ready. */
    public float cooldownFraction(Spell spell, long now) {
        int left = cooldownLeft(spell, now);
        return left == 0 ? 0 : left / (float) cooldowns.get(spell).length;
    }

    /** With {@code amount} more mana as of {@code now} (less, if negative), kept between 0 and the maximum. */
    public CasterState withMana(float amount, long now) {
        return new CasterState(Math.clamp(mana(now) + amount, 0, maxMana), now, maxMana, manaRegen, cooldowns);
    }

    /**
     * With new limits from {@code now} on: mana so far is settled at the old ones, then kept (never refilled), but no
     * more than the new maximum.
     */
    public CasterState withLimits(float maxMana, float manaRegen, long now) {
        return new CasterState(Math.min(mana(now), maxMana), now, maxMana, manaRegen, cooldowns);
    }

    /**
     * With {@code ticks} left on {@code spell}'s cooldown; 0 or less makes it ready. A cooldown still running keeps its
     * full length unless this is longer, so shortening one shows as progress on the sweep rather than a fresh start.
     */
    public CasterState withCooldown(Spell spell, int ticks, long now) {
        if (ticks <= 0) return withCooldown(spell, null, now);
        int length = cooldownLeft(spell, now) > 0 ? Math.max(ticks, cooldowns.get(spell).length) : ticks;
        return withCooldown(spell, new Cooldown(now + ticks, length), now);
    }

    CasterState afterCast(Spell spell, float manaCost, int cooldownTicks, long now) {
        Cooldown cooldown = cooldownTicks > 0 ? new Cooldown(now + cooldownTicks, cooldownTicks) : null;
        return new CasterState(mana(now) - manaCost, now, maxMana, manaRegen, cooldowns).withCooldown(spell, cooldown, now);
    }

    /** With {@code spell}'s cooldown replaced, or removed if null. */
    private CasterState withCooldown(Spell spell, Cooldown cooldown, long now) {
        Map<Spell, Cooldown> updated = new HashMap<>(cooldowns);
        // Forget finished cooldowns so the saved map stays small.
        updated.values().removeIf(entry -> entry.readyAt <= now);
        if (cooldown == null) updated.remove(spell);
        else updated.put(spell, cooldown);
        return new CasterState(manaAt, manaTick, maxMana, manaRegen, updated);
    }

    /**
     * @param readyAt game time the spell can be cast again
     * @param length  the cooldown's full length in ticks, at least 1: the spell's cooldown, after other mods'
     *                adjustments, as it was when cast. The HUD's sweep shows the time left out of this.
     */
    public record Cooldown(long readyAt, int length) {
        static final Codec<Cooldown> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.LONG.fieldOf("ready_at").forGetter(Cooldown::readyAt),
                ExtraCodecs.POSITIVE_INT.fieldOf("length").forGetter(Cooldown::length)
        ).apply(instance, Cooldown::new));
        static final StreamCodec<ByteBuf, Cooldown> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Cooldown::readyAt,
                ByteBufCodecs.VAR_INT, Cooldown::length,
                Cooldown::new);
    }
}
