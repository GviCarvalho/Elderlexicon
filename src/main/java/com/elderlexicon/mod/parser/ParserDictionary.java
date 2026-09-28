package com.elderlexicon.mod.parser;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.magic.lexicon.Rune;
import com.elderlexicon.mod.magic.lexicon.WordClass;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The runes as the older parts of the mod see them (an id, a type, a translation, whether it takes a target), read from
 * the {@link Lexicon}. The lexicon is where the words live; this is only a window on it.
 */
public final class ParserDictionary {

    /** A fixed lexicon, or null to follow the one in force as addons change it. */
    private final Lexicon fixed;

    private ParserDictionary(Lexicon fixed) {
        this.fixed = fixed;
    }

    /** The dictionary of the lexicon in force, whatever addons add to it later. */
    public static ParserDictionary load() {
        return new ParserDictionary(null);
    }

    /** The dictionary of one lexicon. */
    public static ParserDictionary of(Lexicon lexicon) {
        return new ParserDictionary(Objects.requireNonNull(lexicon, "lexicon"));
    }

    public Lexicon lexicon() {
        return fixed != null ? fixed : Lexicons.get();
    }

    public Optional<RuneDefinition> lookup(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return lexicon().rune(token.toLowerCase(Locale.ROOT).trim()).map(ParserDictionary::definitionOf);
    }

    public Map<String, RuneDefinition> entries() {
        Map<String, RuneDefinition> entries = new LinkedHashMap<>();
        for (Rune rune : lexicon().runes()) {
            entries.put(rune.id(), definitionOf(rune));
        }
        return Collections.unmodifiableMap(entries);
    }

    /** How the older parts of the mod see a rune of the lexicon. */
    public static RuneDefinition definitionOf(Rune rune) {
        boolean requiresTarget = rune.verb().map(verb -> verb.awaitsTarget()).orElse(false)
                || rune.expansion().contains(Rune.NEXT_WORD);
        return new RuneDefinition(rune.id(), RuneType.of(rune.wordClass()), rune.translation(), requiresTarget);
    }

    public enum RuneType {
        SOURCE,
        FUNCTION,
        SHAPE,
        FILTER;

        static RuneType of(WordClass wordClass) {
            return switch (wordClass) {
                case SOURCE -> SOURCE;
                case VERB -> FUNCTION;
                case FILTER -> FILTER;
                case FORM -> SHAPE;
            };
        }
    }

    public record RuneDefinition(String id, RuneType type, String translation, boolean requiresTarget) { }
}
