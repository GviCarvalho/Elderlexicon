package com.elderlexicon.mod.spell;

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

    /** The share of the UMU converted that each quality changed costs the spirit's work of unmaking and remaking it. */
    public static final double WORK_PER_QUALITY = 0.05D;
    /** Converting takes this long, per quality changed: half a second, and 0.02 s more for every UMU. */
    public static final int MIN_TICKS = 10;
    public static final double TICKS_PER_UMU = 0.4D;
    public static final int MAX_TICKS_PER_QUALITY = 100;

    /**
     * How many of the two qualities change between {@code from} and {@code to}: fire is hot and dry, air hot and wet,
     * water cold and wet, earth cold and dry. Neighbours (earth and fire, fire and air, air and water, water and earth)
     * change one; opposites (fire and water, earth and air) change both; an element into itself, none.
     */
    public static int qualities(VitaElement from, VitaElement to) {
        if (from == null || to == null || from == to) {
            return 0;
        }
        // Vis is the raw stuff, ready to become anything (book 3.1): it changes no quality. Making Vis means bringing
        // the four into balance, the hardest remaking there is, as dear as changing both.
        if (from == VitaElement.BALANCED) {
            return 0;
        }
        if (to == VitaElement.BALANCED) {
            return 2;
        }
        if (!isElement(from) || !isElement(to)) {
            return 0;
        }
        int changed = 0;
        if (hot(from) != hot(to)) {
            changed++;
        }
        if (dry(from) != dry(to)) {
            changed++;
        }
        return changed;
    }

    /** What the spirit's work of unmaking {@code umu} of one element and remaking it as another costs the body. */
    public static double work(double umu, VitaElement from, VitaElement to) {
        return workFor(umu, qualities(from, to));
    }

    /**
     * The share of the energy being converted that the work takes out of it, for a chain that changed {@code qualities}
     * qualities: the spirit unmakes and remakes with the energy in hand, so the work is lost from it, not paid by the
     * body. Never all of it.
     */
    public static double workShare(int qualities) {
        return Math.min(0.9D, WORK_PER_QUALITY * Math.max(0, qualities));
    }

    /** The work of a chain of conversions that changed {@code qualities} qualities in all. */
    public static double workFor(double umu, int qualities) {
        return Math.max(0.0D, umu) * WORK_PER_QUALITY * Math.max(0, qualities);
    }

    /** How long converting {@code umu} takes, in ticks: longer the more there is and the more qualities change. */
    public static int ticks(double umu, VitaElement from, VitaElement to) {
        return ticksFor(umu, qualities(from, to), from != to);
    }

    /**
     * How long a chain of conversions takes, in ticks: each quality changed takes its time; a conversion that changes
     * none (Vis into an element) still takes the shortest.
     */
    public static int ticksFor(double umu, int qualities, boolean converted) {
        if (!converted || umu <= 0.0D) {
            return 0;
        }
        long perQuality = Math.min(MAX_TICKS_PER_QUALITY, Math.round(MIN_TICKS + TICKS_PER_UMU * umu));
        return (int) Math.max(MIN_TICKS, perQuality * Math.max(0, qualities));
    }

    /** How long a whole chain of conversions takes ({@code chain} from the first element to the last), in ticks. */
    public static int chainTicks(double umu, java.util.List<VitaElement> chain) {
        int total = 0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            total += stepTicks(umu, chain.get(i), chain.get(i + 1));
        }
        return total;
    }

    /** How long one step of a chain takes: its own qualities' time, or the shortest when it changes none. */
    public static int stepTicks(double umu, VitaElement from, VitaElement to) {
        return from == to ? 0 : ticksFor(umu, qualities(from, to), true);
    }

    /** All the qualities a chain of conversions changes, step by step. */
    public static int chainQualities(java.util.List<VitaElement> chain) {
        int total = 0;
        for (int i = 0; i + 1 < chain.size(); i++) {
            total += qualities(chain.get(i), chain.get(i + 1));
        }
        return total;
    }

    private static boolean isElement(VitaElement element) {
        return element == VitaElement.IGNI || element == VitaElement.AQUA || element == VitaElement.FIRMO
                || element == VitaElement.AURA;
    }

    private static boolean hot(VitaElement element) {
        return element == VitaElement.IGNI || element == VitaElement.AURA;
    }

    private static boolean dry(VitaElement element) {
        return element == VitaElement.IGNI || element == VitaElement.FIRMO;
    }
}
