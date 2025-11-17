package com.elderlexicon.mod.command;

import com.elderlexicon.mod.parser.ParseResult;
import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.SpellContext;
import com.elderlexicon.mod.spell.SpellModule;
import com.elderlexicon.mod.spell.module.SpellExecutionModule;
import com.elderlexicon.mod.spell.module.SpellCostModule;
import com.elderlexicon.mod.vita.VitaElement;
import com.elderlexicon.mod.vita.VitaScoreboardManager;
import com.elderlexicon.mod.vita.VitaSystem;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Registers and executes the /spell command, translating rune inputs into readable descriptions.
 */
@SuppressWarnings("null")
public final class SpellCommand {

    private static final double EPSILON = 1.0E-4D;

    private static final List<SpellModule> MODULES = List.of(
            new SpellExecutionModule(),
            new SpellCostModule()
    );

    private SpellCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> builder = Commands.literal("spell")
                .executes(context -> execute(context.getSource(), ""))
                .then(Commands.argument("runes", Objects.requireNonNull(StringArgumentType.greedyString()))
                        .executes(context -> execute(context.getSource(), StringArgumentType.getString(context, "runes"))));

        dispatcher.register(builder);
    }

    private static Parser cachedParser;

    private static int execute(CommandSourceStack source, String rawRunes) {
        Parser parser = getParser(source);
        if (parser == null) {
            return 0;
        }

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

        ParseResult result = parser.parseSpell(lexemes);
        if (!result.success()) {
            String failureMessage = result.message() == null || result.message().isBlank()
                    ? "Nao foi possivel interpretar o feitico."
                    : result.message();
            Component failureComponent = Component.literal(Objects.requireNonNull(failureMessage));
            source.sendFailure(Objects.requireNonNull(failureComponent));
            return 0;
        }

        List<String> arguments = result.arguments() == null ? List.of() : result.arguments();
        Optional<Parser.PrimarySource> primarySource = result.primarySource()
                .flatMap(id -> parser.lookup(id).map(Parser.PrimarySource::new));
        if (primarySource.isEmpty()) {
            primarySource = parser.findPrimarySource(arguments);
        }

        double totalCost = requiresEnergyConsumption(parser, arguments)
                ? SpellCostCalculator.computeTotalCost(arguments)
                : 0.0D;
        VitaElement primaryElement = primarySource
                .map(primary -> VitaElement.fromRuneId(primary.definition().id()))
                .orElse(VitaElement.BALANCED);

        SpellContext context = new SpellContext(parser, player, arguments, primarySource, primaryElement, totalCost);
        for (SpellModule module : MODULES) {
            module.apply(context);
        }
        context.commitAmbientEnergy();

        double environmentalContribution = context.environmentalContribution();
        double payableCost = context.payableCost();

        String response = result.message() == null || result.message().isBlank()
                ? "Feitico interpretado com sucesso."
                : result.message();

        if (totalCost > EPSILON || environmentalContribution > EPSILON) {
            String primaryLabel = primarySource
                    .map(primary -> capitalize(primary.definition().id()))
                    .orElse(capitalize(VitaElement.BALANCED.runeId()));
            StringBuilder descriptor = new StringBuilder("Fonte: ")
                    .append(primaryLabel)
                    .append(" | Custo: ")
                    .append(SpellCostCalculator.formatCost(payableCost))
                    .append(" UMU");
            if (environmentalContribution > EPSILON) {
                descriptor.append(" | Ambiente: ")
                        .append(SpellCostCalculator.formatCost(environmentalContribution))
                        .append(" UMU");
            }
            response = response + " (" + descriptor + ")";
        }

        Component outputComponent = Component.literal(Objects.requireNonNull(response));
        source.sendSuccess(() -> Objects.requireNonNull(outputComponent), false);

        VitaScoreboardManager.update(player, VitaSystem.fromPlayer(player));
        return 1;
    }

    private static Parser getParser(CommandSourceStack source) {
        if (cachedParser != null) {
            return cachedParser;
        }
        try {
            cachedParser = new Parser();
            return cachedParser;
        } catch (IllegalStateException exception) {
            String errorText = "Parser indisponivel: " + Objects.requireNonNullElse(exception.getMessage(), "erro desconhecido");
            Component errorComponent = Component.literal(errorText);
            source.sendFailure(Objects.requireNonNull(errorComponent));
            return null;
        }
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private static boolean requiresEnergyConsumption(Parser parser, List<String> lexemes) {
        if (parser == null || lexemes == null || lexemes.isEmpty()) {
            return false;
        }
        for (String lexeme : lexemes) {
            ParserDictionary.RuneDefinition definition = parser.lookup(lexeme).orElse(null);
            if (definition == null) {
                continue;
            }
            if (definition.type() == ParserDictionary.RuneType.FUNCTION
                    && !"exsugat".equalsIgnoreCase(definition.id())) {
                return true;
            }
        }
        return false;
    }
}
