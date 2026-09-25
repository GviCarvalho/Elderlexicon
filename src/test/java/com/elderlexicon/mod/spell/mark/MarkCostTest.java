package com.elderlexicon.mod.spell.mark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MarkCostTest {

    private static final double PLAYER = 20.0D;

    @Test
    void theDefaultThrowIsASinglePush() {
        MarkCost.Throw thrown = MarkCost.throwWith(MarkCost.DEFAULT_THROW_ENERGY, PLAYER, MarkCost.MAX_ENTITY_SPEED);
        assertEquals(2.0D * Math.sqrt(0.5D), thrown.speed(), 1.0E-9);
        assertEquals(1, thrown.pushTicks());
    }

    @Test
    void energyBeyondTheTopSpeedBecomesAThrust() {
        MarkCost.Throw strong = MarkCost.throwWith(500.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED);
        MarkCost.Throw stronger = MarkCost.throwWith(2000.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED);

        assertEquals(MarkCost.MAX_ENTITY_SPEED, strong.speed());
        assertTrue(strong.pushTicks() > 1);
        assertTrue(stronger.pushTicks() > strong.pushTicks());
        assertTrue(stronger.cost() > strong.cost(), "a longer push carries the mass farther");
    }

    @Test
    void theThrustIsCapped() {
        MarkCost.Throw absurd = MarkCost.throwWith(1.0E9D, PLAYER, MarkCost.MAX_ENTITY_SPEED);
        assertEquals(MarkCost.MAX_PUSH_TICKS, absurd.pushTicks());
    }

    @Test
    void chronosSpreadsTheSameEnergyOverTheWholeWindow() {
        MarkCost.Throw tenSeconds = MarkCost.throwWith(1000.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED, 200);

        assertEquals(200, tenSeconds.pushTicks(), "pushed for the whole 10 seconds");
        assertEquals(2.0D * Math.sqrt(1000.0D / (PLAYER * 200)), tenSeconds.speed(), 1.0E-9);
        assertTrue(tenSeconds.speed() < MarkCost.throwWith(1000.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED).speed(),
                "slower, but more controlled");
    }

    @Test
    void noChronosIsTheOrdinaryThrow() {
        assertEquals(MarkCost.throwWith(10.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED),
                MarkCost.throwWith(10.0D, PLAYER, MarkCost.MAX_ENTITY_SPEED, 0));
    }

    @Test
    void heavierThingsGoSlower() {
        double light = MarkCost.throwWith(10.0D, 10.0D, MarkCost.MAX_ENTITY_SPEED).speed();
        double heavy = MarkCost.throwWith(10.0D, 40.0D, MarkCost.MAX_ENTITY_SPEED).speed();
        assertTrue(heavy < light);
    }

    @Test
    void movementIsMassTimesDistance() {
        assertEquals(2.4D, MarkCost.movement(PLAYER, 12.0D), 1.0E-9);
    }
}
