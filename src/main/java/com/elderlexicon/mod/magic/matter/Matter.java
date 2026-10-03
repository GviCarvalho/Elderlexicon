package com.elderlexicon.mod.magic.matter;

import java.util.Objects;
import java.util.Optional;

/**
 * A portion of matter: what it is made of, the state it is in and how much of it there is. What it is (its substance)
 * is not written on it: it is the natural thing whose code its composition is near ({@link MaterialTable#identify}), so
 * matter mixed into the right proportion becomes that thing. Matter near no code is still matter, formless, and behaves by
 * what it holds ({@link Qualities}).
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

    /** What natural thing it is, if its composition is near that thing's code; empty when it is near none. */
    public Optional<Substance> substance(MaterialTable table) {
        return table.identify(composition);
    }

    /** Whether it is near the code of no natural thing: a mixture with no name, formless in the world. */
    public boolean unnamed(MaterialTable table) {
        return substance(table).isEmpty();
    }

    /** Its particles: what it is made of, in the whole particles nearest how much of it there is. */
    public Particles particles() {
        return Particles.in(composition, Particles.ofUmu(umu));
    }

    public Matter withUmu(double amount) {
        return new Matter(composition, state, amount);
    }

    public Matter inState(State other) {
        return new Matter(composition, other, umu);
    }
}
