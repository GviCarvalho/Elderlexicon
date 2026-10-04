package com.elderlexicon.mod.spell.mark;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Numbers as the Old Tongue writes them: one glyph per digit, side by side, read left to right as one
 * value (book, cap. 4.3.2). The digits have glyphs of their own, apart from the letters (the font
 * {@code elderlexicon:sga} draws them): the glyph of digit {@code d} is the character {@code U+E000 + d}.
 * <p>
 * Older pages wrote digit {@code d} as the letter {@code 'Q' + d} ({@code SQ} was 20); those letters are
 * now runes of their own, so an older text is turned into the new writing once, by {@link #upgrade}.
 */
public final class NumberGlyphs {

    /** The glyph of zero; the other digits follow it. */
    public static final char ZERO_GLYPH = '\uE000';
    /** The letter that was zero's glyph before the digits had their own. */
    private static final char LEGACY_ZERO = 'Q';
    /** Digits and digit glyphs mixed, with at least one digit: what the editor has while a number is typed. */
    private static final Pattern TYPED_NUMBER = Pattern.compile("(?=.*[0-9])[0-9\uE000-\uE009]+");
    private static final Pattern WRITTEN_NUMBER = Pattern.compile("-?[0-9\uE000-\uE009]+");
    /** A number in the older writing: a word of digits and the letters Q to Z, with at least one letter. */
    private static final Pattern LEGACY_NUMBER =
            Pattern.compile("(?<=^|\\s)(-?[0-9Q-Z]*[Q-Z][0-9Q-Z]*)(?=$|\\s)");

    private NumberGlyphs() {
    }

    /** The glyph of one digit ({@code 0} to {@code 9}). */
    public static char glyph(int digit) {
        return (char) (ZERO_GLYPH + digit);
    }

    public static boolean isDigitGlyph(char character) {
        return character >= ZERO_GLYPH && character <= ZERO_GLYPH + 9;
    }

    /** Turns every digit of a number being typed into its glyph ({@code 20} to its two glyphs); other words are left alone. */
    public static String toGlyphs(String word) {
        if (word == null || !TYPED_NUMBER.matcher(word).matches()) {
            return word;
        }
        StringBuilder glyphs = new StringBuilder(word.length());
        for (char character : word.toCharArray()) {
            glyphs.append(Character.isDigit(character) ? glyph(character - '0') : character);
        }
        return glyphs.toString();
    }

    /** The digits of a written number (its glyphs, or plain digits), or empty when the word is not one. */
    public static Optional<String> read(String word) {
        if (word == null || !WRITTEN_NUMBER.matcher(word).matches()) {
            return Optional.empty();
        }
        StringBuilder digits = new StringBuilder(word.length());
        for (char character : word.toCharArray()) {
            digits.append(isDigitGlyph(character) ? (char) ('0' + (character - ZERO_GLYPH)) : character);
        }
        return Optional.of(digits.toString());
    }

    /**
     * A text in the older writing, written anew: every word that was a number ({@code SQ}, {@code -T}, {@code S0})
     * gets the digits' own glyphs. Only for a text known to be older: in the new writing, a lone {@code S} is a rune.
     */
    public static String upgrade(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = LEGACY_NUMBER.matcher(text);
        StringBuilder upgraded = new StringBuilder(text.length());
        while (matcher.find()) {
            StringBuilder number = new StringBuilder();
            for (char character : matcher.group(1).toCharArray()) {
                if (character >= LEGACY_ZERO && character <= LEGACY_ZERO + 9) {
                    number.append(glyph(character - LEGACY_ZERO));
                } else if (Character.isDigit(character)) {
                    number.append(glyph(character - '0'));
                } else {
                    number.append(character);
                }
            }
            matcher.appendReplacement(upgraded, Matcher.quoteReplacement(number.toString()));
        }
        matcher.appendTail(upgraded);
        return upgraded.toString();
    }
}
