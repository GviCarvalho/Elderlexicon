package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeatTest {

    @Test
    void releasedAtOnceItIsAsHotAsAllOfIt() {
        assertEquals(44.0D, Heat.of(44.0D, 44, true), 1.0E-9);
    }

    @Test
    void spreadOverItsFlamesItKeepsTheirHeat() {
        assertEquals(1.0D, Heat.of(44.0D, 44, false), 1.0E-9, "a ring of common fire is common fire");
        assertEquals(10.0D, Heat.of(30.0D, 3, false), 1.0E-9, "a lava pool's fire stays hot");
        assertEquals(Heat.COMMON, Heat.of(0.0D, 0, true), 1.0E-9);
    }

    @Test
    void theBandsGoFromCommonFireToPlasma() {
        assertEquals(Heat.Band.COMMON, Heat.band(1.0D));
        assertEquals(Heat.Band.WHITE, Heat.band(3.0D));
        assertEquals(Heat.Band.MELTING, Heat.band(10.0D));
        assertEquals(Heat.Band.PLASMA, Heat.band(44.0D));
    }

    @Test
    void condensingCostsNothingUntilItConcentrates() {
        assertEquals(0.0D, Heat.work(44.0D, 1.0D), 1.0E-9);
        assertEquals(0.1D * 44.0D * (1.0D - 1.0D / 44.0D), Heat.work(44.0D, 44.0D), 1.0E-9);
        assertTrue(Heat.work(44.0D, 44.0D) < 0.1D * 44.0D);
    }

    @Test
    void itCoolsTowardCommonFire() {
        assertEquals(44.0D, Heat.cooled(44.0D, 0.0D), 1.0E-9);
        double later = Heat.cooled(44.0D, Heat.COOLING_TICKS);
        assertEquals(1.0D + 43.0D / Math.E, later, 1.0E-9);
        assertTrue(Heat.cooled(44.0D, 1000.0D) < Heat.SPENT, "long after, it is common fire again");
    }

    @Test
    void theHotterTheFartherItReaches() {
        assertEquals(1.0D, Heat.reach(1.0D), 1.0E-9);
        assertTrue(Heat.reach(44.0D) > Heat.reach(10.0D));
        assertTrue(Heat.reach(1000.0D) > 5.0D, "no ceiling");
    }
}
