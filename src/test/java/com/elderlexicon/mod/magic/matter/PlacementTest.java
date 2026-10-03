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
        List<Placement> lava = Placement.plan(TABLE, Matter.of(substance("stone"), State.LIQUID, 40.0D));
        assertEquals(1, lava.size());
        assertEquals("minecraft:lava", lava.get(0).form().id());
        assertEquals(2, lava.get(0).units(), "forty UMU of molten stone are two blocks of lava, sixteen each");
        assertEquals(32.0D, lava.get(0).placed(), 1.0E-9);
        assertEquals(8.0D, lava.get(0).leftover(), 1.0E-9, "and eight UMU left, not lost");
    }

    @Test
    void whatShowsAsAnItemIsPlacedAsItems() {
        Substance flesh = substance("flesh");
        List<Placement> plan = Placement.plan(TABLE, Matter.natural(flesh, 2.25D));
        assertEquals(Form.Kind.ITEM, plan.get(0).form().kind());
        assertEquals(2, plan.get(0).units(), "an item is one UMU");
        assertEquals(0.25D, plan.get(0).leftover(), 1.0E-9);
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
    void aMixtureWithNoNameIsFormlessMatterThatHolds() {
        Matter mixed = MatterLaws.mix(List.of(
                Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 16.0D),
                Matter.of(TABLE.primordial(VitaElement.AQUA), State.LIQUID, 64.0D))).orElseThrow();
        assertTrue(mixed.unnamed(TABLE));
        List<Placement> plan = Placement.plan(TABLE, mixed);
        assertEquals(1, plan.size());
        Placement formless = plan.get(0);
        assertTrue(formless.formless());
        assertTrue(formless.unnamed());
        assertNull(formless.form());
        assertEquals(80.0D, formless.placed(), 1.0E-9, "formless matter holds it all, exactly");
        assertEquals(0.0D, formless.leftover(), 1.0E-9);
        // Eighty UMU fill five blocks, sixteen each, as any block holds.
        assertEquals(5, formless.units());
    }

    @Test
    void aSubstanceInAStateWithNoLookIsFormless() {
        Placement molten = Placement.plan(TABLE, Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 128.0D))
                .get(0);
        assertTrue(molten.formless());
        assertFalse(molten.unnamed(), "molten earth is still earth");
        assertEquals(8, molten.units(), "128 UMU of earth fill eight blocks, liquid or not");
        assertEquals(128.0D, molten.placed(), 1.0E-9);
        Placement small = Placement.plan(TABLE, Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 0.1D))
                .get(0);
        assertEquals(1, small.units(), "however little there is, it takes a block");
        assertEquals(0.1D, small.placed(), 1.0E-9);
    }

    @Test
    void whatOppositesLetOutGoesAsAGasOfItsOwn() {
        Matter hot = MatterLaws.mix(List.of(
                Matter.of(TABLE.primordial(VitaElement.AQUA), State.LIQUID, 32.0D),
                Matter.of(TABLE.primordial(VitaElement.IGNI), State.PLASMA, 16.0D))).orElseThrow();
        List<Placement> plan = Placement.plan(TABLE, hot);
        assertEquals(2, plan.size(), "the vapour, and the water that stays");
        Placement vapour = plan.get(0);
        assertTrue(vapour.floats());
        // This older law lets out as much fire as water; the drives (magic/physics) boil water into steam, nine of
        // water to one of fire, and take its place in stage 9.
        assertEquals(0.5D, vapour.matter().composition().share(VitaElement.AQUA), 1.0E-9, "half water");
        assertEquals(0.5D, vapour.matter().composition().share(VitaElement.IGNI), 1.0E-9, "half fire");
        Placement water = plan.get(1);
        assertEquals("minecraft:water", water.form().id());
        assertEquals(1, water.units());
        double total = plan.stream().mapToDouble(p -> p.placed() + p.leftover()).sum();
        assertEquals(48.0D, total, 1.0E-9, "nothing is lost (L1)");
    }

    @Test
    void aFloatingMixtureWithNoNameShowsItsOwnColour() {
        Matter hot = new Matter(Composition.of(java.util.Map.of(VitaElement.AURA, 0.7D, VitaElement.IGNI, 0.3D)),
                State.GAS, 2.0D);
        Placement cloud = Placement.plan(TABLE, hot).get(0);
        assertTrue(cloud.formless());
        assertTrue(cloud.floats());
        assertEquals(2.0D, cloud.placed(), 1.0E-9);
    }
}
