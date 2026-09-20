package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VitaAuraTierTest {

    @Test
    void hysteresisPreventsRapidOscillation() {
        VitaImbalanceTier tier = VitaSystem.evaluateAuraTier(18.0D, VitaImbalanceTier.BALANCED);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier);

        tier = VitaSystem.evaluateAuraTier(30.0D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_LOW, tier);

        tier = VitaSystem.evaluateAuraTier(37.0D, tier);
        assertEquals(VitaImbalanceTier.BALANCED, tier);
    }

    @Test
    void severeBoundariesRequireCrossingThreshold() {
        VitaImbalanceTier tier = VitaSystem.evaluateAuraTier(15.0D, VitaImbalanceTier.SEVERELY_LOW);
        assertEquals(VitaImbalanceTier.SEVERELY_LOW, tier);

        tier = VitaSystem.evaluateAuraTier(28.0D, tier);
        assertEquals(VitaImbalanceTier.SLIGHTLY_LOW, tier);

        tier = VitaSystem.evaluateAuraTier(60.0D, VitaImbalanceTier.SLIGHTLY_HIGH);
        assertEquals(VitaImbalanceTier.SEVERELY_HIGH, tier);

        tier = VitaSystem.evaluateAuraTier(45.0D, VitaImbalanceTier.SEVERELY_HIGH);
        assertEquals(VitaImbalanceTier.SLIGHTLY_HIGH, tier);
    }
}
