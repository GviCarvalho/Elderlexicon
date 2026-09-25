package com.elderlexicon.mod.vita;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The body keeps its balance through damage and healing, and drifts back to it after a spell unbalances it. */
class VitaHomeostasisTest {

    private static final double EPSILON = 1.0E-4D;

    @Test
    void damageTakesEachElementInItsShare() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D, 20.0F);

        data.consume(20.0D); // 4 HP
        data.setLastHealth(16.0F);

        assertEquals(44.0D, data.get(VitaElement.AQUA), EPSILON);
        assertEquals(30.4D, data.get(VitaElement.AURA), EPSILON);
        assertEquals(1.6D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(4.0D, data.get(VitaElement.FIRMO), EPSILON);
        assertFalse(data.relaxTowardBalance(0.1D), "hurt, but not unbalanced");
    }

    @Test
    void anElementChangeStaysWhereItWasMade() {
        VitaData data = newData(55.0D, 38.0D, 2.0D, 5.0D, 20.0F);

        data.addElementEnergy(VitaElement.IGNI, 3.0D);

        assertEquals(5.0D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(55.0D, data.get(VitaElement.AQUA), EPSILON);
        assertEquals(38.0D, data.get(VitaElement.AURA), EPSILON);
        assertEquals(5.0D, data.get(VitaElement.FIRMO), EPSILON);
    }

    @Test
    void theBodyDriftsBackToBalance() {
        VitaData data = newData(55.0D, 38.0D, 5.0D, 1.0D, 20.0F);

        assertTrue(data.relaxTowardBalance(0.1D));
        assertEquals(5.0D - 0.3D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(1.0D + 0.4D, data.get(VitaElement.FIRMO), EPSILON);

        for (int second = 0; second < 120; second++) {
            data.relaxTowardBalance(0.1D);
        }
        assertEquals(2.0D, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(5.0D, data.get(VitaElement.FIRMO), EPSILON);
        assertFalse(data.relaxTowardBalance(0.1D), "balanced: nothing left to move");
    }

    @Test
    void resetBalancesForTheHealthNow() {
        VitaData data = newData(10.0D, 70.0D, 9.0D, 0.0D, 20.0F);
        data.setIgniTier(VitaImbalanceTier.SEVERELY_HIGH);

        data.reset(14.0F);

        assertEquals(70.0D * VitaSystem.AQUA_RATIO, data.get(VitaElement.AQUA), EPSILON);
        assertEquals(70.0D * VitaSystem.IGNI_RATIO, data.get(VitaElement.IGNI), EPSILON);
        assertEquals(14.0F, data.lastHealth(), EPSILON);
        assertEquals(VitaImbalanceTier.BALANCED, data.igniTier());
        assertFalse(data.relaxTowardBalance(0.1D));
    }

    private static VitaData newData(double aqua, double aura, double igni, double firmo, float health) {
        try {
            Constructor<VitaData> ctor = VitaData.class.getDeclaredConstructor(double.class, double.class, double.class, double.class, float.class);
            ctor.setAccessible(true);
            return ctor.newInstance(aqua, aura, igni, firmo, health);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Unable to create VitaData for test", exception);
        }
    }
}
