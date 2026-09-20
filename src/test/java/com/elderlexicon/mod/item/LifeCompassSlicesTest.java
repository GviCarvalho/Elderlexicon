package com.elderlexicon.mod.item;

import com.elderlexicon.mod.vita.VitaProfile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifeCompassSlicesTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void computesRatiosFromProfile() {
        VitaProfile profile = new VitaProfile(100.0D, 55.0D, 38.0D, 2.0D, 5.0D,
                10.0D, 5.0D, 3.0D, 2.0D);
        LifeCompassSlices slices = LifeCompassSlices.fromProfile(profile);

        assertEquals(0.55D, slices.aquaRatio(), EPSILON);
        assertEquals(0.38D, slices.auraRatio(), EPSILON);
        assertEquals(0.02D, slices.igniRatio(), EPSILON);
        assertEquals(0.05D, slices.firmoRatio(), EPSILON);

        double overflowTotal = 10.0D + 5.0D + 3.0D + 2.0D;
        assertEquals(10.0D / overflowTotal, slices.aquaOverflowRatio(), EPSILON);
        assertEquals(5.0D / overflowTotal, slices.auraOverflowRatio(), EPSILON);
        assertEquals(3.0D / overflowTotal, slices.igniOverflowRatio(), EPSILON);
        assertEquals(2.0D / overflowTotal, slices.firmoOverflowRatio(), EPSILON);
    }

    @Test
    void handlesEmptyProfilesSafely() {
        VitaProfile profile = VitaProfile.empty();
        LifeCompassSlices slices = LifeCompassSlices.fromProfile(profile);
        assertTrue(slices.isEmpty(), "Empty profile should produce empty slices");
        assertEquals(0.0D, slices.aquaRatio(), EPSILON);
        assertEquals(0.0D, slices.auraRatio(), EPSILON);
    }
}
