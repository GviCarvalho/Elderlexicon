package com.elderlexicon.mod.spell;

import java.util.List;

/**
 * How much of the world an exsugat takes (book 8.2): whole sources, the nearest first, until what is needed is in hand.
 * A source is never taken in part, so the last one may bring more than was needed; nothing is wasted, the rest goes
 * into the mage's body ("não há desperdício, não há sobra", 4.3.2).
 */
public final class Capture {

    private static final double EPSILON = 1.0E-4D;

    private Capture() {
    }

    /** What was taken: how many sources, from the nearest, and how much they hold together. */
    public record Taken(int sources, double total) {

        /** What the spell did not need, for the body. */
        public double surplus(double needed) {
            return Math.max(0.0D, total - needed);
        }
    }

    /** Takes from {@code values} (the UMU of each source, nearest first) until {@code needed} is reached. */
    public static Taken take(List<Double> values, double needed) {
        int sources = 0;
        double total = 0.0D;
        for (double value : values) {
            if (total + EPSILON >= needed) {
                break;
            }
            sources++;
            total += value;
        }
        return new Taken(sources, total);
    }
}
