package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarrierTest {

    @Test
    void theUmuBuyArea() {
        assertEquals(3.0D, Barrier.radius(10.0D), 1.0E-9);
        assertEquals(6.0D, Barrier.radius(40.0D), 1.0E-9, "four times the UMU, twice the radius");
        assertEquals(9.4868D, Barrier.radius(100.0D), 1.0E-3);
        assertEquals(Barrier.MAX_RADIUS, Barrier.radius(1.0E6D), 1.0E-9);
        assertEquals(0.0D, Barrier.radius(0.0D), 1.0E-9);
    }

    @Test
    void theZoneStandsOnTheGroundThreeBlocksTall() {
        // Centred on feet at y = 64.0: blocks 64, 65 and 66 are inside, the floor at 63 is not.
        assertTrue(Barrier.inside(0.5D, 64.0D, 0.5D, 0, 64, 0, 3.0D));
        assertTrue(Barrier.inside(0.5D, 64.0D, 0.5D, 0, 66, 0, 3.0D));
        assertFalse(Barrier.inside(0.5D, 64.0D, 0.5D, 0, 63, 0, 3.0D), "the ground is never moved");
        assertFalse(Barrier.inside(0.5D, 64.0D, 0.5D, 0, 67, 0, 3.0D));
    }

    @Test
    void theZoneIsRound() {
        assertTrue(Barrier.inside(0.5D, 64.0D, 0.5D, 3, 64, 0, 3.0D));
        assertFalse(Barrier.inside(0.5D, 64.0D, 0.5D, 3, 64, 3, 3.0D), "the corner of the square is outside the circle");
    }

    @Test
    void whatIsInsideGoesJustPastTheEdge() {
        double[] edge = Barrier.edge(0.0D, 0.0D, 2.0D, 0.0D, 3.0D);
        assertEquals(4.0D, edge[0], 1.0E-9);
        assertEquals(0.0D, edge[1], 1.0E-9);
        double[] centre = Barrier.edge(0.0D, 0.0D, 0.0D, 0.0D, 3.0D);
        assertEquals(4.0D, Math.hypot(centre[0], centre[1]), 1.0E-9);
    }
}
