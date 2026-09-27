package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargeTest {

    @Test
    void theMoreIsGatheredTheLongerItTakes() {
        assertEquals(0, Charge.ticks(0.0D));
        assertEquals(26, Charge.ticks(20.0D));
        assertEquals(45, Charge.ticks(44.0D), "the ring of fire gathers in a little over two seconds");
        assertEquals(Charge.MAX_TICKS, Charge.ticks(500.0D), "never more than five seconds");
        assertEquals(Charge.MIN_TICKS, Charge.ticks(0.5D));
    }
}
