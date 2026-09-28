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
    void anAmalgamSettlesAndEachPartIsPlacedAsWhatItIs() {
        Matter amalgam = MatterLaws.mix(List.of(
                Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 1.0D),
                Matter.of(TABLE.primordial(VitaElement.AQUA), State.LIQUID, 9.0D))).orElseThrow();
        assertTrue(amalgam.amalgam(TABLE));
        List<Placement> plan = Placement.plan(TABLE, amalgam);
        assertEquals(2, plan.size());
        Placement earth = plan.stream().filter(p -> p.substance().primordial()
                && p.substance().recipe().share(VitaElement.FIRMO) > 0.5D).findFirst().orElseThrow();
        assertEquals("minecraft:dirt", earth.form().id(), "the earth settles as soil");
        assertEquals(2, earth.units());
        Placement water = plan.stream().filter(p -> p != earth).findFirst().orElseThrow();
        assertEquals("minecraft:water", water.form().id());
        assertEquals(3, water.units());
        double total = plan.stream().mapToDouble(p -> p.placed() + p.leftover()).sum();
        assertEquals(10.0D, total, 1.0E-9, "nothing is lost (L1)");
    }
}
