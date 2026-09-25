package com.elderlexicon.mod.spell;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ElementPersistenceTest {

    @Test
    void waterEarthAndTheirFusionsStay() {
        for (String rune : new String[] {"aqua", "firmo", "lutum", "fusus", " AQUA "}) {
            assertEquals(ElementPersistence.PERMANENT, ElementPersistence.of(rune), rune);
        }
    }

    @Test
    void fireAirManaAndTheirFusionsGo() {
        for (String rune : new String[] {"igni", "aura", "vis", "fulmen", "caligo", "nebula", "pulvis", null}) {
            assertEquals(ElementPersistence.EPHEMERAL, ElementPersistence.of(rune), String.valueOf(rune));
        }
    }
}
