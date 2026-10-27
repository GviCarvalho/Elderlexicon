package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** What the arcane compass is written with (docs/bussola-arcana-design.md). */
class DialGrammarTest {

    private static SpellActionEngine engine;

    @BeforeAll
    static void setupEngine() {
        engine = new SpellActionEngine(ParserDictionary.load());
    }

    private static SpellAction only(String spell, String verb) {
        SpellActionResult result = engine.generateActions(Arrays.asList(spell.split(" ")));
        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        List<SpellAction> functions = result.actions().stream()
                .filter(action -> action.type() == SpellActionType.FUNCTION)
                .toList();
        assertEquals(1, functions.size(), "Expected one function in " + result.actions());
        assertEquals(verb, functions.get(0).runeId());
        return functions.get(0);
    }

    @Test
    void aMeasureIsBroughtAsAnImageToThePlaceWrittenNextToTheVerb() {
        SpellAction action = only("igni quantum ego surgit p1 ubis vocant", "vocant");

        assertTrue(action.image(), "the view should leave only the image");
        assertTrue(action.quantityAll(), "a bare quantum is all the source there is");
        assertEquals(Optional.of("ego"), action.subjectMark());
        SpellPlace place = action.place().orElseThrow();
        assertEquals(SpellPlace.Kind.MARK, place.kind());
        assertEquals("p1", place.mark());
    }

    @Test
    void theImageStillWorksWithTheVerbRightAfterTheView() {
        SpellAction action = only("igni surgit vocant", "vocant");

        assertTrue(action.image());
        assertTrue(action.subjectMark().isEmpty());
    }

    @Test
    void aDistanceMayStandBetweenTheViewAndItsVerb() {
        SpellAction action = only("igni surgit 10 ubis vocant", "vocant");

        assertTrue(action.image());
        assertEquals(10.0D, action.place().orElseThrow().distance());
    }

    @Test
    void twoMarksBeforeAPlaceAreWhatIsPushedAndWhere() {
        SpellAction action = only("p1 alvo ubis iactare", "iactare");

        assertEquals(Optional.of("p1"), action.subjectMark());
        assertEquals("alvo", action.place().orElseThrow().mark());
    }

    @Test
    void theVisionBondIsNoImage() {
        SpellActionResult result = engine.generateActions(Arrays.asList("surgit m1 ligabis".split(" ")));
        assertTrue(result.actions().stream().noneMatch(SpellAction::image), "surgit m1 ligabis is a bond of sight");
    }
}
