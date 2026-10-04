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
