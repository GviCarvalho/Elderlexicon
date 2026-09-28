package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Locale;
import java.util.Optional;

/**
 * How matter is (docs/plano-materia-e-forca.md, section 1). The four aspects are the rungs of one ladder, from the densest
 * to the most rarefied: firmo is solid, aqua liquid, aura gas and igni plasma. Changing state is climbing or descending
 * it, one rung at a time (L2). Vis is off the ladder: energy that has no state yet.
 */
public enum State {
    SOLID(VitaElement.FIRMO),
    LIQUID(VitaElement.AQUA),
    GAS(VitaElement.AURA),
    PLASMA(VitaElement.IGNI);

    private final VitaElement element;

    State(VitaElement element) {
        this.element = element;
    }

    /** The aspect that names this state ({@code firmo} is solid). */
    public VitaElement element() {
        return element;
    }

    /** The state an aspect names, or empty for vis, which has none. */
    public static Optional<State> of(VitaElement element) {
        for (State state : values()) {
            if (state.element == element) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }

    /** How many rungs lie between this state and another: solid to plasma is three. */
    public int stepsTo(State other) {
        return Math.abs(ordinal() - other.ordinal());
    }

    /** Whether it flows: everything but a solid, which keeps its shape (and only fluids mix, L4). */
    public boolean fluid() {
        return this != SOLID;
    }

    /** A state by its name or by the aspect that names it ({@code solid} or {@code firmo}). */
    public static Optional<State> parse(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (State state : values()) {
            if (state.name().toLowerCase(Locale.ROOT).equals(normalized) || state.element.runeId().equals(normalized)) {
                return Optional.of(state);
            }
        }
        return Optional.empty();
    }
}
