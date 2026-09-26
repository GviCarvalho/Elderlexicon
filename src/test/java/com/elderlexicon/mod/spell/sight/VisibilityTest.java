package com.elderlexicon.mod.spell.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VisibilityTest {

    @Test
    void levelsAreWholeAndWithinZeroAndTen() {
        assertEquals(0, Visibility.level(0.0D));
        assertEquals(5, Visibility.level(5.4D));
        assertEquals(10, Visibility.level(25.0D));
        assertEquals(0, Visibility.level(-3.0D));
    }

    @Test
    void opacityIsLinear() {
        assertEquals(0.0F, Visibility.opacity(0));
        assertEquals(0.5F, Visibility.opacity(5));
        assertEquals(1.0F, Visibility.opacity(10));
    }

    @Test
    void hidingCostsByMassAndByHowMuchIsHidden() {
        assertEquals(1.0D, Visibility.costPerSecond(20.0D, 0), 1.0E-9, "a player kept unseen");
        assertEquals(0.5D, Visibility.costPerSecond(20.0D, 5), 1.0E-9, "half seen, half the cost");
        assertEquals(0.0D, Visibility.costPerSecond(20.0D, 10), 1.0E-9, "being seen is free");
    }

    @Test
    void itLastsTwoSecondsUnlessChronosSaysOtherwise() {
        assertEquals(2.0D, Visibility.seconds(null));
        assertEquals(30.0D, Visibility.seconds(30.0D));
    }
}
