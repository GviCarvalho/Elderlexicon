package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.Template;
import com.elderlexicon.mod.magic.lexicon.VerbSpec;
import com.elderlexicon.mod.parser.ParserDictionary.RuneDefinition;
import com.elderlexicon.mod.parser.ParserDictionary.RuneType;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionType;
import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Optional;

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
            return ParseResult.failure("Parser nao interpreta mais o comando 'spell'. Utilize o SpellActionEngine + Parser.transcribeActions.");
        }

        return ParseResult.success(command, arguments);
    }

    @Deprecated(forRemoval = true)
    public ParseResult parseSpell(List<String> lexemes) {
        return ParseResult.failure("Parser.parseSpell foi descontinuado. Use SpellActionEngine + Parser.transcribeActions().");
    }

    public SpellTranscript transcribeActions(List<SpellAction> actions) {
        return transcribeActions(actions, List.of(), Optional.empty());
    }

    public SpellTranscript transcribeActions(List<SpellAction> actions,
                                             List<String> lexemes,
                                             Optional<PrimarySource> preferredPrimarySource) {
        if (actions == null || actions.isEmpty()) {
            return SpellTranscript.failure("Spell requer ao menos uma ação.");
        }

        List<String> copiedLexemes = lexemes == null ? List.of() : List.copyOf(lexemes);
        SpellState state = new SpellState("", List.of());
        List<String> plainSources = new ArrayList<>();
        List<String> phrases = new ArrayList<>();
        boolean hasFunction = false;
        boolean suppressPlainSource = false;

        for (SpellAction action : actions) {
            if (action == null) {
                continue;
            }
            if (action.type() == SpellActionType.SOURCE) {
                String elementRuneId = resolveElementRuneId(action).orElse(action.runeId());
                String element = translateElement(action.element(), elementRuneId);
                state.reset(element, action.shapes());
                if (!hasFunction && !suppressPlainSource) {
                    plainSources.add(state.currentForm());
                }
                suppressPlainSource = false;
                continue;
            }
            if (action.type() == SpellActionType.FUNCTION) {
                String elementRuneId = resolveElementRuneId(action)
                        .orElseGet(() -> action.element() == null ? action.runeId() : null);
                String element = translateElement(action.element(), elementRuneId);
                state.reset(element, action.shapes());

                RuneDefinition definition = dictionary.lookup(action.runeId()).orElse(null);
                if (definition == null) {
                    phrases.add(capitalize(element));
                    hasFunction = true;
                    continue;
                }

                String targetElement = translateRune(action.targetRuneId());
                String phrase = action.subjectMark().isPresent() || action.targetMark().isPresent()
                        ? renderMarkedFunction(definition, action, targetElement)
                        : renderFunction(definition, state, targetElement);
                if (action.place().isPresent()) {
                    phrase = phrase + " at " + action.place().get().describe();
                }
                if (!phrase.isBlank()) {
                    phrases.add(phrase);
                    hasFunction = true;
                }

                if (action.targetRuneId() != null) {
                    suppressPlainSource = true;
                    if (targetElement != null) {
                        state.applyTransformation(targetElement);
                    } else {
                        state.markReferenced();
                    }
                } else {
                    state.markReferenced();
                }
            }
        }

        String response;
        if (!phrases.isEmpty()) {
            response = String.join(" and ", phrases);
        } else if (!plainSources.isEmpty()) {
            response = String.join(" ", plainSources);
        } else {
            response = dictionary.lexicon().note("transcript.done").orElse("Feitico interpretado com sucesso.");
        }

        String resolvedPrimarySourceId = resolvePrimarySourceId(copiedLexemes, preferredPrimarySource);
        return SpellTranscript.success(response, copiedLexemes, resolvedPrimarySourceId);
    }

    public Optional<RuneDefinition> lookup(String token) {
        return dictionary.lookup(token);
    }

    public Optional<PrimarySource> findPrimarySource(List<String> lexemes) {
        if (lexemes == null || lexemes.isEmpty()) {
            return Optional.empty();
        }

        RuneDefinition fallbackDefinition = dictionary.lexicon().defaultSource() == null ? null
                : ParserDictionary.definitionOf(dictionary.lexicon().defaultSource());
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

    private String resolvePrimarySourceId(List<String> lexemes, Optional<PrimarySource> preferredPrimarySource) {
        Optional<PrimarySource> safePreferred = preferredPrimarySource == null ? Optional.empty() : preferredPrimarySource;
        return safePreferred
                .map(primary -> primary.definition().id())
                .or(() -> findPrimarySource(lexemes).map(primary -> primary.definition().id()))
                .orElse(null);
    }

    private Optional<String> resolveElementRuneId(SpellAction action) {
        if (action == null) {
            return Optional.empty();
        }
        Object candidate = action.metadata().get("elementRuneId");
        if (candidate instanceof String runeId && !runeId.isBlank()) {
            return Optional.of(runeId);
        }
        return Optional.empty();
    }

    private String translateElement(VitaElement element, String preferredRuneId) {
        String runeId = preferredRuneId;
        if ((runeId == null || runeId.isBlank()) && element != null) {
            runeId = element.runeId();
        }
        if (runeId == null || runeId.isBlank()) {
            return "";
        }
        return translateRune(runeId);
    }

    private String translateRune(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return null;
        }
        return dictionary.lookup(runeId)
                .map(RuneDefinition::translation)
                .orElse(capitalize(runeId));
    }


    /**
     * How a verb is told in the transcript: its English verb, and for a verb that takes a target the target joined to
     * what it acts on ({@code Convert fire to water}). All of it comes from the lexicon.
     */
    private String renderFunction(RuneDefinition function, SpellState state, String targetElement) {
        Optional<Rune> rune = dictionary.lexicon().rune(function.id());
        String verb = verbOf(function, rune);
        String joiner = rune.flatMap(Rune::verb).map(VerbSpec::joiner).orElse(null);
        if (joiner != null && targetElement != null) {
            return verb + " " + state.element() + " " + joiner + " " + targetElement;
        }
        if (targetElement != null) {
            return verb + " " + targetElement;
        }
        return verb + " " + state.describe();
    }

    /** A verb acting on marked things: {@code Summon 'm1'}, {@code Swap 'm1' with 'm2'}. */
    private String renderMarkedFunction(RuneDefinition function, SpellAction action, String targetElement) {
        Optional<Rune> rune = dictionary.lexicon().rune(function.id());
        String caster = dictionary.lexicon().note("transcript.caster").orElse("the caster");
        String subject = action.subjectMark().map(mark -> "'" + mark + "'").orElse(caster);
        String target = action.targetMark().map(mark -> "'" + mark + "'").orElse(targetElement);
        Optional<String> template = rune.flatMap(found -> found.text("transcript.marked"));
        if (template.isPresent()) {
            Map<String, String> values = new HashMap<>();
            values.put("subject", subject);
            values.put("target", target == null ? "" : target);
            values.put("caster", caster);
            return Template.fill(template.get(), values);
        }
        String verb = verbOf(function, rune);
        return target == null ? verb + " " + subject : verb + " " + subject + " to " + target;
    }

    private static String verbOf(RuneDefinition function, Optional<Rune> rune) {
        return rune.flatMap(Rune::verb).map(VerbSpec::phrase).filter(phrase -> !phrase.isBlank())
                .orElseGet(() -> capitalize(function.translation()));
    }

    private static String capitalize(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static final class SpellState {
        private String element;
        private final List<String> shapes;
        private boolean prefersPronoun;

        private SpellState(String element, List<String> shapes) {
            this.element = element;
            this.shapes = new ArrayList<>(shapes);
            this.prefersPronoun = false;
        }

        void reset(String element, List<String> updatedShapes) {
            this.element = element == null ? "" : element;
            this.shapes.clear();
            if (updatedShapes != null) {
                updatedShapes.stream()
                        .map(shape -> shape == null ? "" : shape.trim())
                        .filter(shape -> !shape.isEmpty())
                        .forEach(this.shapes::add);
            }
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

        void applyTransformation(String element) {
            this.element = element;
            this.prefersPronoun = true;
        }

        void markReferenced() {
            this.prefersPronoun = true;
        }
    }
}
