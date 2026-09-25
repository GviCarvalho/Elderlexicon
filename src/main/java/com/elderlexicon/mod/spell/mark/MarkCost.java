package com.elderlexicon.mod.spell.mark;

/**
 * What it costs to act on marked things, and how fast a throw sends them. The numbers are
 * placeholders to be calibrated against {@code ReadmeUMU.md}.
 * <p>
 * Mass is the thing's maximum health for living beings, the block capacity used by Ligabis for blocks
 * (square root of the blast resistance, at least 1) and {@link #ITEM_MASS} for items on the ground.
 */
public final class MarkCost {

    /** UMU per unit of mass per block moved. A 20 HP player summoned 12 blocks costs 2.4 UMU. */
    public static final double MOVE_RATE = 0.01D;
    /** Vita is worth 5 UMU per health point ({@code ReadmeUMU.md}). */
    public static final double UMU_PER_HEALTH = 5.0D;
    public static final double ITEM_MASS = 0.5D;
    /** What a throw puts into the thing when no quantum says otherwise (book: Iactare casts 10 UMU). */
    public static final double DEFAULT_THROW_ENERGY = 10.0D;
    /** Blocks per tick for one unit of sqrt(energy / mass). */
    public static final double SPEED_SCALE = 2.0D;
    /** Blocks per tick for a thrown block; faster, a falling block tunnels through the world. */
    public static final double MAX_BLOCK_SPEED = 3.0D;
    /** Blocks per tick for a thrown entity: the network clamps each axis of a velocity to 3.9. */
    public static final double MAX_ENTITY_SPEED = 3.9D;
    /** A thrust never lasts longer than this, in ticks (5 seconds). */
    public static final int MAX_PUSH_TICKS = 100;
    private static final double MIN_MASS = 0.1D;
    private static final double TICKS_PER_SECOND = 20.0D;

    /**
     * How a thing is thrown: its speed and, when the energy is more than that top speed takes, how many ticks
     * the push keeps it at that speed (a thrust), and what it costs.
     */
    public record Throw(double speed, int pushTicks, double cost) { }

    private MarkCost() {
    }

    /** Cost of carrying {@code mass} over {@code distance} blocks. */
    public static double movement(double mass, double distance) {
        return MOVE_RATE * Math.max(0.0D, mass) * Math.max(0.0D, distance);
    }

    /**
     * Throws a thing of {@code mass} with {@code energy} UMU. The speed grows with sqrt(energy / mass) up to
     * {@code maxSpeed}; energy beyond what that speed takes becomes a thrust, one tick per such share. The cost
     * follows the mass x distance rule: the distance of the push plus one second of flight at that speed.
     */
    public static Throw throwWith(double energy, double mass, double maxSpeed) {
        return throwWith(energy, mass, maxSpeed, 0);
    }

    /**
     * A throw that lasts {@code durationTicks} (chronos, book 4.3.2: "slower, but more controlled"): the same
     * energy spread over the whole window, so the thing is pushed at a steady speed for that long, sqrt(energy /
     * (mass x ticks)) instead of sqrt(energy / mass). Zero or less is the ordinary throw.
     */
    public static Throw throwWith(double energy, double mass, double maxSpeed, int durationTicks) {
        if (durationTicks <= 1) {
            return burst(energy, mass, maxSpeed);
        }
        if (energy <= 0.0D) {
            return new Throw(0.0D, 0, 0.0D);
        }
        double weight = Math.max(MIN_MASS, mass);
        double speed = Math.min(maxSpeed, SPEED_SCALE * Math.sqrt(energy / (weight * durationTicks)));
        double cost = movement(mass, speed * (TICKS_PER_SECOND + durationTicks - 1));
        return new Throw(speed, durationTicks, cost);
    }

    private static Throw burst(double energy, double mass, double maxSpeed) {
        if (energy <= 0.0D) {
            return new Throw(0.0D, 0, 0.0D);
        }
        double weight = Math.max(MIN_MASS, mass);
        double free = SPEED_SCALE * Math.sqrt(energy / weight);
        double speed = Math.min(maxSpeed, free);
        int pushTicks = 1;
        if (free > maxSpeed) {
            double perTick = weight * Math.pow(maxSpeed / SPEED_SCALE, 2.0D);
            pushTicks = (int) Math.max(1L, Math.min(MAX_PUSH_TICKS, Math.round(energy / perTick)));
        }
        double cost = movement(mass, speed * (TICKS_PER_SECOND + pushTicks - 1));
        return new Throw(speed, pushTicks, cost);
    }

    public static double vitaOf(double health) {
        return Math.max(0.0D, health) * UMU_PER_HEALTH;
    }
}
