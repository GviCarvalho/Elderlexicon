package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SpellFlowTest {

    @Test
    void aLongerSpellSpendsMoreAtTheSameFlow() {
        assertEquals(10.0D, SpellFlow.total(10.0D, 40), 1.0E-9, "the default two seconds");
        assertEquals(25.0D, SpellFlow.total(10.0D, 100), 1.0E-9, "five seconds: two and a half windows");
        assertEquals(150.0D, SpellFlow.total(10.0D, 600), 1.0E-9, "thirty seconds");
    }

    @Test
    void aShorterWindowIsABurstOfTheSameEnergy() {
        assertEquals(10.0D, SpellFlow.total(10.0D, 0), 1.0E-9);
        assertEquals(10.0D, SpellFlow.total(10.0D, 20), 1.0E-9);
    }
}
