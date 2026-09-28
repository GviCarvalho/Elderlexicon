package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;

/** Where a rune comes from: one of the few the language is made of, or a fusion of them. */
public enum Origin {
    /** A rune of the language itself (the book's "original"). */
    PRIMORDIAL,
    /** A rune made of others ({@code fusus} is igni and firmo); its meaning comes from its parts. */
    FUSION;

    public static Origin parse(String raw) {
        if (raw == null) {
            return PRIMORDIAL;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "fusion" -> FUSION;
            default -> PRIMORDIAL;
        };
    }
}
