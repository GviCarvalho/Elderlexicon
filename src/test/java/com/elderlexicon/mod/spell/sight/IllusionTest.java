package com.elderlexicon.mod.spell.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IllusionTest {

    @Test
    void anImageLastsTwoSecondsUnlessChronosSaysOtherwise() {
        assertEquals(40, Illusion.ticks(null));
        assertEquals(600, Illusion.ticks(30.0D));
    }

    @Test
    void anImageCostsATenthOfTheMatterEveryTwoSeconds() {
        assertEquals(1.0D, Illusion.cost(10.0D, 2.0D), 1.0E-9);
        assertEquals(15.0D, Illusion.cost(10.0D, 30.0D), 1.0E-9, "held longer, it spends more");
        assertEquals(0.0D, Illusion.cost(-5.0D, 2.0D), 1.0E-9);
    }
}
