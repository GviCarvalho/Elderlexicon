package com.elderlexicon.mod.spell;

/**
 * How long a condensation takes to gather (docs/condensacao-design.md): the more energy is pressed into one point, the
 * longer the mage takes to bring it all together before it is released.
 */
public final class Charge {

    /** The shortest gathering, half a second. */
    public static final int MIN_TICKS = 10;
    /** The longest, five seconds. */
    public static final int MAX_TICKS = 100;
    /** Each UMU gathered adds this many ticks (0.04 s). */
    public static final double TICKS_PER_UMU = 0.8D;

    private Charge() {
    }

    public static int ticks(double energy) {
        if (energy <= 0.0D) {
            return 0;
        }
        return (int) Math.max(MIN_TICKS, Math.min(MAX_TICKS, Math.round(MIN_TICKS + TICKS_PER_UMU * energy)));
    }
}
