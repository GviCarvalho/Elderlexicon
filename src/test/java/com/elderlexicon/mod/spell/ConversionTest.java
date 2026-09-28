package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversionTest {

    @Test
    void theLadderCountsTheRungsBetweenStates() {
        assertEquals(1, Conversion.steps(VitaElement.FIRMO, VitaElement.AQUA), "solid to liquid");
        assertEquals(1, Conversion.steps(VitaElement.AQUA, VitaElement.AURA), "liquid to gas");
        assertEquals(1, Conversion.steps(VitaElement.AURA, VitaElement.IGNI), "gas to plasma");
        assertEquals(2, Conversion.steps(VitaElement.IGNI, VitaElement.AQUA));
        assertEquals(2, Conversion.steps(VitaElement.FIRMO, VitaElement.AURA));
        assertEquals(3, Conversion.steps(VitaElement.FIRMO, VitaElement.IGNI), "the two ends of the ladder");
        assertEquals(0, Conversion.steps(VitaElement.IGNI, VitaElement.IGNI));
    }

    @Test
    void theFartherOnTheLadderTheDearerAndSlower() {
        assertEquals(6.0D, Conversion.work(40.0D, VitaElement.FIRMO, VitaElement.IGNI), 1.0E-9);
        assertEquals(4.0D, Conversion.work(40.0D, VitaElement.IGNI, VitaElement.AQUA), 1.0E-9);
        assertEquals(2.0D, Conversion.work(40.0D, VitaElement.AQUA, VitaElement.FIRMO), 1.0E-9);
        assertEquals(78, Conversion.ticks(40.0D, VitaElement.FIRMO, VitaElement.IGNI));
        assertEquals(52, Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.AQUA));
        assertEquals(0, Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.IGNI));
    }

    @Test
    void visBecomesAnythingFreelyButGoingBackCostsTheWholeLadder() {
        assertEquals(0, Conversion.steps(VitaElement.BALANCED, VitaElement.IGNI));
        assertEquals(3, Conversion.steps(VitaElement.AQUA, VitaElement.BALANCED));
        assertEquals(Conversion.MIN_TICKS, Conversion.ticks(1000.0D, VitaElement.BALANCED, VitaElement.IGNI),
                "it takes only the shortest time");
    }

    @Test
    void goingRungByRungCostsTheSameAsJumping() {
        // fire to water directly crosses two rungs; by way of air, one and then one: the same work, the same time.
        assertEquals(Conversion.work(40.0D, VitaElement.IGNI, VitaElement.AQUA), Conversion.workFor(40.0D, 1 + 1), 1.0E-9);
        assertEquals(Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.AQUA), Conversion.ticksFor(40.0D, 2, true));
    }

    @Test
    void aChainTakesEachOfItsSteps() {
        java.util.List<VitaElement> chain = java.util.List.of(VitaElement.BALANCED, VitaElement.IGNI, VitaElement.AURA,
                VitaElement.AQUA, VitaElement.FIRMO);
        assertEquals(3, Conversion.chainSteps(chain), "vis to fire is free; the other three descend one rung each");
        assertEquals(Conversion.MIN_TICKS + 3 * Conversion.stepTicks(40.0D, VitaElement.IGNI, VitaElement.AURA),
                Conversion.chainTicks(40.0D, chain));
    }
}
