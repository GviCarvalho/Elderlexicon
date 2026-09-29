package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The rose of the elements and the tension above it (docs/plano-rosa-dos-elementos.md, section 2). */
class RoseTest {

    private static final double DELTA = 1.0E-9D;
    private static final MaterialTable TABLE = Materials.builtIn();

    @Test
    void eachAspectStandsAtItsOwnEnd() {
        assertEquals(new Rose(1.0D, 0.0D, 0.0D, 0.0D), Rose.of(VitaElement.IGNI));
        assertEquals(new Rose(-1.0D, 0.0D, 0.0D, 0.0D), Rose.of(VitaElement.AQUA));
        assertEquals(new Rose(0.0D, 1.0D, 0.0D, 0.0D), Rose.of(VitaElement.AURA));
        assertEquals(new Rose(0.0D, -1.0D, 0.0D, 0.0D), Rose.of(VitaElement.FIRMO));
    }

    @Test
    void visIsTheCentreAtFullTension() {
        Rose vis = Rose.of(VitaElement.BALANCED);
        assertEquals(0.0D, vis.distance(), DELTA);
        assertEquals(1.0D, vis.latent(), DELTA);
    }

    @Test
    void characterAndTensionAlwaysMakeTheWhole() {
        Rose rose = Rose.of(Map.of(VitaElement.IGNI, 3.0D, VitaElement.AQUA, 1.0D, VitaElement.FIRMO, 2.0D,
                VitaElement.BALANCED, 2.0D));
        assertEquals(1.0D, rose.character() + rose.latent(), DELTA);
        // Back to the shares: nothing was lost in the change of coordinates.
        Map<VitaElement, Double> shares = rose.shares();
        assertEquals(1.0D, shares.values().stream().mapToDouble(Double::doubleValue).sum(), DELTA);
    }

    @Test
    void steamHoldsThermalTension() {
        Rose steam = Rose.of(Map.of(VitaElement.IGNI, 1.0D, VitaElement.AQUA, 1.0D));
        assertEquals(1.0D, steam.thermal(), DELTA);
        assertEquals(0.0D, steam.mechanical(), DELTA);
        assertEquals(0.0D, steam.x(), DELTA);
    }

    @Test
    void aSolidHoldsItsTensionStill() {
        // Wood has water and fire in it and does not boil; melted, the same parts are free to act.
        Composition wood = TABLE.substance("wood").orElseThrow().recipe();
        Rose rose = Rose.of(wood);
        assertTrue(rose.latent() > Tension.PHENOMENON, "wood holds opposites");
        assertFalse(Tension.of(rose, State.SOLID, Agitation.NONE).phenomenon(), "a solid holds them still");
        assertTrue(Tension.of(rose, State.LIQUID, Agitation.NONE).phenomenon(), "a fluid lets them act");
    }

    @Test
    void naturalThingsAreMatter() {
        for (String name : new String[] {"earth", "stone", "wood", "iron", "bone", "flesh"}) {
            Substance substance = TABLE.substance(name).orElseThrow();
            assertFalse(Tension.of(Matter.natural(substance, 1.0D)).phenomenon(), name + " is matter");
        }
    }

    @Test
    void agitationLiftsMatterIntoAPhenomenon() {
        Rose earth = Rose.of(VitaElement.FIRMO);
        assertFalse(Tension.of(earth, State.SOLID, Agitation.NONE).phenomenon());
        // Thrown hard, earth is a blow waiting to land.
        assertTrue(Tension.of(earth, State.SOLID, Agitation.moving(1.0D, 1.5D)).phenomenon());
        // Fire hot enough is plasma; many UMU pressed into a point are pressure.
        assertEquals(1.0D, Agitation.heated(30.0D).thermal(), DELTA);
        assertEquals(0.0D, Agitation.heated(1.0D).thermal(), DELTA);
        assertEquals(1.0D, Agitation.pressed(1000.0D).concentration(), DELTA);
        assertTrue(Agitation.pressed(20.0D).total() > Tension.PHENOMENON);
    }
}
