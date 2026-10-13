package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The body as a chalice (docs/vita-design.md): the life follows the element most lacking. */
class ChaliceTest {

    private static final double E = 1.0E-6D;

    @Test
    void aWholeBodyHoldsItsCapacity() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        assertEquals(100.0D, body.capacity(), E);
        assertEquals(5.0D, body.full(VitaElement.FIRMO), E);
        assertEquals(1.0D, body.wholeness(), E);
    }

    @Test
    void aFallTakesTheLifeFromTheBones() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        body.dropTo(VitaElement.FIRMO, 16.0D / 20.0D);
        assertEquals(4.0D, body.get(VitaElement.FIRMO), E, "a fifth of the bones");
        assertEquals(55.0D, body.get(VitaElement.AQUA), E, "the blood is whole");
        assertEquals(0.8D, body.wholeness(), E, "the life follows the bones");
    }

    @Test
    void healingFillsWhatLacksFirst() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        body.dropTo(VitaElement.FIRMO, 0.8D);
        body.healTo(0.9D);
        assertEquals(4.5D, body.get(VitaElement.FIRMO), E);
        assertEquals(55.0D, body.get(VitaElement.AQUA), E, "nothing beyond its full");
        assertEquals(0.9D, body.wholeness(), E);
    }

    @Test
    void anElementGivenToAHurtBodyHealsWhenItWasTheOneLacking() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        body.dropTo(VitaElement.FIRMO, 0.8D);
        body.addElementEnergy(VitaElement.FIRMO, 1.0D);
        assertEquals(1.0D, body.wholeness(), E, "the bones whole again: the life too");
    }

    @Test
    void whatComesIntoAFullChalicePushesTheOthersOut() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        body.addElementEnergy(VitaElement.FIRMO, 5.0D);
        assertEquals(100.0D, body.totalUmu(), E, "the chalice holds no more");
        assertEquals(10.0D, body.get(VitaElement.FIRMO), E);
        assertTrue(body.get(VitaElement.AQUA) < 55.0D, "the blood gave way");
        assertTrue(body.wholeness() < 1.0D, "and the life with it");
    }

    @Test
    void theBodyExpelsItsExcessButFillsNothing() {
        VitaData body = VitaData.balanced(16.0F, 20.0F);
        body.addElementEnergy(VitaElement.IGNI, 2.0D);
        double before = body.get(VitaElement.IGNI);
        assertTrue(body.expelExcess(0.1D));
        assertTrue(body.get(VitaElement.IGNI) < before);
        assertEquals(0.8D * 55.0D, body.get(VitaElement.AQUA), E, "what lacks stays lacking");
    }

    @Test
    void aHurtWithNoNatureTakesFromAll() {
        VitaData body = VitaData.balanced(20.0F, 20.0F);
        body.loseShare(0.25D);
        assertEquals(0.75D, body.wholeness(), E);
        assertEquals(0.75D * 38.0D, body.get(VitaElement.AURA), E);
    }
}
