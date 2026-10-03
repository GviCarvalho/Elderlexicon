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
 * primordials and the ones a mage finds by trying (every natural thing has a recipe). The beings are in it too, each with the proportion of its body (docs/particulas-design.md, section 6): one
 * table for what there is, which an anchor of vis reads as a being. It is immutable; {@link MaterialTableBuilder}
 * builds it and {@link Materials} holds the one in force.
 */
public final class MaterialTable {

    /** How far a mixture may be from a recipe and still be that substance: five points of each share. */
    public static final double TOLERANCE = 0.05D;
    /** Distances closer than this are equal: a body as near two beings is the first, whatever rounding says. */
    private static final double TIE = 1.0E-12D;

    private final Map<String, Substance> substances;
    private final Map<VitaElement, Substance> primordials;
    private final Map<String, Reading> readings;
    private final Map<String, Being> beings;
    private final Map<String, Being> shownBy;

    /** What a block or item in the game is, read as matter. */
    public record Reading(Substance substance, State state, double umu) {

        public Matter matter() {
            return Matter.of(substance, state, umu);
        }
    }

    MaterialTable(Map<String, Substance> substances, Map<String, Being> beings) {
        this.substances = Collections.unmodifiableMap(new LinkedHashMap<>(substances));
        this.beings = Collections.unmodifiableMap(new LinkedHashMap<>(beings));
        Map<String, Being> shown = new LinkedHashMap<>();
        beings.values().forEach(being -> shown.putIfAbsent(being.entity(), being));
        this.shownBy = Collections.unmodifiableMap(shown);
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

    /** Every kind of being, in the order the data gives them. */
    public Collection<Being> beings() {
        return beings.values();
    }

    public Optional<Being> being(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(beings.get(id.trim().toLowerCase(java.util.Locale.ROOT)));
    }

    /** The kind of being a creature of the game shows ({@code minecraft:cow} is a cow); empty for one the table lacks. */
    public Optional<Being> beingShownBy(String entity) {
        return entity == null ? Optional.empty()
                : Optional.ofNullable(shownBy.get(entity.trim().toLowerCase(java.util.Locale.ROOT)));
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
     * none is (a mixture with no name).
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

    /** The substance some particles are, by what they hold; empty when they are near no recipe or hold nothing. */
    public Optional<Substance> identify(Particles particles) {
        return particles.composition().flatMap(this::identify);
    }

    /**
     * What some particles are (docs/particulas-design.md, section 6). The anchor chooses the part of the table: without
     * one they are the thing whose code is nearest, or formless matter when near none; with one they are the being whose
     * proportion is nearest, the least sum of differences of the shares (docs/vita-design.md), and on a tie the one the
     * table lists first, never the one rounding happens to favour. A table with no beings has nothing for an anchor to
     * hold, and reads the particles as matter. Only what they hold counts, never what they owe.
     */
    public Identity identify(Particles particles, boolean anchored) {
        Optional<Composition> held = particles.composition();
        if (held.isEmpty()) {
            return new Identity.Nothing();
        }
        Composition composition = held.get();
        if (anchored && !beings.isEmpty()) {
            return kin(composition);
        }
        return identify(composition).<Identity>map(Identity.Thing::new)
                .orElseGet(() -> new Identity.Formless(composition));
    }

    private Identity.Creature kin(Composition body) {
        Being nearest = null;
        double best = Double.MAX_VALUE;
        for (Being being : beings.values()) {
            double distance = 0.0D;
            for (VitaElement aspect : Particles.ASPECTS) {
                distance += Math.abs(body.share(aspect) - being.recipe().share(aspect));
            }
            if (distance < best - TIE) {
                best = distance;
                nearest = being;
            }
        }
        Map<VitaElement, Double> deviation = new EnumMap<>(VitaElement.class);
        for (VitaElement aspect : Particles.ASPECTS) {
            deviation.put(aspect, body.share(aspect) - nearest.recipe().share(aspect));
        }
        return new Identity.Creature(nearest, deviation, best);
    }

    /**
     * How a substance shows in a state, as the data says. A gas or a plasma the data is silent on shows as the nearest
     * state that has something (the denser one on a tie), as its particles when that is a block or an item, since what
     * floats is neither. A solid or a liquid the data is silent on has no look: it is formless matter, which the world
     * shows as such ({@link Placement}).
     */
    public Optional<Form> form(Substance substance, State state) {
        List<Form> exact = substance.declared(state);
        if (!exact.isEmpty()) {
            return Optional.of(exact.get(0));
        }
        if (!floats(state)) {
            return Optional.empty();
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
                    if (form.holdsMatter()) {
                        String particles = (form.kind() == Form.Kind.BLOCK ? "block:" : "item:") + form.id();
                        return Optional.of(new Form(Form.Kind.PARTICLE, particles, 0L));
                    }
                    return Optional.of(form);
                }
            }
        }
        return Optional.empty();
    }

    /** Whether matter in this state floats off into the world (a gas, a plasma) rather than taking room in it. */
    public static boolean floats(State state) {
        return state == State.GAS || state == State.PLASMA;
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
