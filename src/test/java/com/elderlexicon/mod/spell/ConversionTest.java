package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConversionTest {

    @Test
    void theUmuBuysAsManyUnitsAsItHolds() {
        assertEquals(10, Conversion.units(10.0D, VitaElement.IGNI), "ten UMU of anything make ten flames");
        assertEquals(3, Conversion.units(10.0D, VitaElement.AQUA), "and three water sources");
        assertEquals(20, Conversion.units(10.0D, VitaElement.FIRMO), "and twenty blocks of loose soil");
        assertEquals(0, Conversion.units(10.0D, VitaElement.AURA), "air lays no blocks");
    }

    @Test
    void whatMakesNoWholeUnitIsLeftOver() {
        assertEquals(1.0D, Conversion.leftover(10.0D, VitaElement.AQUA, 3), 1.0E-9);
        assertEquals(0.0D, Conversion.leftover(10.0D, VitaElement.IGNI, 10), 1.0E-9);
        assertEquals(4.0D, Conversion.leftover(10.0D, VitaElement.IGNI, 6), 1.0E-9, "no room for four flames");
    }

    @Test
    void twentyBlocksOfSoilAreTenFlamesNotTwenty() {
        double umu = 20 * Density.SOIL;
        assertEquals(10, Conversion.units(umu, VitaElement.IGNI));
    }

    @Test
    void neighboursChangeOneQualityAndOppositesBoth() {
        assertEquals(1, Conversion.qualities(VitaElement.FIRMO, VitaElement.IGNI), "cold to hot");
        assertEquals(1, Conversion.qualities(VitaElement.IGNI, VitaElement.AURA), "dry to wet");
        assertEquals(1, Conversion.qualities(VitaElement.AURA, VitaElement.AQUA), "hot to cold");
        assertEquals(1, Conversion.qualities(VitaElement.AQUA, VitaElement.FIRMO), "wet to dry");
        assertEquals(2, Conversion.qualities(VitaElement.IGNI, VitaElement.AQUA));
        assertEquals(2, Conversion.qualities(VitaElement.FIRMO, VitaElement.AURA));
        assertEquals(0, Conversion.qualities(VitaElement.IGNI, VitaElement.IGNI));
    }

    @Test
    void oppositesCostAndTakeTwiceAsMuch() {
        assertEquals(2.0D, Conversion.work(40.0D, VitaElement.FIRMO, VitaElement.IGNI), 1.0E-9);
        assertEquals(4.0D, Conversion.work(40.0D, VitaElement.IGNI, VitaElement.AQUA), 1.0E-9);
        assertEquals(26, Conversion.ticks(40.0D, VitaElement.FIRMO, VitaElement.IGNI));
        assertEquals(52, Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.AQUA));
        assertEquals(0, Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.IGNI));
    }

    @Test
    void visBecomesAnythingFreelyButIsDearToMake() {
        assertEquals(0, Conversion.qualities(VitaElement.BALANCED, VitaElement.IGNI));
        assertEquals(2, Conversion.qualities(VitaElement.IGNI, VitaElement.BALANCED));
        assertEquals(Conversion.MIN_TICKS, Conversion.ticks(1000.0D, VitaElement.BALANCED, VitaElement.IGNI),
                "it takes only the shortest time");
    }

    @Test
    void aChainAddsUpTheQualitiesItChanges() {
        // fire to water directly changes both; by way of air, one and then one: the same work, the same time.
        assertEquals(Conversion.work(40.0D, VitaElement.IGNI, VitaElement.AQUA), Conversion.workFor(40.0D, 1 + 1), 1.0E-9);
        assertEquals(Conversion.ticks(40.0D, VitaElement.IGNI, VitaElement.AQUA), Conversion.ticksFor(40.0D, 2, true));
    }

    @Test
    void aChainTakesEachOfItsSteps() {
        java.util.List<VitaElement> chain = java.util.List.of(VitaElement.BALANCED, VitaElement.IGNI, VitaElement.AURA,
                VitaElement.AQUA, VitaElement.FIRMO);
        assertEquals(3, Conversion.chainQualities(chain), "vis to fire is free; the other three change one each");
        assertEquals(Conversion.MIN_TICKS + 3 * Conversion.stepTicks(40.0D, VitaElement.IGNI, VitaElement.AURA),
                Conversion.chainTicks(40.0D, chain));
    }
}
