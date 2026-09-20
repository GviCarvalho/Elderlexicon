package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VitaAquaTierTest {

    @Test
    void severeLowRequiresHysteresisToRecover() {
        VitaImbalanceTier tier = VitaSystem.evaluateAquaTier(20.0D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier);

        tier = VitaSystem.evaluateAquaTier(33.5D, tier);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier, "Should stay severe low until boundary + hysteresis");

        tier = VitaSystem.evaluateAquaTier(34.5D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_LOW, tier);
    }

    @Test
    void balancedRangeCoversExpectedWindow() {
        VitaImbalanceTier tier = VitaSystem.evaluateAquaTier(55.0D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.BALANCED, tier);

        tier = VitaSystem.evaluateAquaTier(60.2D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.BALANCED, tier);

        tier = VitaSystem.evaluateAquaTier(61.6D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_HIGH, tier);
    }

    @Test
    void severeHighDemandsCrossingThreshold() {
        VitaImbalanceTier tier = VitaSystem.evaluateAquaTier(76.5D, VitaImbalanceTier.SLIGHTLY_HIGH);
        assertEquals(VitaImbalanceTier.SLIGHTLY_HIGH, tier);

        tier = VitaSystem.evaluateAquaTier(78.5D, tier);
        assertEquals(VitaImbalanceTier.SEVERELY_HIGH, tier);
    }
}
