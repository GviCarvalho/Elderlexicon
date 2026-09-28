package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;
import java.util.Optional;

/**
 * What a rune is in a sentence of the Old Tongue (book 5.1.1). The grammar only ever asks a rune for its class and the
 * frames it declares, never for its name, so a new rune of any class works in every spell the moment it is in the
 * lexicon.
 */
public enum WordClass {
    /** A source: which energy the spell moves ({@code igni}, {@code vis}, a fusion such as {@code fusus}). */
    SOURCE,
    /** A verb (the book's "function"): what the spirit does with what came before it ({@code iactare}). */
    VERB,
    /** A filter: how much, when or where ({@code quantum}, {@code chronos}, {@code ubis}). */
    FILTER,
    /** A form: the shape of what is made ({@code hasta}, {@code murus}); it only colours the spell. */
    FORM;

    /** Reads a class as the lexicon writes it; the older names ({@code function}, {@code shape}) still count. */
    public static Optional<WordClass> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "source" -> Optional.of(SOURCE);
            case "verb", "function" -> Optional.of(VERB);
            case "filter" -> Optional.of(FILTER);
            case "form", "shape" -> Optional.of(FORM);
            default -> Optional.empty();
        };
    }
}
