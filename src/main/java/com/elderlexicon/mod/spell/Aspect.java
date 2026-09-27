package com.elderlexicon.mod.spell;

import com.elderlexicon.mod.vita.VitaElement;

/**
 * The four aspects of energy (docs/interacoes-design.md): an element is not a substance but the aspect its energy
 * shows. Fire is energy as heat, water as cohesion (what water holds and gives back as it changes phase), air as
 * expansion (pressure), earth as mass (density). Vis is energy with all four in perfect balance (book 3.1): the center,
 * where none shows.
 * <p>
 * The aspects lie on two axes of opposites: the thermal one, heat against cohesion (heat boils water away, water
 * swallows heat), and the mechanical one, expansion against mass (pressure spreads, density gathers). Aspects on
 * different axes are neighbours and pass energy to each other directly (hot gas expands, hot rock melts, vapor presses,
 * wet soil turns to mud); opposites only meet by cancelling. That is the old map of the qualities, hot and cold, wet and
 * dry, seen as geometry: neighbours share a quality, opposites none.
 */
public enum Aspect {
    HEAT(Axis.THERMAL, VitaElement.IGNI),
    COHESION(Axis.THERMAL, VitaElement.AQUA),
    EXPANSION(Axis.MECHANICAL, VitaElement.AURA),
    MASS(Axis.MECHANICAL, VitaElement.FIRMO);

    public enum Axis {
        /** Heat against cohesion. */
        THERMAL,
        /** Expansion against mass. */
        MECHANICAL
    }

    public final Axis axis;
    public final VitaElement element;

    Aspect(Axis axis, VitaElement element) {
        this.axis = axis;
        this.element = element;
    }

    /** The other end of its axis. */
    public Aspect opposite() {
        return switch (this) {
            case HEAT -> COHESION;
            case COHESION -> HEAT;
            case EXPANSION -> MASS;
            case MASS -> EXPANSION;
        };
    }

    /** The aspect an element's energy shows, or null for Vis (the center, all four at once) and for no element. */
    public static Aspect of(VitaElement element) {
        if (element == null) {
            return null;
        }
        return switch (element) {
            case IGNI -> HEAT;
            case AQUA -> COHESION;
            case AURA -> EXPANSION;
            case FIRMO -> MASS;
            default -> null;
        };
    }

    /** Steps between two aspects: none to itself, one to a neighbour (the other axis), two to its opposite. */
    public static int distance(Aspect from, Aspect to) {
        if (from == to) {
            return 0;
        }
        return from.axis == to.axis ? 2 : 1;
    }

    /** The share of an element's energy that shows as {@code aspect}: all of its own, a quarter of each for Vis. */
    public static double share(VitaElement element, Aspect aspect) {
        if (element == VitaElement.BALANCED) {
            return 0.25D;
        }
        return of(element) == aspect ? 1.0D : 0.0D;
    }
}
