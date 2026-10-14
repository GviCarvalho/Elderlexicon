package com.elderlexicon.mod.mind;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A caster backs off the way with room, and knows a corner when it is in one (docs/feiticeiro-design.md, 3.12). */
class RoomTest {

    /** A hall walled at {@code half} from the middle. */
    private static Room.Ground hall(double halfX, double halfZ) {
        return (x, z) -> Math.abs(x) <= halfX && Math.abs(z) <= halfZ;
    }

    @Test
    void onOpenGroundItBacksStraightAway() {
        Room.Way way = Room.away(hall(100.0D, 100.0D), 0.0D, 0.0D, 0.0D, -5.0D);
        assertEquals(0.0D, way.dx(), 1.0E-9D);
        assertEquals(1.0D, way.dz(), 1.0E-9D);
        assertEquals(Room.SIGHT, way.room());
    }

    @Test
    void withAWallBehindItTurnsAside() {
        // The wall is a block behind it: straight away has no room, the sides have.
        Room.Way way = Room.away(hall(20.0D, 1.5D), 0.0D, 1.0D, 0.0D, -4.0D);
        assertTrue(Math.abs(way.dx()) > 0.6D, "it turns aside: " + way);
        assertTrue(way.room() >= 8.0D, "room " + way.room());
    }

    @Test
    void inACornerItIsCornered() {
        Room.Way way = Room.away(hall(5.0D, 5.0D), 4.5D, 4.5D, 2.0D, 2.0D);
        assertTrue(way.room() < Room.CORNERED, "room " + way.room());
    }

    @Test
    void itLooksNoFartherThanItsSight() {
        assertEquals(Room.SIGHT, Room.free(hall(1000.0D, 1000.0D), 0.0D, 0.0D, 1.0D, 0.0D));
        assertEquals(3.0D, Room.free(hall(3.5D, 3.5D), 0.0D, 0.0D, 1.0D, 0.0D));
    }
}
