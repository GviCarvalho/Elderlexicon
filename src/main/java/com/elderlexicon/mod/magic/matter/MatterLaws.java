package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The laws of matter (docs/plano-materia-e-forca.md, section 1). They hold for every substance, the book's and an
 * addon's alike; none is written for one in particular.
 * <ol>
 *   <li><b>Conservation.</b> No law makes or destroys UMU: matter is moved, turned, mixed or split, and what goes in
 *       comes out.</li>
 *   <li><b>State.</b> Changing state climbs or descends the ladder and keeps what the matter is and how much of it there
 *       is. The spirit's work is paid by the one who changes it, a share of the matter for each rung; coming out of vis
 *       costs nothing, going back into it costs the whole ladder.</li>
 *   <li><b>Body.</b> The body keeps energy, not matter: what it takes in loses its substance and enters the Vita as the
 *       aspect of the state it was in; what leaves it with no recipe is the primordial of its state.</li>
 *   <li><b>Mixing.</b> Fluid portions in one place mix into one, in the proportion of their UMU; what that proportion
 *       matches is what they become. Solids do not mix (a bond joins them).</li>
 *   <li><b>Amalgam.</b> A mixture that matches no recipe falls back apart into its primordials.</li>
 * </ol>
 */
public final class MatterLaws {

    /** The spirit's work for each rung climbed or descended: this share of the matter changed. */
    public static final double WORK_PER_STEP = 0.05D;
    /** Unmaking matter back into vis is as dear as crossing the whole ladder. */
    public static final int STEPS_INTO_VIS = State.values().length - 1;
    /** The work is never all of it. */
    private static final double MAX_WORK_SHARE = 0.9D;

    private MatterLaws() {
    }

    // ------------------------------------------------------------------ L2: the ladder

    /**
     * How many rungs a change of state crosses; a null state is vis, off the ladder: out of it costs none, into it the
     * whole ladder.
     */
    public static int steps(State from, State to) {
        if (from == null) {
            return 0;
        }
        if (to == null) {
            return STEPS_INTO_VIS;
        }
        return from.stepsTo(to);
    }

    /** The share of the matter changed that the spirit's work costs, for so many rungs. */
    public static double workShare(int steps) {
        return Math.min(MAX_WORK_SHARE, WORK_PER_STEP * Math.max(0, steps));
    }

    /** Matter in another state, and the work that change costs whoever makes it. */
    public record Change(Matter matter, double work) {
    }

    /** L2: the same matter, as much of it, in another state; the work is paid apart (L1). */
    public static Change changeState(Matter matter, State to) {
        int steps = steps(matter.state(), to);
        return new Change(matter.inState(to), matter.umu() * workShare(steps));
    }

    // ------------------------------------------------------------------ L3: the body

    /** Energy in the Vita: which aspect, and how much. */
    public record Energy(VitaElement aspect, double umu) {
    }

    /** L3: matter taken into the body loses what it was and enters the Vita as the aspect of its state. */
    public static Energy intoBody(Matter matter) {
        return new Energy(matter.state().element(), matter.umu());
    }

    /** L3: energy leaving the body with no recipe: the primordial of the state it leaves as. */
    public static Matter fromBody(MaterialTable table, State state, double umu) {
        return Matter.of(table.primordial(state.element()), state, umu);
    }

    /**
     * L3: what the Vita gives to make {@code umu} of a recipe the mage knows: each primordial's share, from the aspect
     * that is that primordial.
     */
    public static Map<VitaElement, Double> drawnFromBody(Composition recipe, double umu) {
        return recipe.split(umu);
    }

    // ------------------------------------------------------------------ L4: mixing

    /**
     * L4: fluid portions in one place become one: all their UMU, in the proportion of what each holds, in the state most
     * of it is in (the denser one on a tie). Empty when there is nothing to mix or a portion is solid.
     */
    public static Optional<Matter> mix(List<Matter> portions) {
        if (portions == null || portions.isEmpty()) {
            return Optional.empty();
        }
        Map<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        Map<State, Double> byState = new EnumMap<>(State.class);
        double total = 0.0D;
        for (Matter portion : portions) {
            if (!portion.state().fluid()) {
                return Optional.empty();
            }
            if (portion.umu() <= 0.0D) {
                continue;
            }
            portion.composition().split(portion.umu()).forEach((aspect, amount) -> amounts.merge(aspect, amount, Double::sum));
            byState.merge(portion.state(), portion.umu(), Double::sum);
            total += portion.umu();
        }
        if (total <= 0.0D) {
            return Optional.empty();
        }
        State state = null;
        for (State candidate : State.values()) {
            double held = byState.getOrDefault(candidate, 0.0D);
            if (held > 0.0D && (state == null || held > byState.get(state))) {
                state = candidate;
            }
        }
        return Optional.of(new Matter(Composition.of(amounts), state, total));
    }

    // ------------------------------------------------------------------ L5: the amalgam

    /**
     * L5: what matter is once it settles. A substance stays as it is; an amalgam falls back apart into its primordials,
     * each in the state it is found in, with all its UMU.
     */
    public static List<Matter> decay(MaterialTable table, Matter matter) {
        if (!matter.amalgam(table)) {
            return List.of(matter);
        }
        List<Matter> parts = new ArrayList<>();
        matter.composition().split(matter.umu()).forEach((aspect, umu) ->
                parts.add(Matter.natural(table.primordial(aspect), umu)));
        return parts;
    }
}
