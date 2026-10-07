package dev.arcane.progression;

import com.mojang.serialization.Codec;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.SpellAttributes;
import dev.arcane.api.spell.Spell;
import dev.arcane.registry.ModAttachments;
import dev.arcane.spell.CasterState;
import dev.arcane.spell.ManaLimits;
import dev.arcane.spell.SpellTuning;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;
import org.ladysnake.cca.api.v3.entity.RespawnableComponent;

/** Mana, cooldowns and permanent spell knowledge. Only the owning player receives this data. */
public final class PlayerProgressionComponent implements AutoSyncedComponent, ServerTickingComponent,
        RespawnableComponent<PlayerProgressionComponent> {
    private static final int MAX_COUNTER = 1_000_000;
    private static final Codec<Map<Identifier, Integer>> CASTS_CODEC = Codec.unboundedMap(Identifier.CODEC, Codec.intRange(0, MAX_COUNTER));
    private static final Codec<Map<String, Integer>> COUNTERS_CODEC = Codec.unboundedMap(Codec.STRING, Codec.intRange(0, MAX_COUNTER));
    private static final Identifier RESERVOIR = ArcaneSpells.id("talent/reservoir");
    private static final Identifier RECOVERY = ArcaneSpells.id("talent/recovery");
    private final Player owner;
    private CasterState caster = CasterState.FULL;
    private boolean casterInitialized;
    private boolean attributesDirty = true;
    private final Map<Identifier, Integer> casts = new HashMap<>();
    private final EnumMap<SpellSchool, Integer> affinities = new EnumMap<>(SpellSchool.class);
    private final EnumMap<ArcaneTalent, Integer> talents = new EnumMap<>(ArcaneTalent.class);
    private int corruption;

    public PlayerProgressionComponent(Player owner) {
        this.owner = owner;
    }

    public CasterState casterState() {
        // Entity attachments load after construction. Import lazily, after the entire entity has loaded.
        if (!casterInitialized && owner instanceof ServerPlayer) {
            CasterState legacy = owner.getAttached(ModAttachments.CASTER);
            caster = legacy == null ? CasterState.FULL : legacy;
            casterInitialized = true;
        }
        // Called every tick: only touch the attachment when there is one left to retire.
        if (casterInitialized && owner instanceof ServerPlayer && owner.hasAttached(ModAttachments.CASTER)) {
            owner.removeAttached(ModAttachments.CASTER);
        }
        return caster;
    }

    public void setCasterState(CasterState state) {
        if (!(owner instanceof ServerPlayer)) return;
        caster = state;
        casterInitialized = true;
        owner.removeAttached(ModAttachments.CASTER);
        sync();
    }

    public int casts(Spell spell) {
        return casts.getOrDefault(spell.id(), 0);
    }

    public int mastery(Spell spell) {
        return Math.min(10, casts(spell) / 5);
    }

    public int affinity(SpellSchool school) {
        return affinities.getOrDefault(school, 0);
    }

    /** Whole percent off {@code school}'s mana costs its affinity has earned: one per 500 XP, up to 10. */
    public int affinityDiscount(SpellSchool school) {
        return Math.min(SpellTuning.AFFINITY_MAX_PERCENT, affinity(school) / SpellTuning.AFFINITY_XP_PER_PERCENT);
    }

    public int corruption() {
        return corruption;
    }

    public int rank(ArcaneTalent talent) {
        return talents.getOrDefault(talent, 0);
    }

    public int skillPoints() {
        int earned = casts.values().stream().mapToInt(count -> Math.min(10, count / 5)).sum();
        int spent = talents.entrySet().stream().mapToInt(entry -> entry.getKey().totalCost(entry.getValue())).sum();
        return Math.max(0, earned - spent);
    }

    /** Whether every talent {@code talent} requires is complete, so it can be learned. */
    public boolean unlocked(ArcaneTalent talent) {
        return talent.requires().stream().allMatch(required -> rank(required) >= required.maxRank());
    }

    /** Called only after a successful paid cast. Creative/free/failed casts never award progress. */
    public void recordCast(Spell spell, float manaSpent) {
        if (!(owner instanceof ServerPlayer) || owner.isSpectator() || owner.getAbilities().instabuild
                || !Float.isFinite(manaSpent) || manaSpent <= 0) return;
        casts.put(spell.id(), Math.min(MAX_COUNTER, casts(spell) + 1));
        SpellSchool school = SpellSchool.of(spell);
        int experience = Math.clamp((long) Math.ceil(manaSpent) * (rank(ArcaneTalent.ARCHMAGE) > 0 ? 2 : 1), 1, MAX_COUNTER);
        affinities.put(school, Math.min(MAX_COUNTER, affinity(school) + experience));
        if (school == SpellSchool.ASTRAL) corruption = Math.min(100, corruption + Math.max(1, experience / 25));
        sync();
    }

    /** Network requests contain only the branch: price, points, limits and effects are decided here. */
    public boolean learnTalent(ArcaneTalent talent) {
        if (!(owner instanceof ServerPlayer) || !owner.isAlive() || owner.isSpectator() || owner.isUsingItem()) return false;
        int rank = rank(talent);
        if (rank >= talent.maxRank() || !unlocked(talent) || skillPoints() < talent.costForNextRank(rank)) return false;
        talents.put(talent, rank + 1);
        attributesDirty = true;
        applyAttributes();
        sync();
        return true;
    }

    @Override public void serverTick() {
        casterState();
        if (attributesDirty) applyAttributes();
        // Corruption is tracked for future spell interactions; it currently has no hidden damage penalty.
        if (corruption > 0 && owner.tickCount % 40 == 0 && !owner.isUsingItem()) {
            corruption--;
            sync();
        }
    }

    private void applyAttributes() {
        if (!(owner instanceof ServerPlayer player)) return;
        updateModifier(player.getAttribute(SpellAttributes.MAX_MANA), RESERVOIR, rank(ArcaneTalent.RESERVOIR) * 20);
        updateModifier(player.getAttribute(SpellAttributes.MANA_REGEN), RECOVERY, rank(ArcaneTalent.RECOVERY) * 0.5);
        attributesDirty = false;
        ManaLimits.update(player);
    }

    private static void updateModifier(AttributeInstance attribute, Identifier id, double amount) {
        if (attribute == null) return;
        attribute.removeModifier(id);
        if (amount != 0) attribute.addTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_VALUE));
    }

    private void sync() {
        if (owner instanceof ServerPlayer) ArcaneProgression.PLAYER.sync(owner);
    }

    @Override public boolean shouldSyncWith(ServerPlayer recipient) {
        return recipient == owner;
    }

    @Override public void readData(ValueInput input) {
        var savedCaster = input.read("caster", CasterState.CODEC);
        caster = savedCaster.orElse(CasterState.FULL);
        casterInitialized = savedCaster.isPresent();
        casts.clear();
        casts.putAll(input.read("casts", CASTS_CODEC).orElse(Map.of()));
        affinities.clear();
        Map<String, Integer> savedAffinities = input.read("affinities", COUNTERS_CODEC).orElse(Map.of());
        for (SpellSchool school : SpellSchool.values()) affinities.put(school, savedAffinities.getOrDefault(key(school), 0));
        talents.clear();
        Map<String, Integer> savedTalents = input.read("talents", COUNTERS_CODEC).orElse(Map.of());
        for (ArcaneTalent talent : ArcaneTalent.values()) talents.put(talent, Math.min(talent.maxRank(), savedTalents.getOrDefault(key(talent), 0)));
        corruption = Math.clamp(input.getIntOr("corruption", 0), 0, 100);
        attributesDirty = true;
    }

    @Override public void writeData(ValueOutput output) {
        output.store("caster", CasterState.CODEC, casterState());
        output.store("casts", CASTS_CODEC, casts);
        Map<String, Integer> savedAffinities = new HashMap<>();
        affinities.forEach((school, xp) -> savedAffinities.put(key(school), xp));
        output.store("affinities", COUNTERS_CODEC, savedAffinities);
        Map<String, Integer> savedTalents = new HashMap<>();
        talents.forEach((talent, rank) -> savedTalents.put(key(talent), rank));
        output.store("talents", COUNTERS_CODEC, savedTalents);
        output.putInt("corruption", corruption);
    }

    @Override public boolean shouldCopyForRespawn(boolean lossless, boolean keepInventory, boolean sameCharacter) {
        return true;
    }

    @Override public void copyForRespawn(PlayerProgressionComponent original, HolderLookup.Provider registries,
            boolean lossless, boolean keepInventory, boolean sameCharacter) {
        casts.clear();
        casts.putAll(original.casts);
        affinities.clear();
        affinities.putAll(original.affinities);
        talents.clear();
        talents.putAll(original.talents);
        corruption = original.corruption;
        // Keep the old behavior: ordinary death resets mana/cooldowns; returning from the End preserves them.
        caster = lossless ? original.casterState() : CasterState.FULL;
        casterInitialized = true;
        attributesDirty = true;
    }

    private static String key(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}
