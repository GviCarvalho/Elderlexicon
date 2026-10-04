package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** How matter goes into the world, worked out before the world is touched: as it is, nothing reacting on the way in. */
class PlacementTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    private static Substance substance(String id) {
        return TABLE.substance(id).orElseThrow();
    }

    private static Placement plan(Matter matter) {
        return Placement.plan(TABLE, matter).orElseThrow();
    }

    @Test
    void wholeBlocksArePlacedAndTheRestIsLeftOver() {
        Placement lava = plan(Matter.of(substance("stone"), State.LIQUID, 40.0D));
        assertEquals("minecraft:lava", lava.form().id());
        assertEquals(2, lava.units(), "forty UMU of molten stone are two blocks of lava, sixteen each");
        assertEquals(32.0D, lava.placed(), 1.0E-9);
        assertEquals(8.0D, lava.leftover(), 1.0E-9, "and eight UMU left, not lost");
    }

    @Test
    void whatShowsAsAnItemIsPlacedAsItems() {
        Placement flesh = plan(Matter.natural(substance("flesh"), 2.25D));
        assertEquals(Form.Kind.ITEM, flesh.form().kind());
        assertEquals(2, flesh.units(), "an item is one UMU");
        assertEquals(0.25D, flesh.leftover(), 1.0E-9);
    }

    @Test
    void aGasGoesIntoTheAirWhole() {
        Placement cloud = plan(Matter.of(TABLE.primordial(VitaElement.AQUA), State.GAS, 2.5D));
        assertTrue(cloud.floats());
        assertEquals(Form.Kind.PARTICLE, cloud.form().kind(), "what it looks like there");
        assertEquals(2.5D, cloud.placed(), 1.0E-9, "into the air, all of it");
        assertEquals(0.0D, cloud.leftover(), 1.0E-9);
    }

    @Test
    void fireGoesIntoTheAirAsAnyGas() {
        // A block of fire is not laid as the game's fire: its particles go into the air, where the drives make of them
        // a flame, or lightning (docs/particulas-design.md, stage 9).
        Placement fire = plan(Matter.natural(TABLE.primordial(VitaElement.IGNI), 16.0D));
        assertTrue(fire.floats());
        assertFalse(fire.formless(), "it is fire, by its name");
        assertEquals(16.0D, fire.placed(), 1.0E-9);
        assertEquals(0.0D, fire.leftover(), 1.0E-9);
    }

    @Test
    void aMixtureWithNoNameIsFormlessMatterThatHoldsItsParticles() {
        Matter mixed = new Matter(Composition.of(Map.of(VitaElement.FIRMO, 0.2D, VitaElement.AQUA, 0.8D)),
                State.LIQUID, 80.0D);
        assertTrue(mixed.unnamed(TABLE));
        Placement formless = plan(mixed);
        assertTrue(formless.formless());
        assertTrue(formless.unnamed());
        assertNull(formless.form());
        assertEquals(80.0D, formless.placed(), 1.0E-9, "formless matter holds it all, exactly");
        assertEquals(0.0D, formless.leftover(), 1.0E-9);
        // 20480 particles fill five blocks of 4096, as any block holds.
        assertEquals(5, formless.units());
    }

    @Test
    void aSubstanceInAStateWithNoLookIsFormless() {
        Placement molten = plan(Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 128.0D));
        assertTrue(molten.formless());
        assertFalse(molten.unnamed(), "molten earth is still earth");
        assertEquals(8, molten.units(), "128 UMU of earth fill eight blocks, liquid or not");
        assertEquals(128.0D, molten.placed(), 1.0E-9);
        Placement small = plan(Matter.of(TABLE.primordial(VitaElement.FIRMO), State.LIQUID, 0.1D));
        assertEquals(1, small.units(), "however little there is, it takes a block");
        assertEquals(0.1D, small.placed(), 0.5D / Particles.PER_UMU, "to the nearest particle");
    }

    @Test
    void nothingReactsOnTheWayIn() {
        // Fire and water in one liquid go into the world as they are, one formless liquid: what they do there (the fire,
        // with no mass to hold it, is agitation, and the water boils) is the drives' to do.
        Matter hot = new Matter(Composition.of(Map.of(VitaElement.AQUA, 2.0D, VitaElement.IGNI, 1.0D)), State.LIQUID,
                48.0D);
        Placement placement = plan(hot);
        assertTrue(placement.formless());
        assertFalse(placement.floats());
        assertEquals(hot, placement.matter());
        assertEquals(48.0D, placement.placed() + placement.leftover(), 1.0E-9, "nothing is lost (L1)");
    }

    @Test
    void aFloatingMixtureWithNoNameShowsItsOwnColour() {
        Matter hot = new Matter(Composition.of(Map.of(VitaElement.AURA, 0.7D, VitaElement.IGNI, 0.3D)),
                State.GAS, 2.0D);
        Placement cloud = plan(hot);
        assertTrue(cloud.formless());
        assertTrue(cloud.floats());
        assertEquals(2.0D, cloud.placed(), 1.0E-9);
    }

    @Test
    void noMatterGoesNowhere() {
        assertTrue(Placement.plan(TABLE, Matter.natural(substance("stone"), 0.0D)).isEmpty());
    }
}
