package com.elderlexicon.mod.magic.matter;

import java.util.Objects;
import java.util.Optional;

/**
 * A portion of matter: what it is made of, the state it is in and how much of it there is. What it is (its substance)
 * is not written on it: it is the recipe its composition matches ({@link MaterialTable#identify}), so matter mixed into
 * the right proportion becomes that substance, and matter that matches none is an amalgam (L5).
 *
 * @param composition what it is made of
 * @param state       how it is
 * @param umu         how much of it there is
 */
public record Matter(Composition composition, State state, double umu) {

    public Matter {
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(state, "state");
        umu = Math.max(0.0D, umu);
    }

    /** {@code umu} of a substance, in a state. */
    public static Matter of(Substance substance, State state, double umu) {
        return new Matter(substance.recipe(), state, umu);
    }

    /** {@code umu} of a substance, in the state it is found in. */
    public static Matter natural(Substance substance, double umu) {
        return of(substance, substance.nature(), umu);
    }

    /** What it is, if its composition matches a recipe; empty for an amalgam. */
    public Optional<Substance> substance(MaterialTable table) {
        return table.identify(composition);
    }

    /** Whether it matches no recipe: an amalgam, which falls back apart with time (L5). */
    public boolean amalgam(MaterialTable table) {
        return substance(table).isEmpty();
    }

    public Matter withUmu(double amount) {
        return new Matter(composition, state, amount);
    }

    public Matter inState(State other) {
        return new Matter(composition, other, umu);
    }
}
