package com.elderlexicon.mod.spell.mark;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SpellWordsTest {

    private static final Predicate<String> RUNES = Set.of("igni", "vocant", "ubis")::contains;

    @Test
    void runesWinOverEverythingElse() {
        assertEquals(SpellWords.Kind.RUNE, SpellWords.classify("igni", RUNES));
        assertEquals(SpellWords.Kind.RUNE, SpellWords.classify(" Vocant ", RUNES));
    }

    @Test
    void digitsAreNumbers() {
        assertEquals(SpellWords.Kind.NUMBER, SpellWords.classify("10", RUNES));
        assertEquals(SpellWords.Kind.NUMBER, SpellWords.classify("5", RUNES));
        assertEquals(SpellWords.Kind.NUMBER, SpellWords.classify("-30", RUNES));
        assertEquals(-30.0D, SpellWords.number("-30").orElseThrow());
    }

    @Test
    void marksNeedTwoCharactersAndALetter() {
        assertEquals(SpellWords.Kind.MARK, SpellWords.classify("m1", RUNES));
        assertEquals(SpellWords.Kind.MARK, SpellWords.classify("casa", RUNES));
        assertEquals(SpellWords.Kind.MARK, SpellWords.classify("chave_2", RUNES));
        assertEquals(SpellWords.Kind.UNKNOWN, SpellWords.classify("m", RUNES), "a lone letter is a glyph");
        assertEquals(SpellWords.Kind.UNKNOWN, SpellWords.classify("???", RUNES));
        assertEquals(SpellWords.Kind.UNKNOWN, SpellWords.classify("", RUNES));
    }

    @Test
    void numbersAreNotMarks() {
        assertTrue(SpellWords.number("m1").isEmpty());
    }
}
