package com.elderlexicon.mod.spell.mark;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class NumberGlyphsTest {

    @Test
    void theEditorWritesEveryDigitAsItsGlyph() {
        assertEquals("SQ", NumberGlyphs.toGlyphs("20"));
        assertEquals("SQ", NumberGlyphs.toGlyphs("S0"), "the 2 was already a glyph when the 0 was typed");
        assertEquals("RQQ", NumberGlyphs.toGlyphs("R00"));
    }

    @Test
    void theEditorLeavesOtherWordsAlone() {
        assertEquals("m1", NumberGlyphs.toGlyphs("m1"));
        assertEquals("casa", NumberGlyphs.toGlyphs("casa"));
        assertEquals("SQ", NumberGlyphs.toGlyphs("SQ"), "already all glyphs");
        assertEquals("C", NumberGlyphs.toGlyphs("C"));
    }

    @Test
    void theReaderTurnsGlyphsBackIntoDigits() {
        assertEquals(Optional.of("20"), NumberGlyphs.read("SQ"));
        assertEquals(Optional.of("20"), NumberGlyphs.read("S0"), "pages written before the fix");
        assertEquals(Optional.of("-30"), NumberGlyphs.read("-TQ"));
        assertEquals(Optional.of("10"), NumberGlyphs.read("10"));
        assertEquals(Optional.of("5"), NumberGlyphs.read("V"));
    }

    @Test
    void lowerCaseWordsAndRuneGlyphsAreNotNumbers() {
        assertTrue(NumberGlyphs.read("sq").isEmpty(), "a lower case word is a mark");
        assertTrue(NumberGlyphs.read("C").isEmpty(), "C is igni");
        assertTrue(NumberGlyphs.read("m1").isEmpty());
    }
}
