package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Every substance there is and how each shows in the game ({@code data/elderlexicon/lexicon/materials.json}): the four
 * primordials, the ones the book teaches (its fusions) and the ones a mage finds by trying (every natural thing has a
 * recipe). It is immutable; {@link MaterialTableBuilder} builds it and {@link Materials} holds the one in force.
 */
public final class MaterialTable {

    /** How far a mixture may be from a recipe and still be that substance: five points of each share. */
    public static final double TOLERANCE = 0.05D;

    private final Map<String, Substance> substances;
    private final Map<VitaElement, Substance> primordials;
    private final Map<String, Reading> readings;

    /** What a block or item in the game is, read as matter. */
    public record Reading(Substance substance, State state, double umu) {

        public Matter matter() {
            return Matter.of(substance, state, umu);
        }
    }

    MaterialTable(Map<String, Substance> substances) {
        this.substances = Collections.unmodifiableMap(new LinkedHashMap<>(substances));
        EnumMap<VitaElement, Substance> firsts = new EnumMap<>(VitaElement.class);
        Map<String, Reading> read = new LinkedHashMap<>();
        for (Substance substance : substances.values()) {
            substance.recipe().pureAspect().ifPresent(aspect -> firsts.putIfAbsent(aspect, substance));
            substance.forms().forEach((state, forms) -> {
                for (Form form : forms) {
                    if (form.holdsMatter()) {
                        read.putIfAbsent(key(form.kind(), form.id()), new Reading(substance, state, form.umu()));
                    }
                }
            });
        }
        this.primordials = Collections.unmodifiableMap(firsts);
        this.readings = Collections.unmodifiableMap(read);
    }

    public Collection<Substance> substances() {
        return substances.values();
    }

    public Optional<Substance> substance(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(substances.get(id.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    /** The primordial substance of an aspect: terra for firmo, água for aqua, ar for aura, fogo for igni. */
    public Substance primordial(VitaElement aspect) {
        Substance found = primordials.get(aspect);
        if (found == null) {
            throw new IllegalArgumentException("no primordial substance of " + aspect.runeId());
        }
        return found;
    }

    /**
     * The substance a composition is: the recipe nearest to it, if within {@link #TOLERANCE} of every share; empty when
     * none is (an amalgam).
     */
    public Optional<Substance> identify(Composition composition) {
        Substance nearest = null;
        double best = Double.MAX_VALUE;
        for (Substance substance : substances.values()) {
            double distance = substance.recipe().distance(composition);
            if (distance <= TOLERANCE + 1.0E-9D && distance < best) {
                nearest = substance;
                best = distance;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * How a substance shows in a state: as the data says or, when it says nothing for that state, as the nearest state
     * that has something (the denser one on a tie). A gas or a plasma shown by a block or an item is shown as its
     * particles, since what floats is neither.
     */
    public Optional<Form> form(Substance substance, State state) {
        List<Form> exact = substance.declared(state);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        State[] states = State.values();
        for (int distance = 1; distance < states.length; distance++) {
            for (int sign : new int[] {-1, 1}) {
                int at = state.ordinal() + sign * distance;
                if (at < 0 || at >= states.length) {
                    continue;
                }
                List<Form> near = substance.declared(states[at]);
                if (!near.isEmpty()) {
                    Form form = near.get(0);
                    if (state.fluid() && state != State.LIQUID && form.holdsMatter()) {
                        String particles = (form.kind() == Form.Kind.BLOCK ? "block:" : "item:") + form.id();
                        return Optional.of(new Form(Form.Kind.PARTICLE, particles, 0.0D));
                    }
                    return Optional.of(form);
                }
            }
        }
        return Optional.empty();
    }

    /** What a block or an item of the game is, as matter; empty for what is no natural matter (a chest, a sword). */
    public Optional<Reading> read(Form.Kind kind, String id) {
        if (kind == null || id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(readings.get(key(kind, id.trim().toLowerCase(java.util.Locale.ROOT))));
    }

    private static String key(Form.Kind kind, String id) {
        return kind.name() + ":" + id;
    }
}
