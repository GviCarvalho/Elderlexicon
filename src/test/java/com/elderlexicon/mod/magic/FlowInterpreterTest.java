package com.elderlexicon.mod.magic;

import com.elderlexicon.mod.magic.flow.FlowInterpreter;
import com.elderlexicon.mod.magic.grammar.SpellGrammar;
import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import com.elderlexicon.mod.spell.Conversion;
import com.elderlexicon.mod.spell.action.SpellAction;
import com.elderlexicon.mod.spell.action.SpellActionResult;
import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The flow of energy through a spell, from where it comes to what spends it (docs/exsugat-vertere-design.md). */
class FlowInterpreterTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();
    private static final SpellGrammar GRAMMAR = new SpellGrammar(LEXICON);

    private record Run(FakeFlow.Ledger ledger, FakeFlow.World world, SpellActionResult read) {
    }

    private static Run cast(String spell) {
        return cast(spell, false, 30.0D);
    }

    private static Run cast(String spell, boolean focus, double bodyOfEach) {
        SpellActionResult read = GRAMMAR.read(Arrays.asList(spell.split(" ")));
        assertTrue(read.issues().isEmpty(), spell + ": " + read.issues());
        VitaElement primary = read.primarySource().map(p -> LEXICON.elementOf(p.definition().id()))
                .orElse(VitaElement.BALANCED);
        FakeFlow.Ledger ledger = new FakeFlow.Ledger(primary, 3.0D, read.vertereRequests());
        ledger.primarySource = read.primarySource();
        ledger.focus = focus;
        FakeFlow.World world = new FakeFlow.World(ledger);
        for (VitaElement element : VitaElement.values()) {
            world.body.put(element, bodyOfEach);
        }
        new FlowInterpreter(LEXICON).run(ledger, read.actions(), world);
        return new Run(ledger, world, read);
    }

    @Test
    void aVerbSpendsTheBodysEnergyThroughTheCostOfTheSpell() {
        Run run = cast("igni iactare");
        assertEquals(List.of("iactare"), run.world().performed());
        assertEquals(VitaElement.IGNI, run.world().last("perform").element());
        assertNull(run.world().last("draw"), "nothing is drawn out of the body: the spell only costs it");
    }

    @Test
    void aConversionWithNothingInHandConvertsTheVita() {
        Run run = cast("igni vertere aqua");
        FakeFlow.Call vita = run.world().last("vita");
        assertNotNull(vita);
        assertEquals(VitaElement.AQUA, vita.element());
        assertEquals(1.0D, vita.amount(), 1.0E-9);
        assertEquals(VitaElement.AQUA, run.ledger().primaryElement());
        assertEquals(4.0D, run.ledger().totalCost(), 1.0E-9, "the conversion is paid for");
    }

    @Test
    void aFocusConvertsInsteadOfTheVita() {
        Run run = cast("igni vertere aqua", true, 30.0D);
        assertNull(run.world().last("vita"), "the Vita is left alone with a focus");
        assertEquals(VitaElement.AQUA, run.ledger().primaryElement());
        assertEquals(4.0D, run.ledger().totalCost(), 1.0E-9);
    }

    @Test
    void aQuantityOnTheConversionSaysHowMuch() {
        assertEquals(5.0D, cast("igni quantum 5 vertere aqua").world().last("vita").amount(), 1.0E-9);
    }

    @Test
    void whatIsCapturedIsConvertedNotTheVita() {
        Run run = cast("igni exsugat vertere aqua iactare");
        assertNull(run.world().last("vita"), "the captured fire is converted, not the body's");
        assertEquals(List.of("iactare"), run.world().performed());
        assertEquals(VitaElement.AQUA, run.world().last("perform").element(), "what is thrown is the converted water");
        FakeFlow.Call capture = run.world().last("capture");
        assertNotNull(capture, "the capture pulls what the iactare spent, after it ran");
        assertEquals(VitaElement.AQUA, capture.element());
        assertEquals("iactare", capture.action().runeId());
    }

    @Test
    void whatIsCapturedAndNeverSpentIsConvertedWhereItIs() {
        Run run = cast("firmo exsugat vertere igni");
        FakeFlow.Call converted = run.world().last("convertInPlace");
        assertNotNull(converted);
        assertEquals(VitaElement.IGNI, converted.element());
        assertEquals(FlowInterpreter.DEFAULT_UMU, converted.amount(), 1.0E-9);
        assertTrue(run.world().performed().isEmpty());
    }

    @Test
    void aBareQuantityAfterACaptureTakesAllInReachAndCondensesIt() {
        Run run = cast("igni exsugat quantum chronos 0 iactare");
        FakeFlow.Call all = run.world().last("captureAll");
        assertNotNull(all);
        SpellAction released = run.world().last("perform").action();
        assertTrue(released.intensity() > 1.0D, "fire released at once is as hot as all of it");
        assertTrue(released.charge() > 0, "condensing takes time");
        assertEquals(7, released.orb());
        assertEquals(3.0D, run.ledger().environmental, 1.0E-9, "the captured fire pays what the spell cost");
        assertNull(run.world().last("capture"), "everything was already taken");
    }

    @Test
    void allOfTheBodysManaCanBeCondensed() {
        Run run = cast("vis quantum chronos 0 iactare");
        assertEquals(30.0D, run.world().last("draw").amount(), 1.0E-9);
        SpellAction released = run.world().last("perform").action();
        assertEquals(30.0D, released.intensity(), 1.0E-9);
        assertEquals(9, released.orb());
        assertEquals(VitaElement.BALANCED, run.world().last("perform").element());
    }

    @Test
    void manaDrawnOutAndConvertedIsReleasedAsWhatItBecame() {
        Run run = cast("vis quantum vertere igni chronos 0 iactare");
        assertEquals(VitaElement.IGNI, run.world().last("perform").element());
        // vis into an element changes no quality: nothing is lost to the work.
        assertEquals(30.0D, run.world().last("perform").action().intensity(), 1.0E-9);
        assertNull(run.world().last("vita"), "what is held is converted, not the Vita");
    }

    @Test
    void theWorkOfConvertingIsTakenOutOfTheEnergyInHand() {
        Run run = cast("aqua quantum 20 vertere aura chronos 0 vocant");
        assertEquals(20.0D, run.world().last("draw").amount(), 1.0E-9);
        double worked = 20.0D * (1.0D - Conversion.workShare(1));
        assertEquals(worked, run.world().last("perform").action().intensity(), 1.0E-9);
        assertEquals(VitaElement.AURA, run.world().last("perform").element());
    }

    @Test
    void aMarkedConversionIsTheThingsOwn() {
        Run run = cast("m1 vertere aqua");
        assertNull(run.world().last("vita"));
        assertEquals(List.of("vertere"), run.world().performed());
        assertEquals("m1", run.world().last("perform").action().subjectMark().orElseThrow());
    }

    @Test
    void aViewedConversionOnlyDisguises() {
        Run run = cast("igni surgit vertere aqua");
        assertNotNull(run.world().last("disguise"));
        assertNull(run.world().last("vita"));
        assertTrue(run.world().performed().isEmpty());
    }

    @Test
    void aCaptureWrittenLastIsItsOwnVerb() {
        Run run = cast("igni exsugat");
        assertEquals(List.of("exsugat"), run.world().performed());
        assertNull(run.world().last("capture"));
    }

    @Test
    void theSubjectStaysThroughTheVerbsThatFollow() {
        Run run = cast("m1 vocant iactare");
        assertEquals(List.of("vocant", "iactare"), run.world().performed());
        for (FakeFlow.Call call : run.world().calls) {
            if (call.what().equals("perform")) {
                assertEquals("m1", call.action().subjectMark().orElseThrow(), call.action().toString());
            }
        }
    }

    @Test
    void withNoCasterTheFlowOnlyKeepsAccounts() {
        SpellActionResult read = GRAMMAR.read(List.of("igni", "exsugat", "iactare"));
        FakeFlow.Ledger ledger = new FakeFlow.Ledger(VitaElement.IGNI, 3.0D, read.vertereRequests());
        FakeFlow.World world = new FakeFlow.World(ledger);
        world.caster = false;
        new FlowInterpreter(LEXICON).run(ledger, read.actions(), world);
        assertEquals(List.of("iactare"), world.performed());
        assertNull(world.last("capture"));
    }
}
