package com.elderlexicon.mod.spell.sight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevelationTest {

    @Test
    void defaultGazeIsSixteenBlocksForTwoSeconds() {
        assertEquals(16.0D, Revelation.radius(null, 2.0D));
        assertEquals(2.0D, Revelation.seconds(null));
        assertEquals(40, Revelation.ticks(Revelation.seconds(null)));
    }

    @Test
    void potencyBuysReach() {
        assertEquals(100.0D, Revelation.radius(20.0D, 2.0D), 1.0E-9);
        assertEquals(16.0D, Revelation.radius(3.2D, 2.0D), 1.0E-9);
        assertEquals(10.0D, Revelation.radius(20.0D, 20.0D), 1.0E-9, "a longer gaze reaches less for the same UMU");
        assertEquals(Revelation.MAX_RADIUS, Revelation.radius(1000.0D, 2.0D));
        assertEquals(16.0D, Revelation.radius(0.0D, 2.0D));
    }

    @Test
    void theSpiritComparesWholeUmu() {
        assertTrue(Revelation.matches(1.5D, 2.0D), "stone (1.5) is earth of 2 UMU");
        assertTrue(Revelation.matches(2.0D, 2.0D), "cobblestone too");
        assertTrue(Revelation.matches(0.5D, 1.0D), "dirt is earth of 1 UMU");
        assertFalse(Revelation.matches(3.0D, 2.0D), "ores are not");
        assertFalse(Revelation.matches(Double.POSITIVE_INFINITY, 50.0D), "bedrock is beyond measure");
        assertTrue(Revelation.matches(3.0D, null), "nothing asked shows everything");
    }

    @Test
    void chronosSetsTheDuration() {
        assertEquals(10.0D, Revelation.seconds(10.0D));
        assertEquals(200, Revelation.ticks(10.0D));
    }

    @Test
    void costGrowsWithRadiusAndTime() {
        assertEquals(3.2D, Revelation.cost(16.0D, 2.0D), 1.0E-9);
        assertEquals(6.4D, Revelation.cost(32.0D, 2.0D), 1.0E-9);
        assertEquals(16.0D, Revelation.cost(16.0D, 10.0D), 1.0E-9);
    }

    @Test
    void sourceRunesPickWhatIsSought() {
        assertEquals(Revelation.Kind.IGNI, Revelation.Kind.ofSource("igni"));
        assertEquals(Revelation.Kind.AURA, Revelation.Kind.ofSource("AURA"));
        assertEquals(Revelation.Kind.VIS, Revelation.Kind.ofSource("vis"));
        assertEquals(Revelation.Kind.VIS, Revelation.Kind.ofSource(null));
    }

    @Test
    void earthIsWorthItsHardness() {
        assertEquals(0.5D, Revelation.earthUmu(0.5F), 1.0E-9);
        assertEquals(Double.POSITIVE_INFINITY, Revelation.earthUmu(-1.0F));
    }

    @Test
    void denserEarthIsSeenMoreStrongly() {
        double dirt = Revelation.density(Revelation.earthUmu(0.5F));
        double stone = Revelation.density(Revelation.earthUmu(1.5F));
        double ore = Revelation.density(Revelation.earthUmu(3.0F));
        double obsidian = Revelation.density(Revelation.earthUmu(50.0F));

        assertTrue(dirt < stone && stone < ore && ore < obsidian);
        assertEquals(1.0D, obsidian, 1.0E-9);
        assertEquals(1.0D, Revelation.density(Revelation.earthUmu(-1.0F)));
        assertEquals(0.0D, Revelation.density(0.1D));
    }
}
