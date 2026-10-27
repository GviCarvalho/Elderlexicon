package com.elderlexicon.mod.mind;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A doctrine learned in the arena is read back as it was written, and chooses as it learned (3.10). */
class DoctrineTest {

    private static final Doctrine.Situation AT_HAND = new Doctrine.Situation(2.0D, 0.0D, 1.0D, 1.0D, 1.0D, true,
            12.0D);

    private static Doctrine learned() {
        double[] values = new double[Doctrine.Stance.values().length];
        values[Doctrine.Stance.STRIKE.ordinal()] = 0.2D;
        values[Doctrine.Stance.WARD.ordinal()] = 0.6D;
        values[Doctrine.Stance.BACK_OFF.ordinal()] = 0.4D;
        Doctrine.Steps steps = Doctrine.Steps.standard();
        return new Doctrine("zombie", steps, Map.of(steps.key(AT_HAND), values), new JsonObject());
    }

    @Test
    void itChoosesTheBestOfWhatItCanDoNow() {
        Doctrine doctrine = learned();
        assertEquals(Doctrine.Stance.WARD, doctrine.choose(AT_HAND, Doctrine.all()).orElseThrow());
        assertEquals(Doctrine.Stance.BACK_OFF, doctrine.choose(AT_HAND,
                EnumSet.of(Doctrine.Stance.STRIKE, Doctrine.Stance.BACK_OFF)).orElseThrow(), "it cannot ward now");
    }

    @Test
    void whereItNeverWasItSaysNothing() {
        Doctrine.Situation far = new Doctrine.Situation(30.0D, Double.POSITIVE_INFINITY, 1.0D, 1.0D, 1.0D, true,
                12.0D);
        assertTrue(learned().choose(far, Doctrine.all()).isEmpty());
    }

    @Test
    void itIsReadBackAsItWasWritten() {
        Doctrine read = Doctrine.fromJson(new StringReader(learned().toJson()));
        assertEquals("zombie", read.foe());
        assertEquals(Doctrine.Stance.WARD, read.choose(AT_HAND, Doctrine.all()).orElseThrow());
        assertEquals(0.4D, read.worth(AT_HAND)[Doctrine.Stance.BACK_OFF.ordinal()], 1.0E-9D);
    }

    @Test
    void aDoctrineLearnedBeforeRoomWasSeenIsStillRead() {
        String old = learned().toJson().replaceAll(",\\s*\"room\": \\[[^\\]]*\\]", "")
                .replace("\"0.0.2.2.2.1.2\"", "\"0.0.2.2.2.1\"");
        Doctrine read = Doctrine.fromJson(new StringReader(old));
        assertEquals(0, read.steps().room().length);
        assertEquals(Doctrine.Stance.WARD, read.choose(AT_HAND, Doctrine.all()).orElseThrow());
    }

    @Test
    void theStepsSplitWhatItSees() {
        Doctrine.Steps steps = Doctrine.Steps.standard();
        assertEquals("0.0.2.2.2.1.2", steps.key(AT_HAND));
        assertEquals("6.3.2.2.2.0.0", steps.key(new Doctrine.Situation(30.0D, Double.POSITIVE_INFINITY, 1.0D, 1.0D, 1.0D,
                false, 1.0D)));
    }
}
