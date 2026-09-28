package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;

import java.util.Optional;

/**
 * How runes are written: each rune of the language has its letter of the Standard Galactic Alphabet in the lexicon, and
 * each digit is the letter {@code 'Q' + d} ({@link NumberGlyphs}). Both the grimoire and the spirit read through here,
 * so a rune an addon gives a glyph is written and read like the book's own.
 */
public final class Glyphs {

    private Glyphs() {
    }

    /** The glyph of a rune id, or of a single digit; empty when it has none. */
    public static Optional<Character> glyphForRune(String runeId) {
        if (runeId == null) {
            return Optional.empty();
        }
        String id = runeId.trim();
        if (id.length() == 1 && Character.isDigit(id.charAt(0))) {
            return Optional.of(NumberGlyphs.toGlyphs(id).charAt(0));
        }
        return Lexicons.get().rune(id).flatMap(Rune::glyph)
                .filter(glyph -> glyph.length() == 1)
                .map(glyph -> glyph.charAt(0));
    }

    /** The rune a glyph writes (a digit for {@code Q} to {@code Z}); empty for a letter no rune is written with. */
    public static Optional<String> runeForGlyph(char glyph) {
        char letter = Character.toUpperCase(glyph);
        Optional<String> digit = NumberGlyphs.read(String.valueOf(letter));
        if (digit.isPresent()) {
            return digit;
        }
        for (Rune rune : Lexicons.get().runes()) {
            if (rune.glyph().filter(written -> written.length() == 1 && written.charAt(0) == letter).isPresent()) {
                return Optional.of(rune.id());
            }
        }
        return Optional.empty();
    }
}
