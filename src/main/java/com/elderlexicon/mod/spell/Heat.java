package com.elderlexicon.mod.spell;

/**
 * The heat of fire, its intensity (docs/condensacao-design.md): how hot each flame is. Common fire is 1. Heat is never
 * written; it is won by capturing a lot of fire and releasing it at once ({@code igni exsugat quantum chronos 0
 * iactare}), so all of it goes into one strike and the strike is as hot as the fire that went into it.
 */
public final class Heat {

    /** Common fire, what the body gives and what a flame in the world holds. */
    public static final double COMMON = 1.0D;
    /** How long a condensed heat takes to cool down, in ticks (the time constant of its cooling). */
    public static final double COOLING_TICKS = 60.0D;
    /** Below this the heat is back to common fire and the hot spot is gone. */
    public static final double SPENT = 1.2D;
    /** The share of the condensed energy the spirit's work of condensing costs, at the highest heats. */
    public static final double WORK_SHARE = 0.1D;

    private Heat() {
    }

    /** What a fire of a given heat can do, from the common flame to plasma. */
    public enum Band {
        /** Burns what burns. */
        COMMON,
        /** White and bluish: melts snow and ice, cooks what lies on the ground, turns sand into glass. */
        WHITE,
        /** Melts stone into lava and boils water away at once. */
        MELTING,
        /** Plasma, the stuff of lightning and stars: bursts when it strikes. */
        PLASMA
    }

    public static Band band(double heat) {
        if (heat >= 30.0D) {
            return Band.PLASMA;
        }
        if (heat >= 10.0D) {
            return Band.MELTING;
        }
        if (heat >= 3.0D) {
            return Band.WHITE;
        }
        return Band.COMMON;
    }

    /**
     * How hot fire is released: all of what was captured in one instant is as hot as all of it together; spread over
     * its own flames it keeps the heat they had on average (a lava pool's fire stays hotter than a torch's).
     */
    public static double of(double captured, int sources, boolean atOnce) {
        if (captured <= 0.0D) {
            return COMMON;
        }
        double heat = atOnce ? captured : captured / Math.max(1, sources);
        return Math.max(COMMON, heat);
    }

    /** How far around it a heat reaches, in blocks: from one block for a common flame to five for the hottest. */
    public static double reach(double heat) {
        if (heat <= 100.0D) {
            return Math.max(1.0D, 0.5D * Math.sqrt(heat));
        }
        // Past a hundred it keeps growing, slowly: ten times hotter, two blocks farther.
        return 5.0D + 2.0D * Math.log10(heat / 100.0D);
    }

    /**
     * What the spirit's work of condensing costs: nothing when nothing is condensed, and up to a tenth of the energy as
     * the heat climbs (condensing goes against the way heat spreads).
     */
    public static double work(double energy, double heat) {
        if (energy <= 0.0D || heat <= COMMON) {
            return 0.0D;
        }
        return WORK_SHARE * energy * (1.0D - COMMON / heat);
    }

    /** How strong the burst of plasma is where it strikes: a twelfth of its heat, then growing slowly past 60. */
    public static float burst(double heat) {
        if (heat <= 60.0D) {
            return (float) (heat / 12.0D);
        }
        return (float) (5.0D + 2.0D * Math.log10(heat / 60.0D));
    }

    /** The heat left {@code ticks} after it was condensed: it cools toward common fire, fast at first. */
    public static double cooled(double heat, double ticks) {
        if (heat <= COMMON || ticks <= 0.0D) {
            return Math.max(COMMON, heat);
        }
        return COMMON + (heat - COMMON) * Math.exp(-ticks / COOLING_TICKS);
    }
}
