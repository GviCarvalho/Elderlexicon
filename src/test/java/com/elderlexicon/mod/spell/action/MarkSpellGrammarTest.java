package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.parser.Parser;
import com.elderlexicon.mod.parser.ParserDictionary;
import com.elderlexicon.mod.spell.mark.SpellPlace;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/** Marks, numbers and ubis in spells ({@code docs/marcas-como-runas-design.md}). */
class MarkSpellGrammarTest {

    private static SpellActionEngine engine;

    @BeforeAll
    static void setupEngine() {
        engine = new SpellActionEngine(ParserDictionary.load());
    }

    private static SpellActionResult parse(String spell) {
        return engine.generateActions(Arrays.asList(spell.split(" ")));
    }

    private static SpellAction onlyFunction(SpellActionResult result) {
        List<SpellAction> functions = result.actions().stream()
                .filter(action -> action.type() == SpellActionType.FUNCTION)
                .toList();
        assertEquals(1, functions.size(), "Expected one function in " + result.actions());
        return functions.get(0);
    }

    @Test
    void aMarkBeforeAFunctionIsItsSubject() {
        SpellActionResult result = parse("m1 vocant");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vocant = onlyFunction(result);
        assertEquals("vocant", vocant.runeId());
        assertEquals(Optional.of("m1"), vocant.subjectMark());
        assertTrue(vocant.place().isEmpty());
    }

    @Test
    void ubisTakesAMarkBeforeItAsThePlace() {
        SpellActionResult result = parse("m1 ubis igni vocant");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vocant = onlyFunction(result);
        assertTrue(vocant.subjectMark().isEmpty(), "the mark was spent as a place");
        assertEquals(Optional.of(SpellPlace.mark("m1")), vocant.place());
        assertEquals("igni", vocant.metadata().get("elementRuneId"));
    }

    @Test
    void theSourceMayComeBeforeTheMarkPlace() {
        SpellAction written = onlyFunction(parse("igni m1 ubis vocant"));
        assertEquals(Optional.of(SpellPlace.mark("m1")), written.place());
        assertEquals("igni", written.metadata().get("elementRuneId"));
    }

    @Test
    void oneNumberIsADistanceAndTheSourceStaysTheSubject() {
        SpellActionResult result = parse("igni 10 ubis vocant");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vocant = onlyFunction(result);
        assertEquals(Optional.of(SpellPlace.distance(10)), vocant.place());
        assertEquals("igni", vocant.metadata().get("elementRuneId"));
    }

    @Test
    void threeNumbersAreCoordinates() {
        SpellAction vocant = onlyFunction(parse("10 64 -30 ubis m1 vocant"));
        assertEquals(Optional.of(SpellPlace.coordinates(10, 64, -30)), vocant.place());
        assertEquals(Optional.of("m1"), vocant.subjectMark());
    }

    @Test
    void teleportHome() {
        SpellAction vocant = onlyFunction(parse("casa ubis m1 vocant"));
        assertEquals(Optional.of(SpellPlace.mark("casa")), vocant.place());
        assertEquals(Optional.of("m1"), vocant.subjectMark());
    }

