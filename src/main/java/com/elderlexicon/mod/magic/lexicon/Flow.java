package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;

/**
 * What a verb does to the energy flowing through a spell (docs/exsugat-vertere-design.md, "Um fluxo só para todo
 * feitiço"). Every spell is one flow: where the energy comes from (the body, or the world when an origin filter says so),
 * what it is turned into on the way, and the verb that spends it. The flow is run by these roles alone, so any verb
 * declaring one takes part in it exactly like the runes of the book do.
 */
public enum Flow {
    /** Uses the energy in hand: the body's, what was taken from the world, or what was drawn out and converted. */
    SPEND,
    /**
     * Turns the energy in hand into another source ({@code vertere}): the energy taken from the world, the energy drawn
     * out of the body, or, with nothing else in hand, the body's own Vita.
     */
    CONVERT;

    public static Flow parse(String raw) {
        if (raw == null) {
            return SPEND;
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "convert" -> CONVERT;
            default -> SPEND;
        };
    }
}
