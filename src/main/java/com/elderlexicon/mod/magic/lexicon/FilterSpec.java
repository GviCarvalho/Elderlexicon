package com.elderlexicon.mod.magic.lexicon;

import java.util.Locale;

/**
 * What a filter sets and how it takes its value (book 4.3.2).
 *
 * @param parameter what it sets on the verb after it
 * @param argument  how it takes its value: the number right after it (or right before it), or the numbers and marks
 *                  written before it ({@code m1 ubis}, {@code 10 64 -30 ubis}); an origin needs none
 *                  ({@code firmo tenet}: within the mage's reach)
 * @param bareAll   written with no number, it asks for all there is ({@code firmo tenet quantum iactare})
 */
public record FilterSpec(Parameter parameter, Argument argument, boolean bareAll) {

    /** How a filter takes its value. */
    public enum Argument {
        /** One number, the one right after it; the one right before it is accepted too ({@code 20 quantum}). */
        VALUE,
        /** Everything written right before it (one, two or three numbers or marks); numbers after it are accepted. */
        OPERANDS;

        public static Argument parse(String raw) {
            if (raw == null) {
                return VALUE;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "operands" -> OPERANDS;
                default -> VALUE;
            };
        }
    }
}
