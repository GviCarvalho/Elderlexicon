package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CaptureTest {

    @Test
    void takesTheNearestUntilEnough() {
        Capture.Taken taken = Capture.take(List.of(1.0D, 1.0D, 1.0D, 1.0D), 3.0D);
        assertEquals(3, taken.sources());
        assertEquals(3.0D, taken.total(), 1.0E-9);
        assertEquals(0.0D, taken.surplus(3.0D), 1.0E-9);
    }

    @Test
    void aSourceIsTakenWholeAndTheRestIsSurplus() {
        Capture.Taken taken = Capture.take(List.of(1.5D, 1.5D, 1.5D), 2.0D);
        assertEquals(2, taken.sources());
        assertEquals(3.0D, taken.total(), 1.0E-9);
        assertEquals(1.0D, taken.surplus(2.0D), 1.0E-9);
    }

    @Test
    void takesWhatThereIsWhenTheWorldHasTooLittle() {
        Capture.Taken taken = Capture.take(List.of(1.0D), 10.0D);
        assertEquals(1, taken.sources());
        assertEquals(1.0D, taken.total(), 1.0E-9);
    }

    @Test
    void nothingIsTakenWhenNothingIsNeeded() {
        assertEquals(0, Capture.take(List.of(3.0D), 0.0D).sources());
    }
}
