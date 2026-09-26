package com.elderlexicon.mod.spell.sight;

/**
 * Illusions ({@code igni surgit vocant}): a function that works with the image of its source, its light and not its
 * matter ({@code docs/surgit-visao-design.md}, section 4). What it makes is seen and cannot be touched, and lasts as every
 * sight spell does. This part holds the rules that do not touch the world.
 */
public final class Illusion {

    /** An image is light, not matter: it costs this share of what the same function would cost with the matter. */
    public static final double COST_SHARE = 0.1D;
    /** Without chronos an image lasts the trance window, as every sight spell does: two seconds (book, chapter V). */
    public static final double DEFAULT_SECONDS = 2.0D;
    /** How far from the mage exsugat and impediunt reach the images of a source. */
    public static final double REACH = 5.0D;

    private Illusion() {
    }

    public static double seconds(Double chronos) {
        return chronos == null || chronos <= 0.0D ? DEFAULT_SECONDS : chronos;
    }

    public static int ticks(Double chronos) {
        return Math.max(1, (int) Math.round(seconds(chronos) * 20.0D));
    }

    /**
     * What an image of {@code energy} UMU of matter costs to keep for {@code seconds}: a tenth of the matter for every two
     * seconds it stands. A spell held longer is an open tap: the longer it runs, the more it spends.
     */
    public static double cost(double energy, double seconds) {
        return Math.max(0.0D, energy) * COST_SHARE * Math.max(0.0D, seconds) / DEFAULT_SECONDS;
    }
}
