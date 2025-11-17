package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.parser.ParserDictionary.RuneDefinition;
import com.elderlexicon.mod.parser.ParserDictionary.RuneType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Simple baseline parser that understands slash-prefixed commands.
 */
public class Parser {

    private final ParserDictionary dictionary;

    public Parser() {
        this(ParserDictionary.load());
    }

    public Parser(ParserDictionary dictionary) {
        this.dictionary = dictionary;
    }

    public ParseResult parse(String rawInput) {
        if (rawInput == null) {
            return ParseResult.failure("Input cannot be null");
        }

        String trimmed = rawInput.trim();
        if (trimmed.isEmpty()) {
            return ParseResult.failure("Input is empty");
        }

        if (!trimmed.startsWith("/")) {
            return ParseResult.failure("Commands must start with '/'");
        }

        String withoutPrefix = trimmed.substring(1).trim();
        if (withoutPrefix.isEmpty()) {
            return ParseResult.failure("Missing command name");
        }

        String[] tokens = withoutPrefix.split("\\s+");
        String command = tokens[0].toLowerCase(Locale.ROOT);
        List<String> arguments = tokens.length > 1 ? Arrays.asList(tokens).subList(1, tokens.length) : List.of();

        if ("spell".equals(command)) {
            return parseSpell(arguments);
        }

        return ParseResult.success(command, arguments);
    }

    public ParseResult parseSpell(List<String> lexemes) {
        if (lexemes == null) {
            return ParseResult.failure("Spell requer ao menos um termo.");
        }

        List<String> sanitized = lexemes.stream()
                .map(token -> token == null ? "" : token.trim())
                .filter(token -> !token.isEmpty())
                .toList();

        if (sanitized.isEmpty()) {
            return ParseResult.failure("Spell requer ao menos um termo.");
        }

        return handleSpell(sanitized);
    }

    public Optional<RuneDefinition> lookup(String token) {
        return dictionary.lookup(token);
    }

    public Optional<PrimarySource> findPrimarySource(List<String> lexemes) {
        if (lexemes == null || lexemes.isEmpty()) {
            return Optional.empty();
        }

        RuneDefinition fallbackDefinition = dictionary.lookup("vis").orElse(null);
        PrimarySource fallback = fallbackDefinition != null ? new PrimarySource(fallbackDefinition) : null;

        PrimarySource latestSource = null;

        for (String lexeme : lexemes) {
            RuneDefinition definition = dictionary.lookup(lexeme).orElse(null);
            if (definition == null) {
                continue;
            }

            RuneType type = definition.type();
            if (type == RuneType.SHAPE) {
                continue;
            }

            if (type == RuneType.SOURCE) {
                latestSource = new PrimarySource(definition);
                continue;
            }

            if (type == RuneType.FUNCTION) {
                if (latestSource != null) {
                    return Optional.of(latestSource);
                }
                return Optional.ofNullable(fallback);
            }
        }

        if (latestSource != null) {
            return Optional.of(latestSource);
        }

        return Optional.ofNullable(fallback);
    }

    public record PrimarySource(RuneDefinition definition) { }

    private ParseResult handleSpell(List<String> lexemes) {
        List<RuneToken> runes = new ArrayList<>(lexemes.size());
        for (String lexeme : lexemes) {
            RuneDefinition definition = dictionary.lookup(lexeme)
                    .orElse(null);
            if (definition == null) {
                return ParseResult.failure("Lexema desconhecido: " + lexeme);
            }
            runes.add(new RuneToken(lexeme, definition, false));
        }

        if (runes.isEmpty()) {
            return ParseResult.failure("Spell requer ao menos um termo.");
        }

        int cursor = 0;
        List<String> shapes = new ArrayList<>();
        while (cursor < runes.size() && runes.get(cursor).definition().type() == RuneType.SHAPE) {
            shapes.add(runes.get(cursor).definition().translation());
            cursor++;
        }

        if (cursor >= runes.size()) {
            return ParseResult.failure("Spell requer uma fonte após a forma.");
        }

        RuneToken head = runes.get(cursor);
        if (head.definition().type() != RuneType.SOURCE) {
            RuneDefinition fallbackSource = dictionary.lookup("vis").orElse(null);
            if (fallbackSource == null) {
                return ParseResult.failure("Não foi possível inferir a fonte padrão 'vis'.");
            }
            runes.add(cursor, new RuneToken("vis", fallbackSource, true));
            head = runes.get(cursor);
        }

        if (head.definition().type() != RuneType.SOURCE) {
            return ParseResult.failure("Um feitiço deve iniciar com uma fonte.");
        }

        SpellState state = new SpellState(head.definition().translation(), shapes);
        List<String> plainSources = new ArrayList<>();
        plainSources.add(state.currentForm());
        List<String> phrases = new ArrayList<>();
        boolean hasFunction = false;

        PendingFunction pendingFunction = null;
        for (int index = cursor + 1; index < runes.size(); index++) {
            RuneToken rune = runes.get(index);
            RuneType type = rune.definition().type();

            if (type == RuneType.SHAPE) {
                if (pendingFunction != null) {
                    return ParseResult.failure("A função '" + pendingFunction.function().translation() + "' precisa de uma fonte seguinte.");
                }
                state.addShape(rune.definition().translation());
                if (!hasFunction && !plainSources.isEmpty()) {
                    plainSources.set(plainSources.size() - 1, state.currentForm());
                }
                continue;
            }

            if (type == RuneType.FUNCTION) {
                if (pendingFunction != null) {
                    return ParseResult.failure("A função '" + pendingFunction.function().translation() + "' precisa de uma fonte seguinte.");
                }

                if (rune.definition().requiresTarget()) {
                    pendingFunction = new PendingFunction(rune.definition());
                } else {
                    phrases.add(renderFunction(rune.definition(), state, null));
                    hasFunction = true;
                    state.markReferenced();
                }
            } else { // SOURCE
                if (pendingFunction != null) {
                    String targetElement = rune.definition().translation();
                    String phrase = renderFunction(pendingFunction.function(), state, targetElement);
                    phrases.add(phrase);
                    hasFunction = true;
                    state.applyTransformation(targetElement);
                    pendingFunction = null;
                } else {
                    String element = rune.definition().translation();
                    state.setElement(element);
                    if (!hasFunction) {
                        plainSources.add(state.currentForm());
                    }
                }
            }
        }

        if (pendingFunction != null) {
            return ParseResult.failure("A função '" + pendingFunction.function().translation() + "' precisa de uma fonte seguinte.");
        }

        String response;
        if (!phrases.isEmpty()) {
            response = String.join(" and ", phrases);
        } else {
            response = plainSources.stream().collect(Collectors.joining(" "));
        }

        List<String> originalLexemes = List.copyOf(lexemes);
        String primarySourceId = findPrimarySource(originalLexemes)
                .map(primary -> primary.definition().id())
                .orElse(null);
        return ParseResult.success("spell", originalLexemes, response, primarySourceId);
    }

