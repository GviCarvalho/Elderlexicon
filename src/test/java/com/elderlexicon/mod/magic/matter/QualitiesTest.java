package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** What matter is like comes from what it holds, whatever it is called (docs/plano-materia-emergente.md). */
class QualitiesTest {

    private static final MaterialTable TABLE = Materials.builtIn();

    @Test
    void aMixtureIsLikeWhatItHolds() {
        Matter molten = new Matter(Composition.of(Map.of(VitaElement.FIRMO, 0.7D, VitaElement.IGNI, 0.3D)),
                State.LIQUID, 5.0D);
        Qualities qualities = Qualities.of(molten);
        assertTrue(qualities.heavy());
        assertTrue(qualities.hot(), "no one wrote magma: it burns because it is a third fire");
        assertFalse(qualities.wet());
        assertTrue(qualities.glow() > 0);
        assertEquals("líquida, pesada e quente (terra 70%, fogo 30%)", Qualities.describe(molten));
    }

    @Test
    void aLittleOfAQualityDoesNotAct() {
        Qualities stone = Qualities.of(Matter.natural(TABLE.substance("stone").orElseThrow(), 1.5D));
        assertFalse(stone.hot(), "a tenth of fire in stone does not burn");
        assertEquals(0, stone.glow());
        assertTrue(stone.heavy());
    }

    @Test
    void heatDriesWhatIsWet() {
        Matter warm = new Matter(Composition.of(Map.of(VitaElement.AQUA, 0.6D, VitaElement.IGNI, 0.4D)),
                State.LIQUID, 1.0D);
        assertTrue(Qualities.of(warm).wet(), "more water than fire: it still puts fire out");
        Matter hot = new Matter(Composition.of(Map.of(VitaElement.AQUA, 0.4D, VitaElement.IGNI, 0.6D)),
                State.LIQUID, 1.0D);
        assertFalse(Qualities.of(hot).wet());
        assertTrue(Qualities.of(hot).hot());
    }
}
