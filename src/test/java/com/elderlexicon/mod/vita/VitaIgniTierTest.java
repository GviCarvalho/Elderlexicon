package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VitaIgniTierTest {

    @Test
    void tinyBaselineHonorsHysteresis() {
        VitaImbalanceTier tier = VitaSystem.evaluateIgniTier(1.1D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier);

        tier = VitaSystem.evaluateIgniTier(1.3D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_LOW, tier);

        tier = VitaSystem.evaluateIgniTier(2.1D, tier);
        assertEquals(VitaImbalanceTier.BALANCED, tier);
    }

    @Test
    void severeHighNeedsThreshold() {
        VitaImbalanceTier tier = VitaSystem.evaluateIgniTier(2.6D, VitaImbalanceTier.SLIGHTLY_HIGH);
        assertEquals(VitaImbalanceTier.SLIGHTLY_HIGH, tier);

        tier = VitaSystem.evaluateIgniTier(3.0D, tier);
        assertEquals(VitaImbalanceTier.SEVERELY_HIGH, tier);
    }
}
