package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.StringJoiner;

/**
 * A portion of matter: what it is made of, the state it is in and how much of it there is. What it is (its substance)
 * is not written on it: it is the natural thing whose code its composition is near ({@link MaterialTable#identify}), so
 * matter mixed into the right proportion becomes that thing. Matter near no code is still matter, formless, and does
 * what its particles do (docs/particulas-design.md).
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

    /**
     * Exactly these particles, in a state: what is owed is no part of it. Its {@link #particles()} are these again, to
     * the last one.
     *
     * @throws IllegalArgumentException when they hold nothing
     */
    public static Matter of(Particles particles, State state) {
        Particles held = particles.present();
        Composition composition = held.composition()
                .orElseThrow(() -> new IllegalArgumentException("no matter in particles that hold nothing"));
        return new Matter(composition, state, held.umu());
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

    /**
     * How the spirit tells what it is: its state and what it is made of, the most first ("líquida (terra 70%, fogo
     * 30%)").
     */
    public String describe() {
        String how = switch (state) {
            case SOLID -> "sólida";
            case LIQUID -> "líquida";
            case GAS -> "gasosa";
            case PLASMA -> "em plasma";
        };
        StringJoiner parts = new StringJoiner(", ", " (", ")");
        composition.shares().entrySet().stream()
                .filter(share -> share.getValue() >= 0.005D)
                .sorted(Comparator.comparing(Map.Entry<VitaElement, Double>::getValue).reversed())
                .forEach(share -> parts.add(nameOf(share.getKey()) + " "
                        + String.format(Locale.ROOT, "%d%%", Math.round(share.getValue() * 100.0D))));
        return how + parts;
    }

    private static String nameOf(VitaElement aspect) {
        return switch (aspect) {
            case FIRMO -> "terra";
            case AQUA -> "água";
            case AURA -> "ar";
            case IGNI -> "fogo";
            default -> aspect.runeId();
        };
    }
}
