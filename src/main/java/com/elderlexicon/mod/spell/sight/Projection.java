package com.elderlexicon.mod.spell.sight;

/**
 * Astral projection ({@code 10 ubis surgit}, {@code m1 ubis surgit}): the spirit leaves the body and looks from the place
 * written with ubis, turning and drifting as the mage wills, while the body stands in trance, defenceless
 * ({@code docs/surgit-visao-design.md}, section 3). It lasts as every sight spell and costs by how far the spirit goes
 * and for how long: an open tap.
 */
public final class Projection {

    /** Without chronos the spirit is out for the trance window: two seconds (book, chapter V). */
    public static final double DEFAULT_SECONDS = 2.0D;
    /** Each block between body and spirit, held for a second, costs this much. */
    public static final double UMU_PER_BLOCK_SECOND = 0.05D;
    /** Even a spirit that barely leaves the body pays as if it went this far. */
    public static final double MIN_DISTANCE = 4.0D;
    /** How far the spirit may drift from where it appeared. */
    public static final double LEASH = 16.0D;

    private Projection() {
    }

    public static double seconds(Double chronos) {
        return chronos == null || chronos <= 0.0D ? DEFAULT_SECONDS : chronos;
    }

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    /** What sending the spirit {@code distance} blocks away for {@code seconds} costs; drifting is paid in advance. */
    public static double cost(double distance, double seconds) {
        double far = Math.max(MIN_DISTANCE, distance) + LEASH;
        return UMU_PER_BLOCK_SECOND * far * Math.max(0.0D, seconds);
    }
}
