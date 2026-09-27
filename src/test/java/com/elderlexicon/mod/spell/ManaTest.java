package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManaTest {

    @Test
    void experienceIsCountedAsTheGameCountsIt() {
        assertEquals(0.0D, Mana.points(0, 0.0F), 1.0E-9);
        assertEquals(352.0D, Mana.points(16, 0.0F), 1.0E-9);
        assertEquals(1395.0D, Mana.points(30, 0.0F), 1.0E-9);
        assertEquals(1395.0D + 56.0D, Mana.points(30, 0.5F), 1.0E-9, "half of the 112 to the next level");
    }

    @Test
    void tenPointsAreOneUmuWithNoCeiling() {
        assertEquals(139.5D, Mana.umuOf(1395.0D), 1.0E-9);
        assertTrue(Mana.umuOf(Mana.points(2_000_000_000, 0.0F)) > 1.0E17D, "no Anchor of a Hundred in the mod");
    }

    @Test
    void itsLightLingersLongerTheMoreThereIs() {
        assertTrue(Mana.lightSeconds(100.0D) > Mana.lightSeconds(10.0D));
        assertEquals(30.0D, Mana.lightSeconds(1.0E18D), 1.0E-9);
    }
}
