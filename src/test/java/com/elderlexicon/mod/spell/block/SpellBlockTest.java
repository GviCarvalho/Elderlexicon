package com.elderlexicon.mod.spell.block;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

class SpellBlockTest {

    private static String normalize(String token) {
        return token.startsWith("#") ? "" : token.toLowerCase(Locale.ROOT);
    }

    @Test
    void singleLineIsOneSpellWithNoDelay() {
        SpellBlock block = SpellBlock.parse("igni exsugat iactare", SpellBlockTest::normalize);

        assertEquals(1, block.lines().size());
        assertEquals(List.of("igni", "exsugat", "iactare"), block.lines().get(0).runeIds());
        assertEquals(0, block.delaySteps(block.lines().get(0)));
    }

    @Test
    void bookExampleReleasesBothLinesTogether() {
        // vis vertere aura vocant  -> release at 3
        //     vertere aqua vocant  -> row 1 + col 2 = 3
        SpellBlock block = SpellBlock.parse("vis vertere aura vocant\nvertere aqua vocant", SpellBlockTest::normalize);

        assertEquals(2, block.lines().size());
        assertEquals(3, block.lines().get(0).releasePosition());
        assertEquals(3, block.lines().get(1).releasePosition());
        assertEquals(0, block.delaySteps(block.lines().get(0)));
        assertEquals(0, block.delaySteps(block.lines().get(1)));
    }

    @Test
    void linesStayIndependentInsteadOfFusing() {
        SpellBlock block = SpellBlock.parse("vis vertere aqua iactare\nvertere aura iactare", SpellBlockTest::normalize);

        assertEquals(List.of("vis", "vertere", "aqua", "iactare"), block.lines().get(0).runeIds());
        assertEquals(List.of("vertere", "aura", "iactare"), block.lines().get(1).runeIds());
    }

    @Test
    void longerLineIsReleasedLater() {
        SpellBlock block = SpellBlock.parse("aqua vocant\nigni quantum 5 vocant", SpellBlockTest::normalize);

        SpellBlock.Line first = block.lines().get(0);
        SpellBlock.Line second = block.lines().get(1);
        assertEquals(1, first.releasePosition());
        assertEquals(4, second.releasePosition());
        assertEquals(0, block.delaySteps(first));
        assertEquals(3, block.delaySteps(second));
    }

    @Test
    void discardedTokensStillOccupyTheirColumn() {
        SpellBlock block = SpellBlock.parse("igni #x iactare", SpellBlockTest::normalize);

        assertEquals(List.of("igni", "iactare"), block.lines().get(0).runeIds());
        assertEquals(2, block.lines().get(0).releasePosition());
    }

    @Test
    void blankLinesKeepRowsButProduceNoSpell() {
        SpellBlock block = SpellBlock.parse("igni vocant\n\naqua vocant", SpellBlockTest::normalize);

        assertEquals(2, block.lines().size());
        assertEquals(2, block.lines().get(1).row());
        assertEquals(3, block.lines().get(1).releasePosition());
    }

    @Test
    void blankInputIsEmpty() {
        assertTrue(SpellBlock.parse("  \n ", SpellBlockTest::normalize).isEmpty());
        assertTrue(SpellBlock.parse(null, SpellBlockTest::normalize).isEmpty());
    }
}
