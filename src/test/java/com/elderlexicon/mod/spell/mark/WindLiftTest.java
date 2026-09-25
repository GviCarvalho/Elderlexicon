package com.elderlexicon.mod.spell.mark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WindLiftTest {

    private static final double PLAYER = 20.0D;
    /** 1 UMU per second. */
    private static final double ONE_PER_SECOND = 1.0D / 20.0D;

    @Test
    void oneUmuPerSecondHoldsAPlayerAtTheGround() {
        assertEquals(WindLift.GRAVITY, WindLift.acceleration(ONE_PER_SECOND, PLAYER, 0.0D), 1.0E-9);
        assertEquals(0.0D, WindLift.hoverHeight(ONE_PER_SECOND, PLAYER), 1.0E-9);
    }

    @Test
    void thePushFadesWithHeight() {
        double low = WindLift.acceleration(ONE_PER_SECOND, PLAYER, 0.0D);
        double high = WindLift.acceleration(ONE_PER_SECOND, PLAYER, WindLift.FADE_HEIGHT);
        assertEquals(low / Math.E, high, 1.0E-9);
    }

    @Test
    void moreWindHoldsHigherAtAnExponentialPrice() {
        assertEquals(2.0D, WindLift.hoverHeight(ONE_PER_SECOND * Math.E, PLAYER), 1.0E-9);
        assertEquals(ONE_PER_SECOND * Math.exp(2.0D), WindLift.powerToHover(PLAYER, 4.0D), 1.0E-9);
        assertEquals(4.0D, WindLift.hoverHeight(WindLift.powerToHover(PLAYER, 4.0D), PLAYER), 1.0E-9);
    }

    @Test
    void lighterThingsFloatOnLess() {
        assertTrue(WindLift.hoverHeight(ONE_PER_SECOND, 4.0D) > 0.0D, "a chicken floats where a player does not");
        assertEquals(0.0D, WindLift.hoverHeight(ONE_PER_SECOND / 2.0D, PLAYER), "too weak to lift a player");
    }
}
