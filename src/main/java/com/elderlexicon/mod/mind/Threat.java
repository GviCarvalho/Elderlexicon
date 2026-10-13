package com.elderlexicon.mod.mind;

/**
 * How soon a foe gets to a caster (docs/feiticeiro-design.md, 3.9): a mage does not open its trance with the foe a step
 * away, for the two seconds of it are enough for a blow, and a blow closes the crack. It weighs how far the foe stands,
 * how far its blow reaches and how fast it comes, and keeps where the foe cannot get to it before the reading is out.
 */
public final class Threat {

    /** How far the blow of a creature reaches, and of a person (a little more: a person's arm is longer). */
    public static final double BLOW = 2.0D;
    public static final double PERSON_BLOW = 3.0D;
    /** How fast, in blocks a tick, a creature comes for each point of its speed (a first guess: a zombie, 0.23, ~2.3 b/s). */
    public static final double PER_SPEED = 0.5D;
    /** How fast a person comes at a run (5.6 blocks a second). */
    public static final double PERSON_SPEED = 0.28D;
    /** How much of what it saw a foe come a tick weighs against what it saw before (a running mean). */
    private static final double FRESH = 0.3D;

    private double seen;

    /** It saw the foe come {@code closed} blocks nearer in one tick (less than zero: it went farther). */
    public void saw(double closed) {
        seen = seen * (1.0D - FRESH) + Math.max(0.0D, closed) * FRESH;
    }

    /** It lost sight of the foe, or took another: what it saw of the last one is forgotten. */
    public void forget() {
        seen = 0.0D;
    }

    /** How fast the foe comes, as far as it can tell: what it saw, or what it guesses of one like it, the faster. */
    public double speed(double guessed) {
        return Math.max(seen, guessed);
    }

    /** In how many ticks a foe {@code distance} away, whose blow reaches {@code blow}, coming at {@code speed}, strikes. */
    public static double ticksToBlow(double distance, double blow, double speed) {
        if (speed <= 1.0E-6D) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(0.0D, distance - blow) / speed;
    }

    /**
     * How far a foe whose blow reaches {@code blow}, coming at {@code speed}, must stand for a trance of {@code ticks}
     * to end, {@code margin} times over, before it strikes.
     */
    public static double safeDistance(double blow, double speed, int ticks, double margin) {
        return blow + speed * ticks * margin;
    }
}
