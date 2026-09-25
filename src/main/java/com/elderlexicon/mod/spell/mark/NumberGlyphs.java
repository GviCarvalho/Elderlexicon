package com.elderlexicon.mod.spell.mark;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Numbers as the Old Tongue writes them: one glyph per digit, side by side, read left to right as one
 * value (book, cap. 4.3.2). The glyph of digit {@code d} is the letter {@code 'Q' + d}, so {@code 20} is
 * written {@code SQ} and {@code -30} is {@code -TQ}.
 * <p>
 * Glyphs are upper case, as the grimoire writes them; a lower case word stays a mark, so {@code sq} is a
 * mark and {@code SQ} is the number 20.
 */
public final class NumberGlyphs {

    private static final char ZERO_GLYPH = 'Q';
    /** Digits and digit glyphs mixed, with at least one digit: what the editor has while a number is typed. */
    private static final Pattern TYPED_NUMBER = Pattern.compile("(?=.*[0-9])[0-9Q-Z]+");
    private static final Pattern WRITTEN_NUMBER = Pattern.compile("-?[0-9Q-Z]+");

    private NumberGlyphs() {
    }

    /** Turns every digit of a number being typed into its glyph ({@code S0} to {@code SQ}); other words are left alone. */
    public static String toGlyphs(String word) {
        if (word == null || !TYPED_NUMBER.matcher(word).matches()) {
            return word;
        }
        StringBuilder glyphs = new StringBuilder(word.length());
        for (char character : word.toCharArray()) {
            glyphs.append(Character.isDigit(character) ? (char) (ZERO_GLYPH + (character - '0')) : character);
        }
        return glyphs.toString();
    }

    /** The digits of a written number ({@code -TQ} to {@code -30}), or empty when the word is not one. */
    public static Optional<String> read(String word) {
        if (word == null || !WRITTEN_NUMBER.matcher(word).matches()) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder(word.length());
        for (char character : word.toCharArray()) {
            if (character >= ZERO_GLYPH && character <= ZERO_GLYPH + 9) {
                digits.append((char) ('0' + (character - ZERO_GLYPH)));
            } else {
                digits.append(character);
            }
        }
        return Optional.of(digits.toString());
    }
}
