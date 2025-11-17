package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.command.SpellCostCalculator;
import com.elderlexicon.mod.vita.VitaElement;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Lightweight terminal runner to exercise the parser without launching Minecraft.
 */
public final class ParserCli {

    private final Parser parser;

    public ParserCli() {
        this.parser = new Parser();
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

            ParseResult result = parser.parse(line);
            if (result.success()) {
                if ("spell".equalsIgnoreCase(result.command())) {
                    writer.println(formatSpellResponse(result));
                } else if (!result.message().isBlank()) {
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

    private String formatSpellResponse(ParseResult result) {
        List<String> lexemes = result.arguments() == null ? List.of() : result.arguments();
        double totalCost = SpellCostCalculator.computeTotalCost(lexemes);

        Optional<Parser.PrimarySource> primarySource = result.primarySource()
                .flatMap(id -> parser.lookup(id).map(Parser.PrimarySource::new));
        if (primarySource.isEmpty()) {
            primarySource = parser.findPrimarySource(lexemes);
        }

        String response = result.message() == null || result.message().isBlank()
                ? "Spell interpretado com sucesso."
                : result.message();

        if (totalCost > 0.0D) {
            String primaryLabel = primarySource
                    .map(primary -> capitalize(primary.definition().id()))
                    .orElse(capitalize(VitaElement.BALANCED.runeId()));
            String totalDescriptor = "Fonte: " + primaryLabel + " | Custo: " + SpellCostCalculator.formatCost(totalCost) + " UMU";
            response = response + " (" + totalDescriptor + ")";
        }

        return response;
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
