package com.elderlexicon.arena;

import java.util.Random;

/**
 * Where a fight in the arena happens (docs/feiticeiro-design.md, 3.12): flat ground walled at {@code halfX} and
 * {@code halfZ} from the middle. Open ground, a hall, a corridor: one who backs away the same way long enough meets a
 * wall, and in a corridor there is no side to turn to.
 */
record Hall(String name, double halfX, double halfZ) {

    static final Hall OPEN = new Hall("open ground", 40.0D, 40.0D);
    static final Hall ROOM = new Hall("a hall 16 x 16", 8.0D, 8.0D);
    static final Hall SMALL = new Hall("a hall 10 x 10", 5.0D, 5.0D);
    static final Hall FIELD = new Hall("a field 40 x 40", 20.0D, 20.0D);
    static final Hall CORRIDOR = new Hall("a corridor 5 x 40", 2.5D, 20.0D);

    /** Where a fight in training happens: open ground now and then, mostly halls and corridors of every size. */
    static Hall draw(Random random) {
        if (random.nextDouble() < 0.4D) {
            return OPEN;
        }
        return new Hall("drawn", 2.5D + random.nextDouble() * 13.5D, 2.5D + random.nextDouble() * 13.5D);
    }

    /** Whether one can stand at a point: within the walls, a hair off them. */
    boolean open(double x, double z) {
        return Math.abs(x) <= halfX - 0.3D && Math.abs(z) <= halfZ - 0.3D;
    }

    double clampX(double x) {
        return Math.max(-halfX + 0.3D, Math.min(halfX - 0.3D, x));
    }

    double clampZ(double z) {
        return Math.max(-halfZ + 0.3D, Math.min(halfZ - 0.3D, z));
    }
}
