package com.elderlexicon.mod.mind;

/**
 * How much room a caster has to give ground (docs/feiticeiro-design.md, 3.12): it looks away from its foe, and a little
 * to each side, and to each side outright, and sees how far it could walk each way before a wall, a drop or fire stops
 * it. It backs away the way with the most room, rather than into the corner straight behind it; and one with no room
 * any way knows it is cornered before it backs into the wall.
 * <p>
 * The same in the world (blocks) and in the arena (walls), so a doctrine learned there sees here what it saw there.
 */
public final class Room {

    /** How far it looks each way, a block a step. */
    public static final double SIGHT = 12.0D;
    /** Below this much room every way, it is cornered. */
    public static final double CORNERED = 1.5D;
    /** The ways it looks, in degrees from straight away from the foe: straight away first. */
    private static final double[] WAYS = {0.0D, 45.0D, -45.0D, 90.0D, -90.0D};
    /** How much a block of room straight away is worth over one to the side (it gains less distance sideways). */
    private static final double AWAY = 2.0D;

    /** Whether a caster can stand at a point. */
    @FunctionalInterface
    public interface Ground {
        boolean open(double x, double z);
    }

    /** The way it would back off: where it goes (a unit step), and how much room there is that way. */
    public record Way(double dx, double dz, double room) {
    }

    private Room() {
    }

    /**
     * The way to walk around a foe at ({@code fx}, {@code fz}), standing at ({@code x}, {@code z}): square to the line
     * between them, on the side {@code side} (1 or -1) while it has room there, else on the other, if it has more there
     * (docs/feiticeiro-design.md, 3.16).
     */
    public static Way aside(Ground ground, double x, double z, double fx, double fz, int side) {
        double ax = x - fx;
        double az = z - fz;
        double length = Math.hypot(ax, az);
        if (length < 1.0E-6D) {
            ax = 1.0D;
            az = 0.0D;
        } else {
            ax /= length;
            az /= length;
        }
        double dx = -az * side;
        double dz = ax * side;
        double room = free(ground, x, z, dx, dz);
        if (room >= CORNERED) {
            return new Way(dx, dz, room);
        }
        double other = free(ground, x, z, -dx, -dz);
        return other > room ? new Way(-dx, -dz, other) : new Way(dx, dz, room);
    }

    /** How far it could walk from ({@code x}, {@code z}) along ({@code dx}, {@code dz}), a unit step, up to {@link #SIGHT}. */
    public static double free(Ground ground, double x, double z, double dx, double dz) {
        double walked = 0.0D;
        while (walked < SIGHT && ground.open(x + dx * (walked + 1.0D), z + dz * (walked + 1.0D))) {
            walked += 1.0D;
        }
        return walked;
    }

    /**
     * The best way to back off from a foe at ({@code fx}, {@code fz}), standing at ({@code x}, {@code z}): the one with
     * the most room, straight away weighing a little more.
     */
    public static Way away(Ground ground, double x, double z, double fx, double fz) {
        double ax = x - fx;
        double az = z - fz;
        double length = Math.hypot(ax, az);
        if (length < 1.0E-6D) {
            ax = 1.0D;
            az = 0.0D;
        } else {
            ax /= length;
            az /= length;
        }
        Way best = null;
        double bestWorth = -Double.MAX_VALUE;
        for (double degrees : WAYS) {
            double angle = Math.toRadians(degrees);
            double dx = ax * Math.cos(angle) - az * Math.sin(angle);
            double dz = ax * Math.sin(angle) + az * Math.cos(angle);
            double room = free(ground, x, z, dx, dz);
            double worth = room + AWAY * Math.cos(angle);
            if (worth > bestWorth) {
                bestWorth = worth;
                best = new Way(dx, dz, room);
            }
        }
        return best;
    }
}
