package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.sight.Revelation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Revelation spells ({@code docs/surgit-visao-design.md}, section 1). */
class SurgitSightGrammarTest {

    private static SpellActionEngine engine;

    @BeforeAll
    static void setupEngine() {
        engine = new SpellActionEngine(ParserDictionary.load());
    }

    private static SpellAction surgit(String spell) {
        SpellActionResult result = engine.generateActions(Arrays.asList(spell.split(" ")));
        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        List<SpellAction> functions = result.actions().stream()
                .filter(action -> action.type() == SpellActionType.FUNCTION)
                .toList();
        assertEquals(1, functions.size(), "Expected one function in " + result.actions());
        assertEquals("surgit", functions.get(0).runeId());
        return functions.get(0);
    }

    private static Revelation.Kind kindOf(SpellAction action) {
        return action.subjectMark().isPresent()
                ? Revelation.Kind.MARK
                : Revelation.Kind.ofSource((String) action.metadata().get("elementRuneId"));
    }

    @Test
    void aSourceBeforeSurgitIsWhatTheSpiritLooksFor() {
        SpellAction action = surgit("igni surgit");

        assertEquals(Revelation.Kind.IGNI, kindOf(action));
        assertTrue(action.subjectMark().isEmpty());
    }

    @Test
    void visSurgitLooksForMagic() {
        assertEquals(Revelation.Kind.VIS, kindOf(surgit("vis surgit")));
    }

    @Test
    void aMarkBeforeSurgitIsRevealedAndRead() {
        SpellAction action = surgit("r2 surgit");

        assertEquals(Revelation.Kind.MARK, kindOf(action));
        assertEquals(Optional.of("r2"), action.subjectMark());
    }

    @Test
    void quantumBeforeSurgitIsItsPotencyAndChronosItsDuration() {
        SpellAction action = surgit("aqua quantum 30 chronos 10 surgit");

        assertEquals(Revelation.Kind.AQUA, kindOf(action));
        assertEquals(30.0D, action.potency().getAsDouble());
        assertTrue(action.sourceValue().isEmpty());
        assertEquals(10.0D, Revelation.seconds(action.seconds().getAsDouble()));
    }

    @Test
    void quantumBeforeTheSourceIsTheValueSought() {
        SpellAction action = surgit("quantum 2 firmo surgit");

        assertEquals(Revelation.Kind.FIRMO, kindOf(action));
        assertEquals(2.0D, action.sourceValue().getAsDouble());
        assertTrue(action.potency().isEmpty());
    }

    @Test
    void bothQuantumsCanBeWritten() {
        SpellAction action = surgit("quantum 3 firmo quantum 20 surgit");

        assertEquals(3.0D, action.sourceValue().getAsDouble());
        assertEquals(20.0D, action.potency().getAsDouble());
    }

    @Test
    void surgitBeforeAMarkWithQuantumSetsHowMuchOfItIsSeen() {
        SpellAction action = surgit("surgit m1 quantum 0");

        assertEquals(Optional.of("m1"), action.subjectMark());
        assertEquals(0.0D, action.visibility().getAsDouble());
        assertTrue(action.seconds().isEmpty());
    }

    @Test
    void visibilityTakesChronosForItsDuration() {
        SpellAction action = surgit("surgit m1 quantum 5 chronos 30");

        assertEquals(5.0D, action.visibility().getAsDouble());
        assertEquals(30.0D, action.seconds().getAsDouble());
    }

    @Test
    void surgitBeforeAMarkAloneStillReadsIt() {
        SpellAction action = surgit("surgit r2");

        assertEquals(Optional.of("r2"), action.subjectMark());
        assertTrue(action.visibility().isEmpty());
    }

    @Test
    void surgitQuantumWithoutAMarkIsForWhatIsAimedAt() {
        SpellAction action = surgit("surgit quantum 3");

        assertTrue(action.subjectMark().isEmpty());
        assertEquals(3.0D, action.visibility().getAsDouble());
    }

    private static SpellAction function(String spell, String runeId) {
        SpellActionResult result = engine.generateActions(Arrays.asList(spell.split(" ")));
        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        List<SpellAction> functions = result.actions().stream()
                .filter(action -> action.type() == SpellActionType.FUNCTION)
                .toList();
        assertEquals(1, functions.size(), "Expected one function in " + result.actions());
        assertEquals(runeId, functions.get(0).runeId());
        return functions.get(0);
    }

    @Test
    void surgitBeforeAFunctionMakesItWorkWithTheImageOnly() {
        SpellAction vocant = function("igni surgit vocant", "vocant");

        assertTrue(vocant.image());
        assertEquals("igni", vocant.metadata().get("elementRuneId"));
    }

    @Test
    void theMarkAndFiltersPassToTheFunctionAfterSurgit() {
        SpellAction vocant = function("m1 chronos 30 surgit vocant", "vocant");

        assertTrue(vocant.image());
        assertEquals(Optional.of("m1"), vocant.subjectMark());
        assertEquals(30.0D, vocant.seconds().getAsDouble());
    }

    @Test
    void vertereOfTheImageKeepsItsTarget() {
        SpellAction vertere = function("igni surgit vertere aqua", "vertere");

        assertTrue(vertere.image());
        assertEquals("aqua", vertere.targetRuneId());
    }

    @Test
    void surgitAtTheEndIsStillARevelation() {
        assertFalse(surgit("igni surgit").image());
    }

    @Test
    void surgitMarkLigabisIsABondOfSight() {
        SpellAction bond = surgit("surgit m1 ligabis");

        assertTrue(bond.sightBond());
        assertEquals(Optional.of("m1"), bond.subjectMark());
        assertTrue(bond.seconds().isEmpty());
    }

    @Test
    void aBondOfSightTakesChronosForHowLong() {
        SpellAction bond = surgit("surgit m1 chronos 30 ligabis");

        assertTrue(bond.sightBond());
        assertEquals(30.0D, bond.seconds().getAsDouble());
    }

    @Test
    void otherBondsAreLeftToLigabis() {
        SpellActionResult result = engine.generateActions(Arrays.asList("vis eu ligabis r1".split(" ")));

        assertTrue(result.actions().stream().noneMatch(SpellAction::sightBond));
    }

    @Test
    void ubisBeforeSurgitSendsTheSpiritThere() {
        SpellAction projection = surgit("10 ubis surgit");

        assertTrue(projection.place().isPresent());
        assertTrue(projection.subjectMark().isEmpty());
        assertEquals(10.0D, projection.place().get().distance());
    }

    @Test
    void aMarkPlaceSendsTheSpiritToTheMark() {
        SpellAction projection = surgit("m1 ubis chronos 20 surgit");

        assertEquals(Optional.of(com.elderlexicon.mod.spell.mark.SpellPlace.mark("m1")), projection.place());
        assertEquals(20.0D, projection.seconds().getAsDouble());
    }
}
