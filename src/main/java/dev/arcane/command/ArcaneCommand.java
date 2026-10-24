package dev.arcane.command;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.arcane.api.ArcaneSpellsApi;
import dev.arcane.api.spell.Spell;
import dev.arcane.progression.ArcaneProgression;
import dev.arcane.spell.SpellCasting;
import dev.arcane.spell.SpellTuning;
import java.util.Collection;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.selector.EntitySelector;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * {@code /arcane}, for operators: read or set players' mana and corruption, and clear their cooldowns or talents.
 *
 * <pre>
 * /arcane mana get &lt;player&gt;                 /arcane corruption get &lt;player&gt;
 * /arcane mana set|add &lt;players&gt; &lt;amount&gt;   /arcane corruption set|add &lt;players&gt; &lt;amount&gt;
 * /arcane mana fill &lt;players&gt;               /arcane cooldowns reset &lt;players&gt;
 * /arcane talents reset &lt;players&gt;           (the points spent on them come back)
 * </pre>
 */
public final class ArcaneCommand {
    private ArcaneCommand() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                Commands.literal("arcane").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .then(Commands.literal("mana")
                                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
                                        .executes(ArcaneCommand::getMana)))
                                .then(Commands.literal("set").then(players().then(Commands.argument("amount", FloatArgumentType.floatArg(0))
                                        .executes(ArcaneCommand::setMana))))
                                .then(Commands.literal("add").then(players().then(Commands.argument("amount", FloatArgumentType.floatArg())
                                        .executes(ArcaneCommand::addMana))))
                                .then(Commands.literal("fill").then(players().executes(context -> each(context, "mana.fill",
                                        player -> ArcaneSpellsApi.addMana(player, ArcaneSpellsApi.maxMana(player)))))))
                        .then(Commands.literal("corruption")
                                .then(Commands.literal("get").then(Commands.argument("player", EntityArgument.player())
                                        .executes(ArcaneCommand::getCorruption)))
                                .then(Commands.literal("set").then(players().then(Commands.argument("amount",
                                        IntegerArgumentType.integer(0, SpellTuning.CORRUPTION_MAX)).executes(ArcaneCommand::setCorruption))))
                                .then(Commands.literal("add").then(players().then(Commands.argument("amount", IntegerArgumentType.integer())
                                        .executes(ArcaneCommand::addCorruption)))))
                        .then(Commands.literal("cooldowns")
                                .then(Commands.literal("reset").then(players().executes(context -> each(context, "cooldowns.reset", player -> {
                                    for (Spell spell : SpellCasting.spells()) ArcaneSpellsApi.setCooldown(player, spell, 0);
                                })))))
                        .then(Commands.literal("talents")
                                .then(Commands.literal("reset").then(players().executes(context -> each(context, "talents.reset",
                                        player -> ArcaneProgression.get(player).resetTalents())))))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, EntitySelector> players() {
        return Commands.argument("players", EntityArgument.players());
    }

    private static int getMana(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        float mana = ArcaneSpellsApi.mana(player);
        context.getSource().sendSuccess(() -> Component.translatable("commands.arcane_spells.mana.get", player.getDisplayName(),
                decimal(mana), decimal(ArcaneSpellsApi.maxMana(player))), false);
        return (int) mana;
    }

    private static int setMana(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float amount = FloatArgumentType.getFloat(context, "amount");
        return each(context, "mana.set", player -> ArcaneSpellsApi.addMana(player, amount - ArcaneSpellsApi.mana(player)), decimal(amount));
    }

    private static int addMana(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        float amount = FloatArgumentType.getFloat(context, "amount");
        return each(context, "mana.add", player -> ArcaneSpellsApi.addMana(player, amount), decimal(amount));
    }

    private static int getCorruption(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        int corruption = ArcaneProgression.get(player).corruption();
        context.getSource().sendSuccess(() -> Component.translatable("commands.arcane_spells.corruption.get", player.getDisplayName(),
                corruption, SpellTuning.CORRUPTION_MAX), false);
        return corruption;
    }

    private static int setCorruption(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        return each(context, "corruption.set", player -> ArcaneProgression.get(player).setCorruption(amount), amount);
    }

    private static int addCorruption(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        int amount = IntegerArgumentType.getInteger(context, "amount");
        return each(context, "corruption.add", player -> {
            var progression = ArcaneProgression.get(player);
            progression.setCorruption(progression.corruption() + amount);
        }, amount);
    }

    /**
     * Applies {@code action} to every player the command names, then reports it with
     * {@code commands.arcane_spells.<key>.single} (the player's name first) or {@code .multiple} (the number of players
     * first), followed by {@code args}. Returns how many players it applied to.
     */
    private static int each(CommandContext<CommandSourceStack> context, String key, Consumer<ServerPlayer> action, Object... args)
            throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "players");
        players.forEach(action);
        Object[] with = new Object[args.length + 1];
        System.arraycopy(args, 0, with, 1, args.length);
        if (players.size() == 1) {
            with[0] = players.iterator().next().getDisplayName();
            context.getSource().sendSuccess(() -> Component.translatable("commands.arcane_spells." + key + ".single", with), true);
        } else {
            with[0] = players.size();
            context.getSource().sendSuccess(() -> Component.translatable("commands.arcane_spells." + key + ".multiple", with), true);
        }
        return players.size();
    }

    private static String decimal(float value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
