package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VitaFirmoTierTest {

    @Test
    void hysteresisRespectsSmallBaseline() {
        VitaImbalanceTier tier = VitaSystem.evaluateFirmoTier(2.5D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier);

        tier = VitaSystem.evaluateFirmoTier(3.2D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_LOW, tier);

        tier = VitaSystem.evaluateFirmoTier(4.8D, tier);
        assertEquals(VitaImbalanceTier.BALANCED, tier);
    }

    @Test
    void severeHighRequiresCrossingThreshold() {
        VitaImbalanceTier tier = VitaSystem.evaluateFirmoTier(7.05D, VitaImbalanceTier.SLIGHTLY_HIGH);
        assertEquals(VitaImbalanceTier.SLIGHTLY_HIGH, tier);

        tier = VitaSystem.evaluateFirmoTier(7.3D, tier);
        assertEquals(VitaImbalanceTier.SEVERELY_HIGH, tier);
    }
}
