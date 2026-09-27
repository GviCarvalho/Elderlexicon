package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AirPressureTest {

    @Test
    void theBandsGoFromABreezeToABomb() {
        assertEquals(AirPressure.Band.BREEZE, AirPressure.band(1.0D));
        assertEquals(AirPressure.Band.GUST, AirPressure.band(3.0D));
        assertEquals(AirPressure.Band.GALE, AirPressure.band(10.0D));
        assertEquals(AirPressure.Band.BOMB, AirPressure.band(30.0D));
    }

    @Test
    void onlyABombExplodes() {
        assertEquals(0.0F, AirPressure.bomb(29.0D), 1.0E-6F);
        assertTrue(AirPressure.bomb(100.0D) > AirPressure.bomb(30.0D));
        assertTrue(AirPressure.bomb(1.0E6D) > 6.0F, "no ceiling");
    }

    @Test
    void theHarderItIsPressedTheHarderAndFartherItThrows() {
        assertTrue(AirPressure.push(20.0D) > AirPressure.push(3.0D));
        assertEquals(3.5D, AirPressure.push(1000.0D), 1.0E-9);
        assertTrue(AirPressure.reach(25.0D) > AirPressure.reach(4.0D));
        assertTrue(AirPressure.reach(1000.0D) > 10.0D, "no ceiling");
    }
}
