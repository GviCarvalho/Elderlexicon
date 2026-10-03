package com.elderlexicon.mod.magic.matter;

/**
 * Energy put into a portion, for each of its UMU, in the measure of {@link Tension}: a portion whose parts all cancel
 * out holds 1 (docs/plano-rosa-dos-elementos.md, section 2). It is always the same for the same inputs.
 *
 * @param kinetic       the push of a force: half the mass of a UMU times the square of its speed
 * @param thermal       heat above common fire, from nothing for a flame to 1 at the heat of plasma
 * @param concentration many UMU pressed into one point, from nothing for one to 1 when a black hole forms
 */
public record Agitation(double kinetic, double thermal, double concentration) {

    public static final Agitation NONE = new Agitation(0.0D, 0.0D, 0.0D);

    /** The heat of common fire, and of plasma (the same as {@code spell.Heat}). */
    private static final double COMMON_HEAT = 1.0D;
    private static final double PLASMA_HEAT = 30.0D;
    /** How many UMU pressed into one point make a black hole (the same as {@code spell.Density}). */
    private static final double BLACK_HOLE_UMU = 1000.0D;

    public Agitation {
        if (kinetic < 0.0D || thermal < 0.0D || concentration < 0.0D) {
            throw new IllegalArgumentException("a negative agitation");
        }
    }

    /** Matter of this {@code density} (mass for each UMU) flying at {@code speed}, in blocks a tick. */
    public static Agitation moving(double density, double speed) {
        return new Agitation(ImpactLaw.energy(Math.max(0.0D, density), speed), 0.0D, 0.0D);
    }

    /** Fire this hot (common fire is 1). */
    public static Agitation heated(double heat) {
        return new Agitation(0.0D, logShare(heat / COMMON_HEAT, PLASMA_HEAT / COMMON_HEAT), 0.0D);
    }

    /** This many UMU pressed into one point. */
    public static Agitation pressed(double umu) {
        return new Agitation(0.0D, 0.0D, logShare(umu, BLACK_HOLE_UMU));
    }

    /** Both agitations at once. */
    public Agitation plus(Agitation other) {
        return new Agitation(kinetic + other.kinetic, thermal + other.thermal, concentration + other.concentration);
    }

    public double total() {
        return kinetic + thermal + concentration;
    }

    /** Where {@code value} is between 1 and {@code top}, on a log scale: 0 at 1 or less, 1 at the top, more beyond. */
    private static double logShare(double value, double top) {
        return value <= 1.0D ? 0.0D : Math.log(value) / Math.log(top);
    }
}
