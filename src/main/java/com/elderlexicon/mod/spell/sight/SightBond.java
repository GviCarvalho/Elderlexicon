package com.elderlexicon.mod.spell.sight;

/**
 * The bond of sight ({@code surgit m1 ligabis}): the mage's sight is bound to what bears the mark, and sees what it sees,
 * where it looks, without turning its head for it ({@code docs/surgit-visao-design.md}, section 2). The mage's own body
 * stands where it was, blind to what is around it. It lasts as every sight spell and is paid as an open tap.
 */
public final class SightBond {

    /** Without chronos the bond lasts the trance window: two seconds (book, chapter V). */
    public static final double DEFAULT_SECONDS = 2.0D;
    /** Seeing through other eyes costs this much for each second it lasts. */
    public static final double UMU_PER_SECOND = 0.5D;

    private SightBond() {
    }

    public static double seconds(Double chronos) {
        return chronos == null || chronos <= 0.0D ? DEFAULT_SECONDS : chronos;
    }

    public static int ticks(double seconds) {
        return Math.max(1, (int) Math.round(seconds * 20.0D));
    }

    public static double cost(double seconds) {
        return UMU_PER_SECOND * Math.max(0.0D, seconds);
    }
}
