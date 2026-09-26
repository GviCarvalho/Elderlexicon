package com.elderlexicon.mod.spell;

/**
 * A spell held longer is an open tap: what quantum asks (or the default) is what flows out in the default window of
 * two seconds (book 4.3.2, "o tempo de evocação padrão (2 segundos)"), and chronos is how long the tap stays open. Five
 * seconds of iactare flow as strongly as two, and spend two and a half times as much. A window shorter than the default
 * is a burst, the same energy let out at once (chronos 0 "releases it at once"), so only lengthening costs more.
 */
public final class SpellFlow {

    /** The default window of an evocation: two seconds. */
    public static final int WINDOW_TICKS = 40;

    private SpellFlow() {
    }

    /** How many default windows a spell held for {@code durationTicks} runs: 1 for the default or shorter. */
    public static double windows(int durationTicks) {
        return Math.max(1.0D, durationTicks / (double) WINDOW_TICKS);
    }

    /** What flows out in all of {@code durationTicks} when {@code perWindow} flows out every default window. */
    public static double total(double perWindow, int durationTicks) {
        return perWindow * windows(durationTicks);
    }
}
