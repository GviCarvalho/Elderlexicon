package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VitaHomeostasisTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void appliesCyclicDampingWhenAddingEnergy() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D);

        data.addElementEnergy(VitaElement.AQUA, 2.0D);

        assertEquals(57.0D, data.get(VitaElement.AQUA), EPSILON);
        assertEquals(2.0D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(38.0D, data.get(VitaElement.AURA), EPSILON);
        assertEquals(5.0D, data.get(VitaElement.FIRMO), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(2.0D - (2.0D * 0.45D), data.get(VitaElement.IGNI), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(38.0D + (2.0D * 0.20D), data.get(VitaElement.AURA), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(5.0D - (2.0D * 0.10D), data.get(VitaElement.FIRMO), EPSILON);

        assertTrue(!data.runHomeostasisTick(), "Queue should empty after three ticks");
    }

    @Test
    void energyRemovalPushesForwardInCycle() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D);

        data.consumeElement(VitaElement.IGNI, 1.0D);

        assertEquals(1.0D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(38.0D, data.get(VitaElement.AURA), EPSILON);
        assertEquals(5.0D, data.get(VitaElement.FIRMO), EPSILON);
        assertEquals(55.0D, data.get(VitaElement.AQUA), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(38.0D + (1.0D * 0.45D), data.get(VitaElement.AURA), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(5.0D - (1.0D * 0.20D), data.get(VitaElement.FIRMO), EPSILON);

        assertTrue(data.runHomeostasisTick());
        assertEquals(55.0D + (1.0D * 0.10D), data.get(VitaElement.AQUA), EPSILON);
    }

    @Test
    void ignoresTinyDeltasToAvoidOscillation() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D);

        data.addElementEnergy(VitaElement.FIRMO, 0.01D);

        assertEquals(5.01D, data.get(VitaElement.FIRMO), EPSILON);
        assertEquals(55.0D, data.get(VitaElement.AQUA), EPSILON);
        assertEquals(2.0D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(38.0D, data.get(VitaElement.AURA), EPSILON);

        assertTrue(!data.runHomeostasisTick(), "Tiny adjustments should skip scheduling");
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
