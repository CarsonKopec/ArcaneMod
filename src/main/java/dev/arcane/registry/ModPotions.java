package dev.arcane.registry;

import dev.arcane.ArcaneSpells;
import dev.arcane.spell.SpellTuning;
import net.fabricmc.fabric.api.registry.FabricBrewingRecipeRegistryBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.Potions;

/**
 * The mod's potions, brewed from an Awkward Potion like vanilla's, with their long (redstone) and strong (glowstone)
 * versions. Each also comes as a splash and lingering potion and a tipped arrow, as vanilla's do.
 */
public final class ModPotions {
    // The name is the translation key's last part: item.minecraft.potion.effect.arcane_spells.mana, and so on.
    /** Amethyst shard. */
    public static final Holder<Potion> MANA = register("mana", new Potion("arcane_spells.mana",
            new MobEffectInstance(ModEffects.INSTANT_MANA, 1)));
    public static final Holder<Potion> STRONG_MANA = register("strong_mana", new Potion("arcane_spells.mana",
            new MobEffectInstance(ModEffects.INSTANT_MANA, 1, 1)));
    /** Lapis lazuli. */
    public static final Holder<Potion> ARCANE_FLOW = register("arcane_flow", new Potion("arcane_spells.arcane_flow",
            new MobEffectInstance(ModEffects.ARCANE_FLOW, SpellTuning.ARCANE_FLOW_TICKS)));
    public static final Holder<Potion> LONG_ARCANE_FLOW = register("long_arcane_flow", new Potion("arcane_spells.arcane_flow",
            new MobEffectInstance(ModEffects.ARCANE_FLOW, SpellTuning.LONG_ARCANE_FLOW_TICKS)));
    public static final Holder<Potion> STRONG_ARCANE_FLOW = register("strong_arcane_flow", new Potion("arcane_spells.arcane_flow",
            new MobEffectInstance(ModEffects.ARCANE_FLOW, SpellTuning.STRONG_ARCANE_FLOW_TICKS, 1)));
    /** Glow ink sac. */
    public static final Holder<Potion> CLARITY = register("clarity", new Potion("arcane_spells.clarity",
            new MobEffectInstance(ModEffects.CLARITY, SpellTuning.CLARITY_TICKS)));
    public static final Holder<Potion> LONG_CLARITY = register("long_clarity", new Potion("arcane_spells.clarity",
            new MobEffectInstance(ModEffects.CLARITY, SpellTuning.LONG_CLARITY_TICKS)));

    private ModPotions() {
    }

    public static void register() {
        FabricBrewingRecipeRegistryBuilder.BUILD.register(builder -> {
            builder.addMix(Potions.AWKWARD, Items.AMETHYST_SHARD, MANA);
            builder.addMix(MANA, Items.GLOWSTONE_DUST, STRONG_MANA);
            builder.addMix(Potions.AWKWARD, Items.LAPIS_LAZULI, ARCANE_FLOW);
            builder.addMix(ARCANE_FLOW, Items.REDSTONE, LONG_ARCANE_FLOW);
            builder.addMix(ARCANE_FLOW, Items.GLOWSTONE_DUST, STRONG_ARCANE_FLOW);
            builder.addMix(Potions.AWKWARD, Items.GLOW_INK_SAC, CLARITY);
            builder.addMix(CLARITY, Items.REDSTONE, LONG_CLARITY);
        });
    }

    private static Holder<Potion> register(String name, Potion potion) {
        return Registry.registerForHolder(BuiltInRegistries.POTION, ArcaneSpells.id(name), potion);
    }
}
