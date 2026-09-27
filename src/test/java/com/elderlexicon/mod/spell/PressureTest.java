package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PressureTest {

    @Test
    void theBandsGoFromAJetToIce() {
        assertEquals(Pressure.Band.JET, Pressure.band(6.0D));
        assertEquals(Pressure.Band.CUTTING, Pressure.band(10.0D));
        assertEquals(Pressure.Band.ICE, Pressure.band(30.0D));
    }

    @Test
    void theLitresAreKeptWhenItBursts() {
        assertEquals(10, Pressure.sources(30.0D), "ten sources pressed in, ten come out");
        assertEquals(1, Pressure.sources(1.0D));
    }

    @Test
    void onlyAPressedJetCuts() {
        assertEquals(0, Pressure.cuts(9.0D));
        assertEquals(4, Pressure.cuts(12.0D));
        assertEquals(3333, Pressure.cuts(10000.0D), "no ceiling");
    }

    @Test
    void theHarderItIsPressedTheHarderItThrows() {
        assertTrue(Pressure.push(30.0D) > Pressure.push(6.0D));
        assertEquals(3.5D, Pressure.push(1000.0D), 1.0E-9, "never past what the network carries");
        assertTrue(Pressure.burstReach(100.0D) > Pressure.burstReach(30.0D));
    }

    @Test
    void onlyIceVIIShattersItsSurroundings() {
        assertEquals(0.0F, Pressure.shatter(29.0D), 1.0E-6F, "water that never froze only pushes");
        assertTrue(Pressure.shatter(100.0D) > Pressure.shatter(30.0D));
        assertTrue(Pressure.shatter(1.0E6D) > 6.0F, "no ceiling");
    }
}
