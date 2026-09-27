package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AspectTest {

    @Test
    void eachElementIsAnAspectOfEnergyAndVisIsTheCenter() {
        assertEquals(Aspect.HEAT, Aspect.of(VitaElement.IGNI));
        assertEquals(Aspect.COHESION, Aspect.of(VitaElement.AQUA));
        assertEquals(Aspect.EXPANSION, Aspect.of(VitaElement.AURA));
        assertEquals(Aspect.MASS, Aspect.of(VitaElement.FIRMO));
        assertNull(Aspect.of(VitaElement.BALANCED));
    }

    @Test
    void theAspectsLieOnTwoAxesOfOpposites() {
        assertEquals(Aspect.COHESION, Aspect.HEAT.opposite(), "heat against cohesion");
        assertEquals(Aspect.MASS, Aspect.EXPANSION.opposite(), "expansion against mass");
        for (Aspect aspect : Aspect.values()) {
            assertEquals(aspect.axis, aspect.opposite().axis);
            assertEquals(aspect, aspect.opposite().opposite());
        }
    }

    @Test
    void neighboursAreOneStepAndOppositesTwo() {
        assertEquals(0, Aspect.distance(Aspect.HEAT, Aspect.HEAT));
        assertEquals(1, Aspect.distance(Aspect.HEAT, Aspect.EXPANSION), "fire and air share hot");
        assertEquals(1, Aspect.distance(Aspect.EXPANSION, Aspect.COHESION), "air and water share wet");
        assertEquals(1, Aspect.distance(Aspect.COHESION, Aspect.MASS), "water and earth share cold");
        assertEquals(1, Aspect.distance(Aspect.MASS, Aspect.HEAT), "earth and fire share dry");
        assertEquals(2, Aspect.distance(Aspect.HEAT, Aspect.COHESION));
        assertEquals(2, Aspect.distance(Aspect.EXPANSION, Aspect.MASS));
    }

    @Test
    void visHoldsAQuarterOfEachAspect() {
        double total = 0.0D;
        for (Aspect aspect : Aspect.values()) {
            assertEquals(0.25D, Aspect.share(VitaElement.BALANCED, aspect), 1.0E-9);
            total += Aspect.share(VitaElement.BALANCED, aspect);
        }
        assertEquals(1.0D, total, 1.0E-9, "the four quarters are all of it");
        assertEquals(1.0D, Aspect.share(VitaElement.IGNI, Aspect.HEAT), 1.0E-9);
        assertEquals(0.0D, Aspect.share(VitaElement.IGNI, Aspect.MASS), 1.0E-9);
    }
}
