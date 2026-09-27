package com.elderlexicon.mod.spell;

/**
 * Air, captured and pressed (docs/condensacao-design.md). Pulling air out of the world leaves a vacuum where it was;
 * pressed into one point it is pressure, released as a breeze, a gust, a gale that tears light things away, and past
 * that a bomb: the pressure going all at once, an explosion with no fire.
 */
public final class AirPressure {

    /** What one block of air gives when it is pulled out (a draft value, to be tuned by playing). */
    public static final double UMU_PER_AIR = 0.5D;
    /** How long the vacuum holds before the air rushes back, in ticks: the default window of two seconds. */
    public static final int VACUUM_TICKS = 40;
    /** How much breath a creature loses each tick in a vacuum, beyond what it would breathe back. */
    public static final int BREATH_LOST = 8;

    public static final double GUST = 3.0D;
    public static final double GALE = 10.0D;
    public static final double BOMB = 30.0D;

    private AirPressure() {
    }

    public enum Band {
        /** Pushes lightly. */
        BREEZE,
        /** Pushes creatures. */
        GUST,
        /** Also tears light things away (leaves, flowers, glass, torches) and blows fire out. */
        GALE,
        /** The pressure going at once: an explosion with no fire. */
        BOMB
    }

    public static Band band(double pressure) {
        if (pressure >= BOMB) {
            return Band.BOMB;
        }
        if (pressure >= GALE) {
            return Band.GALE;
        }
        if (pressure >= GUST) {
            return Band.GUST;
        }
        return Band.BREEZE;
    }

    /** How hard the released air throws what it hits, in blocks per tick (never past what the network carries). */
    public static double push(double pressure) {
        return Math.max(0.3D, Math.min(3.5D, 0.3D + pressure / 8.0D));
    }

    /** How far around a point the released air reaches, in blocks. */
    public static double reach(double pressure) {
        if (pressure <= 72.25D) {
            return Math.max(2.0D, 1.5D + Math.sqrt(pressure));
        }
        return 10.0D + 3.0D * Math.log10(pressure / 72.25D);
    }

    /** How strong a pressure bomb's explosion is: 0 below a bomb, then growing slowly, up to 6. */
    public static float bomb(double pressure) {
        if (pressure < BOMB) {
            return 0.0F;
        }
        if (pressure <= 1484.0D) {
            return (float) (1.0D + Math.log(pressure / GALE));
        }
        return (float) (6.0D + 2.0D * Math.log10(pressure / 1484.0D));
    }
}
