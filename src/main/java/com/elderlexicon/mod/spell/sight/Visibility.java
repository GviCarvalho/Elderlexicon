package com.elderlexicon.mod.spell.sight;

/**
 * Visibility ({@code surgit m1 quantum 0}): the sight of what bears a mark, from 0 (unseen) to 10 (fully seen), and
 * linear between them ({@code docs/surgit-visao-design.md}, section 5). This part holds the rules that do not touch the
 * world: the level asked, how opaque it looks, and what it costs to keep.
 */
public final class Visibility {

    public static final int HIDDEN = 0;
    public static final int SEEN = 10;
    /** Without chronos the change lasts the trance window, as every sight spell does: two seconds (book, chapter V). */
    public static final double DEFAULT_SECONDS = 2.0D;
    /** Hiding is bending light around a body: each unit of mass kept fully hidden costs this much per second. */
    public static final double UMU_PER_MASS_SECOND = 0.05D;

    private Visibility() {
    }

    /** The level written with quantum, rounded to a whole level and kept within 0 and 10. */
    public static int level(double quantum) {
        return (int) Math.max(HIDDEN, Math.min(SEEN, Math.round(quantum)));
    }

    /** How long the change lasts: what chronos asks, or the default. */
    public static double seconds(Double chronos) {
        return chronos == null || chronos <= 0.0D ? DEFAULT_SECONDS : chronos;
    }

    /** How opaque something at {@code level} looks, from 0 to 1. */
    public static float opacity(int level) {
        return Math.max(HIDDEN, Math.min(SEEN, level)) / (float) SEEN;
    }

    /** What keeping {@code mass} at {@code level} costs each second: nothing when fully seen, most when unseen. */
    public static double costPerSecond(double mass, int level) {
        double hidden = (SEEN - Math.max(HIDDEN, Math.min(SEEN, level))) / (double) SEEN;
        return UMU_PER_MASS_SECOND * Math.max(0.0D, mass) * hidden;
    }
}
