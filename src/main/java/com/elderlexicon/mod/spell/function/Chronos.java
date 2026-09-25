package com.elderlexicon.mod.spell.function;

import com.elderlexicon.mod.spell.action.SpellAction;

/**
 * Chronos (book 4.3.2) "governs time, working in seconds to change the default duration of the effects or
 * the instant they manifest". Functions that last take it as their duration: the Iactare channel, a throw, pull or
 * push of a marked thing (it is pushed for that long), what Vocant brings. Those with nothing to stretch (a
 * teleport, a swap, a conversion, a renaming) take it as the moment they happen.
 */
final class Chronos {

    private static final int TICKS_PER_SECOND = 20;
    /** Longest window a written chronos may ask for, in ticks (5 minutes). */
    private static final int MAX_TICKS = 5 * 60 * TICKS_PER_SECOND;

    private Chronos() {
    }

    /** The window written with chronos, in ticks, or 0 when there is none (so the function keeps its own). */
    static int window(SpellAction action) {
        return ticks(action, 0);
    }

    /** The window written with chronos, in ticks, or {@code defaultTicks} when there is none. */
    static int ticks(SpellAction action, int defaultTicks) {
        if (action == null || action.seconds().isEmpty()) {
            return defaultTicks;
        }
        long ticks = Math.round(action.seconds().getAsDouble() * TICKS_PER_SECOND);
        return (int) Math.max(0L, Math.min(MAX_TICKS, ticks));
    }
}
