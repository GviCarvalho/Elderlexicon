package com.elderlexicon.mod.spell.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SightBondTest {

    @Test
    void aBondLastsTwoSecondsUnlessChronosSaysOtherwise() {
        assertEquals(40, SightBond.ticks(SightBond.seconds(null)));
        assertEquals(600, SightBond.ticks(SightBond.seconds(30.0D)));
    }

    @Test
    void seeingThroughOtherEyesIsAnOpenTap() {
        assertEquals(1.0D, SightBond.cost(2.0D), 1.0E-9);
        assertEquals(15.0D, SightBond.cost(30.0D), 1.0E-9);
    }
}
