package com.elderlexicon.mod.spell.mark;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NumberGlyphsTest {

    /** A number in the digits' own glyphs. */
    private static String glyphs(String digits) {
        StringBuilder written = new StringBuilder();
        for (char c : digits.toCharArray()) {
            written.append(Character.isDigit(c) ? NumberGlyphs.glyph(c - '0') : c);
        }
        return written.toString();
    }

    @Test
    void theEditorWritesEveryDigitAsItsGlyph() {
        assertEquals(glyphs("20"), NumberGlyphs.toGlyphs("20"));
        assertEquals(glyphs("20"), NumberGlyphs.toGlyphs(glyphs("2") + "0"), "the 2 was already a glyph when the 0 was typed");
        assertEquals(glyphs("100"), NumberGlyphs.toGlyphs("100"));
    }

    @Test
    void theEditorLeavesOtherWordsAlone() {
        assertEquals("m1", NumberGlyphs.toGlyphs("m1"));
        assertEquals("casa", NumberGlyphs.toGlyphs("casa"));
        assertEquals(glyphs("20"), NumberGlyphs.toGlyphs(glyphs("20")), "already all glyphs");
        assertEquals("C", NumberGlyphs.toGlyphs("C"));
        assertEquals("SQ", NumberGlyphs.toGlyphs("SQ"), "the letters are no digits any more");
    }

    @Test
    void theReaderTurnsGlyphsBackIntoDigits() {
        assertEquals(Optional.of("20"), NumberGlyphs.read(glyphs("20")));
        assertEquals(Optional.of("20"), NumberGlyphs.read(glyphs("2") + "0"), "a number still being typed");
        assertEquals(Optional.of("-30"), NumberGlyphs.read("-" + glyphs("30")));
        assertEquals(Optional.of("10"), NumberGlyphs.read("10"));
        assertEquals(Optional.of("5"), NumberGlyphs.read(glyphs("5")));
    }

    @Test
    void lettersAndLowerCaseWordsAreNotNumbers() {
        assertTrue(NumberGlyphs.read("sq").isEmpty(), "a lower case word is a mark");
        assertTrue(NumberGlyphs.read("C").isEmpty(), "C is igni");
        assertTrue(NumberGlyphs.read("V").isEmpty(), "V is a rune now, not 5");
        assertTrue(NumberGlyphs.read("m1").isEmpty());
    }

    @Test
    void anOlderTextGetsTheDigitsOwnGlyphs() {
        assertEquals("C N " + glyphs("20") + " I", NumberGlyphs.upgrade("C N SQ I"));
        assertEquals("C O " + glyphs("0") + " I", NumberGlyphs.upgrade("C O Q I"), "a lone Q was zero");
        assertEquals("m1 " + glyphs("10") + " -" + glyphs("30") + " P", NumberGlyphs.upgrade("m1 RQ -TQ P"));
        assertEquals("N " + glyphs("20"), NumberGlyphs.upgrade("N S0"), "glyphs and digits mixed");
        assertEquals("C N 20 I\nm1 sq", NumberGlyphs.upgrade("C N 20 I\nm1 sq"), "digits and marks stay");
    }
}
