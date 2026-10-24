package dev.arcane.progression;

import dev.arcane.api.event.SpellEvents;
import dev.arcane.network.SpellEffects;
import dev.arcane.registry.ModDamageTypes;
import dev.arcane.registry.ModEffects;
import dev.arcane.registry.ModSounds;
import dev.arcane.registry.ModSpellEffects;
import dev.arcane.registry.ModTriggers;
import dev.arcane.spell.SpellTuning;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Corruption, the Astral Staff's price and its power. Every paid Astral cast builds it, and it fades by itself while the
 * caster rests, or fast under Clarity. Each point makes Astral spells hit harder; but from
 * {@link SpellTuning#BACKLASH_THRESHOLD} on, an Astral cast may lash back at its caster, hurting them and burning off
 * some of it. The amounts are in {@link SpellTuning}; the corruption itself is kept by {@link PlayerProgressionComponent}.
 */
public final class Corruption {
    private Corruption() {
    }

    /** The spell power {@code corruption} adds to Astral spells. */
    public static float spellPower(int corruption) {
        return corruption * SpellTuning.CORRUPTION_POWER_PER_POINT;
    }

    /** How likely an Astral cast is to backlash at {@code corruption}, 0 to 1. */
    public static float backlashChance(int corruption) {
        return Math.clamp((corruption - SpellTuning.BACKLASH_THRESHOLD) * SpellTuning.BACKLASH_CHANCE_PER_POINT, 0, 1);
    }

    /** How likely {@code player}'s next Astral cast is to backlash, 0 to 1: none under Clarity. Either side. */
    public static float backlashChance(Player player) {
        return player.hasEffect(ModEffects.CLARITY) ? 0 : backlashChance(ArcaneProgression.get(player).corruption());
    }

    /** What a backlash at {@code corruption} does to its caster. */
    public static float backlashDamage(int corruption) {
        return SpellTuning.BACKLASH_BASE_DAMAGE + corruption * SpellTuning.BACKLASH_DAMAGE_PER_POINT;
    }

    /** The corruption a paid Astral cast that spent {@code mana} builds. */
    public static int buildup(float mana) {
        return Math.max(1, Math.round(mana * SpellTuning.CORRUPTION_PER_MANA));
    }

    static void register() {
        // Both sides: the grimoire shows each spell's power.
        SpellEvents.MODIFY_SPELL_POWER.register((caster, spell, power) -> SpellSchool.of(spell) == SpellSchool.ASTRAL
                ? power + spellPower(ArcaneProgression.get(caster).corruption()) : power);
    }

    /**
     * After a paid Astral cast, before it builds any corruption: rolls, at the corruption the spell was cast with (which
     * also set its power), whether it lashes back.
     */
    static void rollBacklash(ServerLevel level, ServerPlayer caster) {
        float chance = backlashChance(caster);
        if (chance > 0 && caster.getRandom().nextFloat() < chance) backlash(level, caster);
    }

    /**
     * {@code caster}'s corruption lashes back at them: it hurts them, more the more corrupt they are, darkens their
     * sight for a moment and burns off {@link SpellTuning#BACKLASH_VENT} corruption.
     */
    public static void backlash(ServerLevel level, ServerPlayer caster) {
        PlayerProgressionComponent progression = ArcaneProgression.get(caster);
        int corruption = progression.corruption();
        progression.setCorruption(corruption - SpellTuning.BACKLASH_VENT);
        Vec3 at = caster.getBoundingBox().getCenter();
        level.playSound(null, at.x, at.y, at.z, ModSounds.BACKLASH, SoundSource.PLAYERS, 1.0f, 1.0f);
        SpellEffects.send(level, ModSpellEffects.BACKLASH, at, new Vec3(0, 1, 0), corruption / (float) SpellTuning.CORRUPTION_MAX,
                0, caster.getId());
        caster.displayClientMessage(Component.translatable("message.arcane_spells.backlash").withStyle(ChatFormatting.LIGHT_PURPLE), true);
        caster.addEffect(new MobEffectInstance(MobEffects.DARKNESS, SpellTuning.BACKLASH_DARKNESS_TICKS));
        ModTriggers.ASTRAL_BACKLASH.trigger(caster);
        // Last: it can kill.
        caster.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.ASTRAL_BACKLASH), backlashDamage(corruption));
    }
}