    private String renderFunction(RuneDefinition function, SpellState state, String targetElement) {
        return switch (function.id()) {
            case "vertere" -> {
                if (targetElement == null) {
                    throw new IllegalStateException("Função 'vertere' requer alvo.");
                }
                yield "Convert " + state.element() + " to " + targetElement;
            }
            case "transiectio" -> {
                if (targetElement == null) {
                    throw new IllegalStateException("Função 'transiectio' requer alvo.");
                }
                yield "Transition " + state.element() + " to " + targetElement;
            }
            case "cohaesio" -> {
                if (targetElement == null) {
                    throw new IllegalStateException("Função 'cohaesio' requer alvo.");
                }
                yield "Fuse " + state.element() + " with " + targetElement;
            }
            case "transvocatio" -> {
                if (targetElement == null) {
                    throw new IllegalStateException("Função 'transvocatio' requer alvo.");
                }
                yield "Transfer " + state.element() + " to " + targetElement;
            }
            case "extractio" -> {
                if (targetElement == null) {
                    throw new IllegalStateException("Função 'extractio' requer alvo.");
                }
                yield "Converge " + state.element() + " with " + targetElement;
            }
            case "vocant" -> "Summon " + state.describe();
            case "impediunt" -> "Repel " + state.describe();
            case "ligabis" -> "Bind " + state.describe();
            case "exsugat" -> "Drain " + state.describe();
            case "aversio" -> "Repulse " + state.describe();
            case "exhaustio" -> "Exhaust " + state.describe();
            case "deflectio" -> "Deflect " + state.describe();
            case "vinculatio" -> "Spellbind " + state.describe();
            case "exsuctio" -> "Channel " + state.describe();
            case "evocatio" -> "Conjure " + state.describe();
            case "compeditio" -> "Tether " + state.describe();
            case "exinanitio" -> "Leech " + state.describe();
            case "coniuratio" -> "Conspire " + state.describe();
            default -> {
                String action = capitalize(function.translation());
                if (targetElement != null) {
                    yield action + " " + targetElement;
                }
                yield action + " " + state.describe();
            }
        };
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private record RuneToken(String lexeme, RuneDefinition definition, boolean implicit) { }

    private record PendingFunction(RuneDefinition function) { }

    private static final class SpellState {
        private String element;
        private final List<String> shapes;
        private boolean prefersPronoun;

        private SpellState(String element, List<String> shapes) {
            this.element = element;
            this.shapes = new ArrayList<>(shapes);
            this.prefersPronoun = false;
        }

        String element() {
            return element;
        }

        String currentForm() {
            if (shapes.isEmpty()) {
                return element;
            }
            return element + " " + String.join(" ", shapes);
        }

        String describe() {
            return prefersPronoun ? "it" : currentForm();
        }

        void addShape(String shape) {
            if (shape != null && !shape.isBlank()) {
                shapes.add(shape);
                prefersPronoun = false;
            }
        }

        void setElement(String element) {
            this.element = element;
            this.prefersPronoun = false;
        }

        void applyTransformation(String element) {
            this.element = element;
            this.prefersPronoun = true;
        }

        void markReferenced() {
            this.prefersPronoun = true;
        }
    }
}