    @Test
    void ubisAfterTheFunctionIsInvalid() {
        SpellActionResult result = parse("igni vocant ubis m1");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("'ubis' requer um lugar")));
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("Marca 'm1' sem função")));
    }

    @Test
    void ubisAlsoTakesNumbersWrittenAfterIt() {
        SpellActionResult result = parse("igni quantum 20 ubis 200 iactare");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction iactare = onlyFunction(result);
        assertEquals(20.0D, iactare.quantity().orElseThrow());
        assertEquals(Optional.of(SpellPlace.distance(200)), iactare.place());
        assertEquals(Optional.of(SpellPlace.coordinates(1, 2, 3)), onlyFunction(parse("ubis 1 2 3 eu vocant")).place());
    }

    @Test
    void ubisTakesOneTwoOrThreeValues() {
        SpellActionResult result = parse("igni 1 2 3 4 ubis vocant");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("um, dois ou três valores")));
    }

    @Test
    void twoNumbersAreDistanceAndHeight() {
        SpellActionResult result = parse("20 5 ubis eu vocant");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vocant = onlyFunction(result);
        assertEquals(Optional.of(SpellPlace.distanceAndHeight(20, 5)), vocant.place());
        assertEquals(Optional.of("eu"), vocant.subjectMark());
    }

    @Test
    void anAxisMayBeAMark() {
        SpellActionResult result = parse("10 m1 -30 ubis igni vocant");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellPlace place = onlyFunction(result).place().orElseThrow();
        assertEquals(SpellPlace.coordinates(SpellPlace.Axis.of(10), SpellPlace.Axis.mark("m1"), SpellPlace.Axis.of(-30)), place);
        assertTrue(place.followsMarks());
        assertEquals(Optional.of(SpellPlace.coordinates(SpellPlace.Axis.mark("m1"), SpellPlace.Axis.of(64),
                SpellPlace.Axis.mark("m2"))), onlyFunction(parse("m1 64 m2 ubis aqua vocant")).place());
    }

    @Test
    void everyMarkBeforeUbisIsPartOfThePlace() {
        SpellActionResult result = parse("m1 10 ubis vocant");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("dois números: distância e altura")));
        assertTrue(onlyFunction(result).subjectMark().isEmpty(), "m1 went to the place, not to the subject");
    }

    @Test
    void aMarkBeforeANumberAndAFunctionIsTheSubject() {
        SpellAction iactare = onlyFunction(parse("eu 200 quantum iactare"));
        assertEquals(Optional.of("eu"), iactare.subjectMark());
    }

    @Test
    void transvocatioSwapsTwoMarks() {
        SpellActionResult result = parse("m1 transvocatio m2");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction swap = onlyFunction(result);
        assertEquals(Optional.of("m1"), swap.subjectMark());
        assertEquals(Optional.of("m2"), swap.targetMark());
    }

    @Test
    void transvocatioWithoutTargetSwapsWithTheCaster() {
        SpellActionResult result = parse("m1 transvocatio");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction swap = onlyFunction(result);
        assertEquals(Optional.of("m1"), swap.subjectMark());
        assertTrue(swap.targetMark().isEmpty());
    }

    @Test
    void transvocatioWithoutSubjectSwapsTheCasterWithTheTarget() {
        SpellAction swap = onlyFunction(parse("transvocatio m2"));
        assertTrue(swap.subjectMark().isEmpty());
        assertEquals(Optional.of("m2"), swap.targetMark());
    }

    @Test
    void transvocatioRefusesASource() {
        SpellActionResult result = parse("m1 transvocatio igni");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("troca coisas marcadas")));
    }

    @Test
    void vertereOnAMarkConvertsItsMatterNotTheCastersVita() {
        SpellActionResult result = parse("m1 vertere aqua");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vertere = onlyFunction(result);
        assertEquals(Optional.of("m1"), vertere.subjectMark());
        assertEquals("aqua", vertere.targetRuneId());
        assertTrue(result.vertereRequests().isEmpty(), "the caster's Vita is not converted");
    }

    @Test
    void vertereFromMarkToMarkRenames() {
        SpellActionResult result = parse("m1 vertere m2");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vertere = onlyFunction(result);
        assertEquals(Optional.of("m1"), vertere.subjectMark());
        assertEquals(Optional.of("m2"), vertere.targetMark());
    }

    @Test
    void aSourceCannotBecomeAMark() {
        SpellActionResult result = parse("igni vertere m1");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("fonte alvo imediatamente após")));
        assertTrue(result.vertereRequests().isEmpty());
    }

    @Test
    void aMarkFollowedByASourceIsUnused() {
        SpellActionResult result = parse("m1 igni vocant");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("Marca 'm1' sem uso")));
        assertTrue(onlyFunction(result).subjectMark().isEmpty());
    }

    @Test
    void ligabisKeepsReadingItsOwnMarksSilently() {
        SpellActionResult result = parse("firmo m1 ligabis m2");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction ligabis = onlyFunction(result);
        assertTrue(ligabis.subjectMark().isEmpty());
        assertEquals(List.of("firmo", "m1", "ligabis", "m2"), result.lexemes());
    }

    @Test
    void theNameAfterReframeIsNotAMark() {
        SpellActionResult result = parse("igni iactare reframe fireball");
        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
    }

    @Test
    void leftoverNumbersAreReported() {
        SpellActionResult result = parse("igni 20 iactare");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("Número sem uso: 20")));
    }

    @Test
    void quantumTakesTheNumberRightAfterIt() {
        SpellActionResult result = parse("igni quantum 20 iactare");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        assertEquals(20.0D, onlyFunction(result).quantity().orElseThrow());
    }

    @Test
    void quantumWorksWithAMarkedSubject() {
        SpellActionResult result = parse("eu quantum 500 iactare");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction iactare = onlyFunction(result);
        assertEquals(Optional.of("eu"), iactare.subjectMark());
        assertEquals(500.0D, iactare.quantity().orElseThrow());
    }

    @Test
    void quantumAlsoTakesANumberWrittenBeforeIt() {
        SpellActionResult result = parse("eu 200 quantum iactare");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction iactare = onlyFunction(result);
        assertEquals(Optional.of("eu"), iactare.subjectMark());
        assertEquals(200.0D, iactare.quantity().orElseThrow());
        assertTrue(iactare.place().isEmpty());
    }

    @Test
    void quantumAndUbisTakeTheirOwnNumbers() {
        SpellAction iactare = onlyFunction(parse("igni quantum 20 10 ubis iactare"));
        assertEquals(20.0D, iactare.quantity().orElseThrow());
        assertEquals(Optional.of(SpellPlace.distance(10)), iactare.place());
    }

    @Test
    void partialVertere() {
        SpellActionResult result = parse("m1 quantum 20 vertere aqua");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction vertere = onlyFunction(result);
        assertEquals(Optional.of("m1"), vertere.subjectMark());
        assertEquals(20.0D, vertere.quantity().orElseThrow());
    }

    @Test
    void quantumWithoutANumberIsReported() {
        SpellActionResult result = parse("igni quantum iactare");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("'quantum' requer um número")));
        assertTrue(onlyFunction(result).quantity().isEmpty());
    }

    @Test
    void chronosTakesTheSecondsOfTheEvocation() {
        SpellActionResult result = parse("igni chronos 5 iactare");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        assertEquals(5.0D, onlyFunction(result).seconds().orElseThrow());
    }

    @Test
    void chronosZeroIsInstant() {
        assertEquals(0.0D, onlyFunction(parse("igni chronos 0 iactare")).seconds().orElseThrow());
    }

    @Test
    void negativeChronosIsForAnotherVolume() {
        SpellActionResult result = parse("igni chronos -3 iactare");
        assertTrue(result.issues().stream().anyMatch(issue -> issue.contains("outro volume")));
        assertTrue(onlyFunction(result).seconds().isEmpty());
    }

    @Test
    void quantumAndChronosTogether() {
        SpellAction iactare = onlyFunction(parse("igni quantum 200 chronos 5 iactare"));
        assertEquals(200.0D, iactare.quantity().orElseThrow());
        assertEquals(5.0D, iactare.seconds().orElseThrow());
    }

    @Test
    void surgitWithAMarkReadsThoseScrolls() {
        SpellActionResult result = parse("r2 chronos 3 surgit");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction surgit = onlyFunction(result);
        assertEquals("surgit", surgit.runeId());
        assertEquals(Optional.of("r2"), surgit.subjectMark());
        assertEquals(3.0D, surgit.seconds().orElseThrow());
    }

    @Test
    void surgitAlsoTakesTheMarkAfterIt() {
        SpellActionResult result = parse("chronos 10 surgit r2");

        assertFalse(result.hasIssues(), "Unexpected issues: " + result.issues());
        SpellAction surgit = onlyFunction(result);
        assertEquals(Optional.of("r2"), surgit.subjectMark());
        assertEquals(10.0D, surgit.seconds().orElseThrow());
    }

    @Test
    void transcriptNamesTheMarks() {
        Parser parser = new Parser();
        SpellActionResult result = parse("casa ubis m1 vocant");
        String message = parser.transcribeActions(result.actions(), result.lexemes(), result.primarySource()).message();
        assertEquals("Summon 'm1' at 'casa'", message);

        SpellActionResult swap = parse("m1 transvocatio m2");
        assertEquals("Swap 'm1' with 'm2'",
                parser.transcribeActions(swap.actions(), swap.lexemes(), swap.primarySource()).message());
    }
}
