package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitaDataExcessTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void computesExcessAboveBaseline() {
        VitaData data = newData(55.0D, 38.0D, 5.0D, 2.0D); // total 100

        assertEquals(3.0D, data.elementExcess(VitaElement.IGNI), EPSILON);
        assertEquals(0.0D, data.elementExcess(VitaElement.AURA), EPSILON);
        assertEquals(100.0D, data.totalUmu(), EPSILON);
    }

    @Test
    void consumeExcessClampsAtBaselineAndReturnsRemainder() {
        VitaData data = newData(55.0D, 38.0D, 5.0D, 2.0D);

        double remaining = data.consumeElementExcess(VitaElement.IGNI, 4.0D);

        assertEquals(1.0D, remaining, EPSILON, "Should return leftover when cost exceeds overflow");
        assertEquals(2.0D, data.get(VitaElement.IGNI), EPSILON, "Element should not dip below baseline share");
        assertTrue(data.elementExcess(VitaElement.IGNI) <= EPSILON, "Overflow should be exhausted");
    }

    @Test
    void balancedRequestsReportNoExcess() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D);

        assertEquals(0.0D, data.elementExcess(VitaElement.BALANCED), EPSILON);
        double remaining = data.consumeElementExcess(VitaElement.BALANCED, 5.0D);
        assertEquals(5.0D, remaining, EPSILON);
    }

    private static VitaData newData(double aqua, double aura, double igni, double firmo) {
        try {
            Constructor<VitaData> ctor = VitaData.class.getDeclaredConstructor(double.class, double.class, double.class, double.class, float.class);
            ctor.setAccessible(true);
            return ctor.newInstance(aqua, aura, igni, firmo, 20.0F);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create VitaData for test", exception);
        }
    }
}
