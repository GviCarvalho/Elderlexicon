package com.elderlexicon.mod.spell.mark;

import java.util.Locale;
import java.util.OptionalDouble;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Tells what a written word is: a rune of the language, a number, a mark (a free word naming the
 * things that carry it) or nothing the spirit understands.
 * <p>
 * Order matters: a rune always wins, then a number, then a mark. A mark needs two characters or more
 * and at least one letter, because a lone letter is read as a rune glyph and digits alone are a number.
 */
public final class SpellWords {

    public enum Kind {
        RUNE,
        NUMBER,
        MARK,
        UNKNOWN
    }

    private static final Pattern NUMBER = Pattern.compile("-?\\d+");
    private static final Pattern MARK = Pattern.compile("[\\p{L}\\p{N}_]{2,}");
    private static final Pattern HAS_LETTER = Pattern.compile(".*\\p{L}.*");

    private SpellWords() {
    }

    /** @param isRune says whether a word is a rune of the dictionary */
    public static Kind classify(String word, Predicate<String> isRune) {
        String normalized = normalize(word);
        if (normalized == null) {
            return Kind.UNKNOWN;
        }
        if (isRune != null && isRune.test(normalized)) {
            return Kind.RUNE;
        }
        if (NUMBER.matcher(normalized).matches()) {
            return Kind.NUMBER;
        }
        if (MARK.matcher(normalized).matches() && HAS_LETTER.matcher(normalized).matches()) {
            return Kind.MARK;
        }
        return Kind.UNKNOWN;
    }

    /** The value of a number word, or empty when the word is not a number. */
    public static OptionalDouble number(String word) {
        String normalized = normalize(word);
        if (normalized == null || !NUMBER.matcher(normalized).matches()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Long.parseLong(normalized));
        } catch (NumberFormatException tooLong) {
            return OptionalDouble.empty();
        }
    }

    private static String normalize(String word) {
        if (word == null) {
            return null;
        }
        String trimmed = word.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? null : trimmed;
    }
}
