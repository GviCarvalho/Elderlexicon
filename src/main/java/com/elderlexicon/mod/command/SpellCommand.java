package com.elderlexicon.mod.command;

import com.elderlexicon.mod.spell.SpellCastingService;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Registers and executes the /spell command, translating rune inputs into readable descriptions.
 */
@SuppressWarnings("null")
public final class SpellCommand {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final SpellCastingService CASTING_SERVICE = new SpellCastingService();

    private SpellCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("spell")
                .executes(context -> execute(context.getSource(), ""))
                .then(Commands.argument("runes", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "runes"))));

        dispatcher.register(builder);
    }

    private static int execute(CommandSourceStack source, String rawRunes) {

        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (CommandSyntaxException exception) {
            Component playerOnly = Component.literal("Apenas jogadores podem executar este feitico.");
            source.sendFailure(Objects.requireNonNull(playerOnly));
            return 0;
        }

        String trimmed = rawRunes == null ? "" : rawRunes.trim();
        List<String> lexemes = trimmed.isEmpty()
                ? List.of()
                : Arrays.stream(trimmed.split("\\s+"))
                .filter(token -> !token.isBlank())
                .toList();

        if (lexemes.isEmpty()) {
            Component failureComponent = Component.literal("Spell requer ao menos um termo.");
            source.sendFailure(Objects.requireNonNull(failureComponent));
            return 0;
        }

        SpellCastingService.Result result = CASTING_SERVICE.cast(player, lexemes);
        result.warnings().forEach(warning -> source.sendSuccess(() -> warning, false));

        if (result.success()) {
            LOGGER.debug("Spell actions resolved via /spell from {}", player.getGameProfile().getName());
            source.sendSuccess(() -> Objects.requireNonNull(result.message()), false);
            return 1;
        }

        source.sendFailure(Objects.requireNonNull(result.message()));
        return 0;
    }
}
