package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.magic.matter.MatterLaws;
import com.elderlexicon.mod.magic.matter.State;
import com.elderlexicon.mod.vita.VitaElement;

/**
 * Vertere keeps the UMU (book 3: "a energia nunca pode ser criada e nunca pode ser destruída; ela apenas muda de
 * forma"): what is converted appears in as many units of the new element as its UMU buys, each unit worth what that
 * element holds in the world (a flame 1, a water source 3, a block of loose soil 0.5). What does not make a whole unit
 * is not lost.
 */
public final class Conversion {

    private Conversion() {
    }

    /** The UMU one unit of {@code element} holds when it appears in the world; 0 for air, which lays no blocks. */
    public static double unitOf(VitaElement element) {
        return switch (element) {
            case IGNI -> 1.0D;
            case AQUA -> Pressure.SOURCE;
            case FIRMO -> Density.SOIL;
            default -> 0.0D;
        };
    }

    /** How many whole units of {@code element} {@code umu} makes. */
    public static int units(double umu, VitaElement element) {
        double unit = unitOf(element);
        if (unit <= 0.0D || umu <= 0.0D) {
            return 0;
        }
        return (int) Math.floor(umu / unit + 1.0E-9D);
    }

    /** What is left of {@code umu} once {@code placed} units of {@code element} are made from it. */
    public static double leftover(double umu, VitaElement element, int placed) {
        return Math.max(0.0D, umu - placed * unitOf(element));
    }

    /** Converting takes this long, per rung crossed: half a second, and 0.02 s more for every UMU. */
    public static final int MIN_TICKS = 10;
    public static final double TICKS_PER_UMU = 0.4D;
    public static final int MAX_TICKS_PER_STEP = 100;

    /**
     * How many rungs of the ladder of states (docs/plano-materia-e-forca.md, L2) lie between {@code from} and
     * {@code to}: firmo is solid, aqua liquid, aura gas and igni plasma, so neighbours on the ladder are one apart and
     * firmo and igni three. Vis is off the ladder: coming out of it crosses none, going back into it the whole ladder.
     */
    public static int steps(VitaElement from, VitaElement to) {
        if (from == null || to == null || from == to) {
            return 0;
        }
        return MatterLaws.steps(State.of(from).orElse(null), State.of(to).orElse(null));
    }

    /** What the spirit's work of unmaking {@code umu} of one element and remaking it as another costs. */
    public static double work(double umu, VitaElement from, VitaElement to) {
        return workFor(umu, steps(from, to));
    }

    /**
     * The share of the energy being converted that the work takes out of it, for a chain that crossed {@code steps}
     * rungs: the spirit unmakes and remakes with the energy in hand, so the work is lost from it, not paid by the body.
     * Never all of it.
     */
    public static double workShare(int steps) {
        return MatterLaws.workShare(steps);
    }

    /** The work of a chain of conversions that crossed {@code steps} rungs in all. */
    public static double workFor(double umu, int steps) {
        return Math.max(0.0D, umu) * workShare(steps);
    }

    /** How long converting {@code umu} takes, in ticks: longer the more there is and the more rungs it crosses. */
    public static int ticks(double umu, VitaElement from, VitaElement to) {
        return ticksFor(umu, steps(from, to), from != to);
    }

    /**
     * How long a chain of conversions takes, in ticks: each rung crossed takes its time; a conversion that crosses
     * none (Vis into an element) still takes the shortest.
     */
    public static int ticksFor(double umu, int steps, boolean converted) {
        if (!converted || umu <= 0.0D) {
            return 0;
        }
        long perStep = Math.min(MAX_TICKS_PER_STEP, Math.round(MIN_TICKS + TICKS_PER_UMU * umu));
        return (int) Math.max(MIN_TICKS, perStep * Math.max(0, steps));
    }

    /** How long a whole chain of conversions takes ({@code chain} from the first element to the last), in ticks. */
    public static int chainTicks(double umu, java.util.List<VitaElement> chain) {
        int total = 0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            total += stepTicks(umu, chain.get(i), chain.get(i + 1));
        }
        return total;
    }

    /** How long one conversion of a chain takes: its own rungs' time, or the shortest when it crosses none. */
    public static int stepTicks(double umu, VitaElement from, VitaElement to) {
        return from == to ? 0 : ticksFor(umu, steps(from, to), true);
    }

    /** All the rungs a chain of conversions crosses, one conversion after another. */
    public static int chainSteps(java.util.List<VitaElement> chain) {
        int total = 0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            total += steps(chain.get(i), chain.get(i + 1));
        }
        return total;
    }

}
