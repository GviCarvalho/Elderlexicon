package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** A blow is the energy of what flies (docs/plano-rosa-dos-elementos.md, section 3). */
class ImpactLawTest {

    private static final double DELTA = 1.0E-9D;

    @Test
    void theEnergyIsHalfTheMassTimesTheSpeedSquared() {
        assertEquals(0.5D * 10.0D * 4.0D, ImpactLaw.energy(10.0D, 2.0D), DELTA);
        assertEquals(4.0D * ImpactLaw.energy(3.0D, 1.0D), ImpactLaw.energy(3.0D, 2.0D), DELTA);
    }

    @Test
    void earthHitsHarderThanFire() {
        double earth = ImpactLaw.energy(Qualities.of(VitaElement.FIRMO), 10.0D, 1.5D);
        double water = ImpactLaw.energy(Qualities.of(VitaElement.AQUA), 10.0D, 1.5D);
        double fire = ImpactLaw.energy(Qualities.of(VitaElement.IGNI), 10.0D, 1.5D);
        assertTrue(earth > water && water > fire);
        assertEquals(2.0D * water, earth, DELTA);
        assertTrue(ImpactLaw.damage(earth) > 4.0D, "ten UMU of earth thrown hurts");
        assertTrue(ImpactLaw.damage(fire) < 1.0D, "fire hurts by its heat, not its weight");
    }

    @Test
    void harderBlocksCostMore() {
        assertTrue(ImpactLaw.cost(50.0D) > ImpactLaw.cost(1.5D));
        assertTrue(ImpactLaw.cost(1.5D) > ImpactLaw.cost(0.5D));
        assertEquals(Double.POSITIVE_INFINITY, ImpactLaw.cost(-1.0D), "bedrock never breaks");
    }

    @Test
    void theRadiusGrowsWithTheEnergy() {
        assertEquals(-1, ImpactLaw.radius(0.1D), "a touch breaks nothing");
        assertEquals(0, ImpactLaw.radius(1.0D), "a little breaks only what it struck");
        assertTrue(ImpactLaw.radius(200.0D) > ImpactLaw.radius(11.0D));
        assertTrue(ImpactLaw.radius(1.0E9D) <= ImpactLaw.MAX_RADIUS);
    }

    @Test
    void nothingIsWorthMoreThanItsEnergy() {
        for (double energy : new double[] {0.0D, 0.3D, 5.0D, 40.0D, 500.0D}) {
            assertTrue(ImpactLaw.breaking(energy) <= energy);
            assertTrue(ImpactLaw.damage(energy) <= energy);
        }
    }

    @Test
    void aStrongerBlowIsLouderAndDeeper() {
        assertTrue(ImpactLaw.volume(100.0D) > ImpactLaw.volume(1.0D));
        assertTrue(ImpactLaw.pitch(100.0D) < ImpactLaw.pitch(1.0D));
        assertFalse(ImpactLaw.blast(10.0D));
        assertTrue(ImpactLaw.blast(100.0D));
    }

    @Test
    void visIsALittleOfEverything() {
        Qualities vis = Qualities.of(VitaElement.BALANCED);
        assertEquals(0.25D, vis.weight(), DELTA);
        assertEquals(0.25D, vis.heat(), DELTA);
        assertTrue(vis.density() > Qualities.of(VitaElement.IGNI).density());
        assertTrue(vis.density() < Qualities.of(VitaElement.FIRMO).density());
    }
}
