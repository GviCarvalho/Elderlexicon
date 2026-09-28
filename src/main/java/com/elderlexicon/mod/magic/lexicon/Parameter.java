package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;
import java.util.Optional;

/** What a filter sets on the verb after it (book 4.3.2). */
public enum Parameter {
    /** How much: UMU ({@code quantum}). Must be above zero; written with no number, it is all there is. */
    QUANTITY,
    /** How long, or when: seconds ({@code chronos}). Never negative ("matter for another volume"). */
    TIME,
    /** Where: a distance, a distance and a height, coordinates or a mark ({@code ubis}). */
    PLACE;

    public static Optional<Parameter> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "quantity" -> Optional.of(QUANTITY);
            case "time" -> Optional.of(TIME);
            case "place" -> Optional.of(PLACE);
            default -> Optional.empty();
        };
    }
}
