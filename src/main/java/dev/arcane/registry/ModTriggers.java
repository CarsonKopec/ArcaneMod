package dev.arcane.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.arcane.ArcaneSpells;
import dev.arcane.api.spell.Spell;
import dev.arcane.progression.ArcaneTalent;
import java.util.Optional;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.ContextAwarePredicate;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.PlayerTrigger;
import net.minecraft.advancements.criterion.SimpleCriterionTrigger;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

/**
 * Advancement triggers for the mod's milestones. In an advancement's JSON, {@code "spell"} and {@code "talent"} narrow
 * a trigger to one spell id or talent name; leave them out to match any.
 */
public final class ModTriggers {
    /** {@code arcane_spells:cast_spell}: a successful cast, paid for or not. */
    public static final SpellTrigger CAST_SPELL = register("cast_spell", new SpellTrigger());
    /** {@code arcane_spells:master_spell}: a spell reached full mastery. */
    public static final SpellTrigger MASTER_SPELL = register("master_spell", new SpellTrigger());
    /** {@code arcane_spells:learn_talent}: a rank of a talent was learned. */
    public static final TalentTrigger LEARN_TALENT = register("learn_talent", new TalentTrigger());
    /** {@code arcane_spells:astral_backlash}: the caster's corruption lashed back at them. */
    public static final PlayerTrigger ASTRAL_BACKLASH = register("astral_backlash", new PlayerTrigger());

    private ModTriggers() {
    }

    /** Loading this class registers the triggers. */
    public static void register() {
    }

    private static <T extends CriterionTrigger<?>> T register(String name, T trigger) {
        return Registry.register(BuiltInRegistries.TRIGGER_TYPES, ArcaneSpells.id(name), trigger);
    }

    public static final class SpellTrigger extends SimpleCriterionTrigger<SpellTrigger.Instance> {
        @Override public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, Spell spell) {
            trigger(player, instance -> instance.spell.isEmpty() || instance.spell.get().equals(spell.id()));
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<Identifier> spell) implements SimpleInstance {
            static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    Identifier.CODEC.optionalFieldOf("spell").forGetter(Instance::spell)
            ).apply(instance, Instance::new));
        }
    }

    public static final class TalentTrigger extends SimpleCriterionTrigger<TalentTrigger.Instance> {
        @Override public Codec<Instance> codec() {
            return Instance.CODEC;
        }

        public void trigger(ServerPlayer player, ArcaneTalent talent) {
            trigger(player, instance -> instance.talent.isEmpty() || instance.talent.get() == talent);
        }

        public record Instance(Optional<ContextAwarePredicate> player, Optional<ArcaneTalent> talent) implements SimpleInstance {
            static final Codec<Instance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                    EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                    ArcaneTalent.CODEC.optionalFieldOf("talent").forGetter(Instance::talent)
            ).apply(instance, Instance::new));
        }
    }
}
