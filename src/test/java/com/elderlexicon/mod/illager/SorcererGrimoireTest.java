package com.elderlexicon.mod.illager;

import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.spell.block.SpellBlock;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The sorcerer's grimoire is written in the language: every page is a sentence the grammar reads cleanly. */
class SorcererGrimoireTest {

    @Test
    void everyPageIsASentenceTheGrammarReads() {
        SorcererGrimoire grimoire = SorcererGrimoire.get();
        assertFalse(grimoire.pages().isEmpty(), "the grimoire has no pages");
        SpellGrammar grammar = new SpellGrammar(Lexicons.builtIn());
        for (SorcererGrimoire.Page page : grimoire.pages()) {
            assertFalse(page.block().isEmpty(), page.name() + " is empty");
            for (SpellBlock.Line line : page.block().lines()) {
                SpellActionResult read = grammar.read(line.runeIds());
                assertFalse(read.actions().isEmpty(), page.name() + ": " + line.runeIds() + " does nothing");
                assertTrue(read.issues().isEmpty(), page.name() + ": " + line.runeIds() + " is misread: "
                        + read.issues());
            }
        }
    }

    @Test
    void aPageReleasesEachLineAtItsOwnStep() {
        SorcererGrimoire.Page volley = SorcererGrimoire.get().pages().stream()
                .filter(page -> page.name().equals("salva de fogo")).findFirst().orElseThrow();
        SpellBlock block = volley.block();
        assertEquals(java.util.List.of(0, 2, 4), block.lines().stream().map(block::delaySteps).toList());
    }

    @Test
    void aPageForTheWoundedServesOnlyTheWounded() {
        SorcererGrimoire.Page bite = new SorcererGrimoire.Page("mordida", java.util.List.of("aqua quantum -10 vocant"),
                0.0D, 14.0D, 1, true);
        SorcererGrimoire grimoire = new SorcererGrimoire(java.util.List.of(bite));
        RandomSource random = RandomSource.create(1L);
        assertTrue(grimoire.choose(page -> page.serves(5.0D, false), random).isEmpty());
        assertTrue(grimoire.choose(page -> page.serves(5.0D, true), random).isPresent());
        assertTrue(grimoire.choose(page -> page.serves(20.0D, true), random).isEmpty());
    }
}
