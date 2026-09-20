package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionEngine;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.action.SpellCostProcessor;
import com.elderlexicon.mod.vita.VitaElement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lightweight terminal runner to exercise the parser without launching Minecraft.
 */
public final class ParserCli {

    private final Parser parser;
    private final SpellActionEngine actionEngine;
    private final SpellCostProcessor costProcessor;

    public ParserCli() {
        Parser parser = new Parser();
        this.parser = parser;
        this.actionEngine = new SpellActionEngine(ParserDictionary.load());
        this.costProcessor = new SpellCostProcessor();
    }

    public static void main(String[] args) throws IOException {
        ParserCli cli;
        try {
            cli = new ParserCli();
        } catch (IllegalStateException exception) {
            System.err.println("Falha ao inicializar o parser: " + exception.getMessage());
            return;
        }
        cli.run();
    }

    private void run() throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        PrintWriter writer = new PrintWriter(System.out, true);

        writer.println("Parser CLI iniciado. Use 'exit' para sair.");
        String line;
        while ((line = reader.readLine()) != null) {
            String trimmed = line.trim();
            if (trimmed.equalsIgnoreCase("exit")) {
                writer.println("Encerrando parser.");
                break;
            }

            if (isSpellCommand(trimmed)) {
                writer.println(formatSpellResponse(trimmed));
                continue;
            }

            ParseResult result = parser.parse(line);
            if (result.success()) {
                if (!result.message().isBlank()) {
                    writer.println(result.message());
                } else {
                    writer.printf("Comando: %s%n", result.command());
                    if (!result.arguments().isEmpty()) {
                        writer.printf("Argumentos: %s%n", String.join(", ", result.arguments()));
                    } else {
                        writer.println("Sem argumentos.");
                    }
                }
            } else {
                writer.printf("Erro: %s%n", result.message());
            }
        }
    }

    private boolean isSpellCommand(String input) {
        if (input == null || input.isBlank()) {
            return false;
        }
        String normalized = input.startsWith("/") ? input.substring(1).trim() : input.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        String[] tokens = normalized.split("\\s+");
        return tokens.length > 0 && "spell".equalsIgnoreCase(tokens[0]);
    }

    private String formatSpellResponse(String rawInput) {
        List<String> lexemes = extractSpellLexemes(rawInput);
        if (lexemes.isEmpty()) {
            return "Spell requer ao menos um termo.";
        }

        SpellActionResult actionResult = actionEngine.generateActions(lexemes);
        List<SpellAction> actions = actionResult.actions();
        if (actions.isEmpty()) {
            if (actionResult.hasIssues()) {
                return "Nao foi possivel interpretar o spell: " + String.join(", ", actionResult.issues());
            }
            return "Nao foi possivel interpretar o spell.";
        }

        double totalCost = requiresEnergyConsumption(actions)
            ? costProcessor.computeTotalCost(actions)
            : 0.0D;

        List<String> sanitizedLexemes = actionResult.lexemes();
        Optional<Parser.PrimarySource> primarySource = actionResult.primarySource();
        if (primarySource.isEmpty()) {
            primarySource = parser.findPrimarySource(sanitizedLexemes);
        }

        SpellTranscript transcript = parser.transcribeActions(actions, sanitizedLexemes, primarySource);
        if (!transcript.success()) {
            return transcript.message().isBlank() ? "Nao foi possivel transcrever o spell." : transcript.message();
        }

        Optional<Parser.PrimarySource> transcriptPrimary = transcript.primarySource()
                .flatMap(id -> parser.lookup(id).map(Parser.PrimarySource::new));
        if (transcriptPrimary.isPresent()) {
            primarySource = transcriptPrimary;
        }

        String response = transcript.message() == null || transcript.message().isBlank()
                ? "Spell interpretado com sucesso."
                : transcript.message();

        if (actionResult.hasIssues()) {
            response = response + " [Aviso: " + String.join(", ", actionResult.issues()) + "]";
        }

        if (totalCost > 0.0D) {
            String primaryLabel = primarySource
                    .map(primary -> capitalize(primary.definition().id()))
                    .orElse(capitalize(VitaElement.BALANCED.runeId()));
            String totalDescriptor = "Fonte: " + primaryLabel + " | Custo: " + SpellCostCalculator.formatCost(totalCost) + " UMU";
            response = response + " (" + totalDescriptor + ")";
        }

        return response;
    }

    private List<String> extractSpellLexemes(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return List.of();
        }
        String normalized = rawInput.startsWith("/") ? rawInput.substring(1).trim() : rawInput.trim();
        if (normalized.isEmpty()) {
            return List.of();
        }
        String[] tokens = normalized.split("\\s+");
        if (tokens.length <= 1 || !"spell".equalsIgnoreCase(tokens[0])) {
            return List.of();
        }
        return Arrays.stream(tokens)
                .skip(1)
                .map(token -> token == null ? "" : token.trim())
                .filter(token -> !token.isEmpty())
                .toList();
    }

    private static boolean requiresEnergyConsumption(List<SpellAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (SpellAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.type() == com.elderlexicon.mod.spell.action.SpellActionType.FUNCTION
                    && !"exsugat".equalsIgnoreCase(action.runeId())) {
                return true;
            }
        }
        return false;
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
