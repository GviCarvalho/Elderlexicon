package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElementPersistenceTest {

    @Test
    void waterAndEarthStay() {
        for (String rune : new String[] {"aqua", "firmo", " AQUA "}) {
            assertEquals(ElementPersistence.PERMANENT, ElementPersistence.of(rune), rune);
        }
    }

    @Test
    void fireAirAndManaGo() {
        for (String rune : new String[] {"igni", "aura", "vis", null}) {
            assertEquals(ElementPersistence.EPHEMERAL, ElementPersistence.of(rune), String.valueOf(rune));
        }
    }
}
