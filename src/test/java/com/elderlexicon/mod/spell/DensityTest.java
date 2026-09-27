package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DensityTest {

    @Test
    void theRocksGoFromSoilToObsidianAndBeyond() {
        assertEquals(Density.Rock.SOIL, Density.rock(0.5D));
        assertEquals(Density.Rock.STONE, Density.rock(1.5D));
        assertEquals(Density.Rock.DEEPSLATE, Density.rock(3.0D));
        assertEquals(Density.Rock.DEEPSLATE, Density.rock(12.0D));
        assertEquals(Density.Rock.OBSIDIAN, Density.rock(13.0D));
        assertEquals(Density.Rock.OBSIDIAN, Density.rock(50.0D));
        assertEquals(Density.Rock.WELL, Density.rock(51.0D), "denser than any rock");
        assertEquals(Density.Rock.BLACK_HOLE, Density.rock(1000.0D), "so dense it falls into itself");
    }

    @Test
    void condensedAtOnceAllOfItGoesIntoOneBlock() {
        assertEquals(15.0D, Density.of(15.0D, 10, true), 1.0E-9, "ten stones in one block");
        assertEquals(1.5D, Density.of(15.0D, 10, false), 1.0E-9, "spread, it is still stone");
    }

    @Test
    void aWellRelaxesIntoObsidian() {
        assertEquals(150.0D, Density.relaxed(150.0D, 0.0D), 1.0E-9);
        assertEquals(50.0D + 100.0D / Math.E, Density.relaxed(150.0D, Density.RELAXING_TICKS), 1.0E-9);
        assertEquals(30.0D, Density.relaxed(30.0D, 1000.0D), 1.0E-9, "rock does not relax");
    }

    @Test
    void onlyAMassBeyondObsidianPulls() {
        assertEquals(0.0D, Density.wellReach(50.0D), 1.0E-9);
        assertEquals(0.0D, Density.wellPull(50.0D, 2.0D), 1.0E-9);
        assertTrue(Density.wellPull(150.0D, 2.0D) > Density.wellPull(150.0D, 4.0D), "closer pulls harder");
        assertTrue(Density.wellPull(300.0D, 3.0D) > Density.wellPull(100.0D, 3.0D), "denser pulls harder");
        assertEquals(12.0D, Density.wellReach(999.0D), 1.0E-9, "a well reaches twelve blocks at most");
        assertTrue(Density.wellReach(1.0E17D) > 40.0D, "a black hole reaches as far as its mass");
    }

    @Test
    void carbonPressedHardEnoughIsDiamond() {
        assertEquals(1, Density.diamonds(9, 27.0D));
        assertEquals(2, Density.diamonds(20, 60.0D));
        assertEquals(0, Density.diamonds(9, 2.0D), "not pressed hard enough");
        assertEquals(0, Density.diamonds(8, 24.0D), "not enough coal for one");
    }

    @Test
    void theHeavierTheMeteorTheBiggerTheCrater() {
        assertTrue(Density.meteor(50.0D) > Density.meteor(3.0D));
        assertTrue(Density.meteor(1.0E6D) > 6.0F, "no ceiling: it keeps growing, slowly");
    }

    @Test
    void aBlackHoleEvaporatesFasterThanAWellRelaxes() {
        double later = 40.0D;
        assertTrue(Density.evaporated(1.0E6D, later) < Density.relaxed(1.0E6D, later));
        assertTrue(Density.evaporated(1.0E17D, 2000.0D) < Density.BLACK_HOLE, "even the heaviest is gone in a few minutes");
    }

    @Test
    void theHeavierTheBiggerAndHungrier() {
        assertEquals(0.0F, Density.horizon(999.0D), 1.0E-6F);
        assertTrue(Density.horizon(1.0E9D) > Density.horizon(1.0E3D));
        assertTrue(Density.swallows(1.0E9D) > Density.swallows(1.0E3D));
        assertEquals(16, Density.swallows(1.0E17D), "no ceiling: it grows with its mass");
    }

    @Test
    void aBlackHolePullsHarderTheCloserAndTearsTheSoftestFirst() {
        double hole = 2000.0D;
        assertTrue(Density.holePull(hole, 2.0D) > Density.holePull(hole, 8.0D));
        assertTrue(Density.tears(hole, 0.5D, 9.0D), "loose soil gives way far out");
        assertTrue(!Density.tears(hole, 50.0D, 9.0D), "obsidian holds there");
        assertTrue(Density.tears(hole, 50.0D, 1.0D), "but not at the heart");
        assertTrue(Density.tears(1.0E6D, 50.0D, 9.0D), "a heavier hole tears obsidian farther out");
        assertTrue(Density.holeBlockReach(hole) < Density.wellReach(hole), "it pulls creatures farther than ground");
    }
}
