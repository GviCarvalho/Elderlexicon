package com.elderlexicon.mod.spell.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectionTest {

    @Test
    void theSpiritIsOutTwoSecondsUnlessChronosSaysOtherwise() {
        assertEquals(40, Projection.ticks(Projection.seconds(null)));
        assertEquals(200, Projection.ticks(Projection.seconds(10.0D)));
    }

    @Test
    void goingFartherAndStayingLongerCostMore() {
        double near = Projection.cost(10.0D, 2.0D);
        assertEquals(0.05D * 26.0D * 2.0D, near, 1.0E-9, "10 blocks away plus the drift it may take, for 2 s");
        assertTrue(Projection.cost(40.0D, 2.0D) > near);
        assertTrue(Projection.cost(10.0D, 20.0D) > near);
        assertEquals(Projection.cost(4.0D, 2.0D), Projection.cost(0.0D, 2.0D), 1.0E-9, "barely leaving pays the minimum");
    }
}
