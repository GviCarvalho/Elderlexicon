package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.magic.lexicon.Lexicon;
import com.elderlexicon.mod.magic.lexicon.Lexicons;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/** The conditions of a line: "and" side by side, aut, non, and chronos as "while" (docs/fluxo-design.md). */
class LineConditionsTest {

    private static final Lexicon LEXICON = Lexicons.builtIn();

    private static List<String> words(String line) {
        return Arrays.asList(line.split(" "));
    }

    private static boolean holds(String line, String... now) {
        Predicate<String> happening = Set.of(now)::contains;
        return LineConditions.holds(LEXICON, words(line), happening);
    }

    @Test
    void aLineWithNoConditionAlwaysHolds() {
        assertTrue(holds("igni iactare"));
    }

    @Test
    void conditionsSideBySideHoldTogether() {
        assertTrue(holds("ferit latet igni iactare", "attack", "sneak"));
        assertFalse(holds("ferit latet igni iactare", "attack"));
    }

    @Test
    void autSplitsTheConditionsIntoGroups() {
        String line = "ferit latet aut patitur igni iactare";
        assertTrue(holds(line, "attack", "sneak"));
        assertTrue(holds(line, "hurt"));
        assertFalse(holds(line, "attack"), "struck, but not sneaking, and not hurt");
    }

    @Test
    void nonTurnsTheNextConditionAround() {
        assertTrue(holds("non latet igni iactare"));
        assertFalse(holds("non latet igni iactare", "sneak"));
        assertTrue(holds("ferit non latet igni iactare", "attack"));
        assertFalse(holds("ferit non latet igni iactare", "attack", "sneak"));
        assertTrue(holds("non non latet igni iactare", "sneak"), "twice turned is as written");
    }

    @Test
    void aLineWakesOnlyForWhatItAsksToHappen() {
        assertTrue(LineConditions.wakesFor(LEXICON, words("ferit aut patitur igni iactare"), "hurt"));
        assertFalse(LineConditions.wakesFor(LEXICON, words("ferit non latet igni iactare"), "sneak"),
                "a condition turned around only filters");
        assertFalse(LineConditions.wakesFor(LEXICON, words("igni iactare"), "attack"));
    }

    /** A scene with the body holding {@code firmo} of earth, and the ground {@code below} blocks under a fall. */
    private static LineConditions.Scene body(double firmo, double below) {
        return new LineConditions.Scene() {
            @Override
            public boolean recent(String trigger) {
                return false;
            }

            @Override
            public double amount(String source) {
                return "firmo".equals(source) ? firmo : 0.0D;
            }

            @Override
            public double groundBelow() {
                return below;
            }
        };
    }

    @Test
    void aMeasureIsWhatTheBodyHoldsExactly() {
        List<String> lacking = words("non quantum 5 firmo chronos vis quantum 1 vertere firmo");
        assertTrue(LineConditions.holds(LEXICON, lacking, body(3.2D, -1.0D)), "3 is not exactly 5");
        assertFalse(LineConditions.holds(LEXICON, lacking, body(4.6D, -1.0D)), "4.6 is 5 in whole UMU");
        assertTrue(LineConditions.holds(LEXICON, lacking, body(7.0D, -1.0D)), "7 is not exactly 5 either");
        assertEquals(Set.of(0, 1, 2, 3), LineConditions.positions(LEXICON, lacking));
        assertEquals(4, LineConditions.whileAt(LEXICON, lacking), "the chronos takes its time from the measure");
        assertEquals(words("non chronos 1 vis quantum 1 vertere firmo"), LineConditions.window(LEXICON, lacking));
        assertTrue(LineConditions.wakesFor(LEXICON, lacking, LineConditions.MEASURE), "a denied measure wakes too");
        List<String> plenty = words("quantum 6 firmo chronos firmo quantum 1 vertere vis");
        assertTrue(LineConditions.holds(LEXICON, plenty, body(6.2D, -1.0D)));
        assertFalse(LineConditions.holds(LEXICON, plenty, body(5.0D, -1.0D)));
    }

    @Test
    void aQuantityBeforeASourceIsAMeasureOnlyBesideTheConditions() {
        assertEquals(Set.of(0), LineConditions.positions(LEXICON, words("ferit quantum 20 igni iactare")),
                "after a happening it is still how much fire is thrown");
        assertTrue(LineConditions.positions(LEXICON, words("quantum 2 firmo surgit")).isEmpty(), "surgit seeks");
        assertEquals(Set.of(0, 1, 2, 3), LineConditions.positions(LEXICON, words("quantum 5 firmo ferit igni iactare")));
    }

    @Test
    void aPlaceBeforeCaditIsTheGroundNearInAFall() {
        List<String> boot = words("3 ubis cadit aura ille ubis impediunt");
        assertEquals(Set.of(0, 1, 2), LineConditions.positions(LEXICON, boot));
        assertTrue(LineConditions.holds(LEXICON, boot, body(0.0D, 2.5D)));
        assertFalse(LineConditions.holds(LEXICON, boot, body(0.0D, 5.0D)), "the ground still too far");
        assertFalse(LineConditions.holds(LEXICON, boot, body(0.0D, -1.0D)), "not falling");
        assertEquals(Set.of(LineConditions.FALLING), LineConditions.triggers(LEXICON, boot));
        assertTrue(LineConditions.wakesFor(LEXICON, boot, LineConditions.FALLING));
        assertEquals(Set.of("land"), LineConditions.triggers(LEXICON, words("cadit aura iactare")), "a landing");
    }

    @Test
    void aBareChronosAfterAConditionLastsWhileItHolds() {
        List<String> line = words("latet chronos aura impediunt");
        assertEquals(1, LineConditions.whileAt(LEXICON, line));
        assertEquals(words("latet chronos 1 aura impediunt"), LineConditions.window(LEXICON, line));
        assertEquals(-1, LineConditions.whileAt(LEXICON, words("latet chronos 5 aura impediunt")),
                "a chronos with its number is only a duration");
        assertEquals(-1, LineConditions.whileAt(LEXICON, words("chronos aura impediunt")), "no condition before it");
    }
}
