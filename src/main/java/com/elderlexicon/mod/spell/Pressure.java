package com.elderlexicon.mod.spell;

/**
 * The pressure of water, its intensity (docs/condensacao-design.md): how many litres are pressed into one point, in
 * UMU (a still water source holds 3). Pressed water wants to spread: released it is a jet, then a jet that cuts, and
 * pressed hard enough it becomes ice VII, a solid that exists only while the pressure is held and bursts back into all
 * of its water when it is not.
 */
public final class Pressure {

    /** A still water source: the common water. */
    public static final double SOURCE = 3.0D;
    /** From here the jet cuts soft ground away. */
    public static final double CUTTING = 10.0D;
    /** From here the water is pressed into ice. */
    public static final double ICE = 30.0D;

    private Pressure() {
    }

    public enum Band {
        /** Pushes creatures and puts fire out. */
        JET,
        /** Also cuts soft ground away: soil, sand, gravel, plants. */
        CUTTING,
        /** Ice VII, held only while the pressure is. */
        ICE
    }

    public static Band band(double pressure) {
        if (pressure >= ICE) {
            return Band.ICE;
        }
        if (pressure >= CUTTING) {
            return Band.CUTTING;
        }
        return Band.JET;
    }

    /** How much water is released when the pressure goes: one still source for every 3 UMU (the litres are kept). */
    public static int sources(double pressure) {
        return (int) Math.max(1L, Math.round(pressure / SOURCE));
    }

    /** How far a burst throws things back, in blocks. */
    public static double burstReach(double pressure) {
        if (pressure <= 196.0D) {
            return Math.max(2.0D, 1.0D + Math.sqrt(pressure) / 2.0D);
        }
        return 8.0D + 3.0D * Math.log10(pressure / 196.0D);
    }

    /** How hard a burst or a jet throws what it hits, in blocks per tick (never past what the network carries). */
    public static double push(double pressure) {
        return Math.max(0.6D, Math.min(3.5D, pressure / 10.0D));
    }

    /** How far a jet reaches, in blocks: twenty, and farther the harder it is pressed. */
    public static double jetReach(double pressure) {
        return 20.0D + 5.0D * Math.log10(Math.max(1.0D, pressure / CUTTING));
    }

    /** How many soft blocks a cutting jet washes away along its path. */
    public static int cuts(double pressure) {
        return pressure < CUTTING ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round(pressure / SOURCE));
    }

    /**
     * How hard ice VII shatters its surroundings when its pressure goes, as an explosion's strength: 0 below the
     * pressure that makes ice (water that never froze only pushes and floods), then growing slowly, up to 6.
     */
    public static float shatter(double pressure) {
        if (pressure < ICE) {
            return 0.0F;
        }
        if (pressure <= 1484.0D) {
            return (float) (1.0D + Math.log(pressure / CUTTING));
        }
        return (float) (6.0D + 2.0D * Math.log10(pressure / 1484.0D));
    }
}
