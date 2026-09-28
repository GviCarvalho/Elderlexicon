package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** How matter goes into the world, worked out before the world is touched. */
class PlacementTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Substance substance(String id) {
        return TABLE.substance(id).orElseThrow();
    }

    @Test
    void wholeBlocksArePlacedAndTheRestIsLeftOver() {
        List<Placement> lava = Placement.plan(TABLE, Matter.of(substance("stone"), State.LIQUID, 4.0D));
        assertEquals(1, lava.size());
        assertEquals("minecraft:lava", lava.get(0).form().id());
        assertEquals(2, lava.get(0).units(), "four UMU of molten stone are two blocks of lava");
        assertEquals(3.0D, lava.get(0).placed(), 1.0E-9);
        assertEquals(1.0D, lava.get(0).leftover(), 1.0E-9, "and one UMU left, not lost");
    }

    @Test
    void whatShowsAsAnItemIsPlacedAsItems() {
        Substance flesh = substance("flesh");
        List<Placement> plan = Placement.plan(TABLE, Matter.natural(flesh, 1.2D));
        assertEquals(Form.Kind.ITEM, plan.get(0).form().kind());
        assertEquals(2, plan.get(0).units());
        assertEquals(0.2D, plan.get(0).leftover(), 1.0E-9);
    }

    @Test
    void aGasGoesIntoTheWorldWhole() {
        Matter vapour = Matter.of(TABLE.primordial(VitaElement.AQUA), State.GAS, 2.5D);
        Placement cloud = Placement.plan(TABLE, vapour).get(0);
        assertEquals(Form.Kind.PARTICLE, cloud.form().kind());
        assertEquals(2.5D, cloud.placed(), 1.0E-9, "it disperses, all of it");
        assertEquals(0.0D, cloud.leftover(), 1.0E-9);
    }

    @Test
    void anAmalgamStaysAsFormlessMatterUntilItFallsApart() {
        Matter amalgam = MatterLaws.mix(List.of(
                Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 1.0D),
                Matter.of(TABLE.primordial(VitaElement.AQUA), State.LIQUID, 9.0D))).orElseThrow();
        assertTrue(amalgam.amalgam(TABLE));
        List<Placement> plan = Placement.plan(TABLE, amalgam);
        assertEquals(1, plan.size());
        Placement formless = plan.get(0);
        assertTrue(formless.formless());
        assertTrue(formless.amalgam());
        assertNull(formless.form());
        assertEquals(10.0D, formless.placed(), 1.0E-9, "formless matter holds it all, exactly");
        assertEquals(0.0D, formless.leftover(), 1.0E-9);
        // Two blocks of molten earth and three of water fill five blocks together.
        assertEquals(5, formless.units());
    }

    @Test
    void aSubstanceInAStateWithNoLookIsFormless() {
        Placement molten = Placement.plan(TABLE, Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 4.0D))
                .get(0);
        assertTrue(molten.formless());
        assertFalse(molten.amalgam(), "molten earth is still earth");
        assertEquals(8, molten.units(), "four UMU of earth fill eight blocks, liquid or not");
        assertEquals(4.0D, molten.placed(), 1.0E-9);
        Placement small = Placement.plan(TABLE, Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 0.1D))
                .get(0);
        assertEquals(1, small.units(), "however little there is, it takes a block");
        assertEquals(0.1D, small.placed(), 1.0E-9);
    }

    @Test
    void aFloatingAmalgamComesApartAsItGoes() {
        Matter hot = MatterLaws.mix(List.of(
                Matter.of(TABLE.primordial(VitaElement.AQUA), State.GAS, 6.0D),
                Matter.of(TABLE.primordial(VitaElement.IGNI), State.PLASMA, 1.0D))).orElseThrow();
        assertTrue(hot.amalgam(TABLE));
        List<Placement> plan = Placement.plan(TABLE, hot);
        assertEquals(2, plan.size(), "water and fire, each as what it is");
        assertTrue(plan.stream().noneMatch(Placement::formless));
        double total = plan.stream().mapToDouble(p -> p.placed() + p.leftover()).sum();
        assertEquals(7.0D, total, 1.0E-9, "nothing is lost (L1)");
    }
}
