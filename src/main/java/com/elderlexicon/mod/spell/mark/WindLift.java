package com.elderlexicon.mod.spell.mark;

/**
 * A lasting wind as a force, not a speed: it pushes with an acceleration proportional to its power (UMU per tick)
 * over the mass it pushes, and the push fades with distance from what it blows out of (a ground effect):
 * {@code a(h) = K x power / mass x e^(-h / H)}. An updraft therefore holds a thing where its push equals the
 * thing's weight, at {@code H x ln(a0 / g)}: just above the ground (sliding with no friction) when barely stronger
 * than gravity, and each extra block of height costs e^(1/H) times the power.
 * <p>
 * Calibrated so that 1 UMU per second holds a 20 health player right at the ground.
 */
public final class WindLift {

    /** Minecraft's gravity on living things, in blocks per tick squared. */
    public static final double GRAVITY = 0.08D;
    /** Blocks over which the push falls to 1/e. */
    public static final double FADE_HEIGHT = 2.0D;
    /** Push per unit of power per unit of mass: 1 UMU/s (0.05 UMU/tick) on 20 mass is exactly gravity. */
    private static final double PUSH_PER_POWER = GRAVITY * 20.0D / 0.05D;
    /**
     * Share of the vertical speed an updraft takes away each tick. Around a held height the push works like a spring
     * of stiffness g / H, and 2 x sqrt(g / H) = 0.4 damps that spring critically: it settles without bouncing.
     */
    public static final double DAMPING = 1.0D * Math.sqrt(GRAVITY / FADE_HEIGHT);
    private static final double MIN_MASS = 0.1D;

    private WindLift() {
    }

    /** Acceleration, in blocks per tick squared, of a wind of {@code umuPerTick} on {@code mass}, {@code height} away. */
    public static double acceleration(double umuPerTick, double mass, double height) {
        if (umuPerTick <= 0.0D) {
            return 0.0D;
        }
        double atSource = PUSH_PER_POWER * umuPerTick / Math.max(MIN_MASS, mass);
        return atSource * Math.exp(-Math.max(0.0D, height) / FADE_HEIGHT);
    }

    /** Height at which an updraft holds {@code mass} still, or zero when it cannot lift it at all. */
    public static double hoverHeight(double umuPerTick, double mass) {
        double atSource = acceleration(umuPerTick, mass, 0.0D);
        return atSource <= GRAVITY ? 0.0D : FADE_HEIGHT * Math.log(atSource / GRAVITY);
    }

    /** Power, in UMU per tick, an updraft needs to hold {@code mass} at {@code height}. */
    public static double powerToHover(double mass, double height) {
        return GRAVITY * Math.max(MIN_MASS, mass) / PUSH_PER_POWER * Math.exp(Math.max(0.0D, height) / FADE_HEIGHT);
    }
}
