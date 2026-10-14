package com.elderlexicon.arena;

import com.elderlexicon.mod.mind.Threat;

/**
 * What the arena is set by: the world's numbers, as the GameTests {@code theArenaIsSetByTheWorld...} measure them
 * (speeds, blocks a tick) and as the game sets them (health, blows, bows). When the world changes, these change with it.
 *
 * @param foe          the kind of foe, as the doctrine is named
 * @param foeSpeed     how fast the foe comes
 * @param guessed      how fast the mind guesses it comes (Threat.PER_SPEED times its speed attribute), as in the world
 * @param foeReach     how far its blow reaches, feet to feet (vanilla: the square root of (2 × its width)² + ours)
 * @param foeDamage    what its blow takes
 * @param foeCooldown  how long between two blows
 * @param foeHealth    its life
 * @param leaps        whether it leaps at the caster from two to four blocks off (a spider)
 * @param bow          how it shoots, if it shoots; null for one that only strikes
 * @param casterSpeed  how fast the caster backs away or walks up
 * @param casterHealth its life
 * @param hit          how often what is thrown lands (a flow runs three and a half blocks a tick: it seldom misses)
 */
public record Calibration(String foe, double foeSpeed, double guessed, double foeReach, double foeDamage,
                          int foeCooldown, double foeHealth, boolean leaps, Bow bow, double casterSpeed,
                          double casterHealth, double hit) {

    /**
     * How a foe shoots, as a skeleton's bow goal does: within {@code reach} it waits {@code interval}, draws for
     * {@code draw} and lets fly; it backs off when nearer than three quarters of its reach.
     *
     * @param arrowSpeed how fast the arrow flies
     * @param damage     what an arrow takes
     * @param hitNear    how often an arrow lands on one standing still within five blocks
     * @param hitFar     and at the bow's reach
     * @param moving     how much of that is left against one walking
     */
    public record Bow(double reach, int interval, int draw, double arrowSpeed, double damage, double hitNear,
                      double hitFar, double moving) {

        double hit(double distance, boolean walking) {
            double share = Math.max(0.0D, Math.min(1.0D, (distance - 5.0D) / (reach - 5.0D)));
            return (hitNear + (hitFar - hitNear) * share) * (walking ? moving : 1.0D);
        }
    }

    /** A sorcerer: how fast it backs away (measured 13/10/2026), its life, how wide it is. */
    private static final double SORCERER = 0.345D;
    private static final double SORCERER_HEALTH = 24.0D;
    private static final double SORCERER_WIDTH = 0.6D;

    /** The reach of a creature {@code width} wide striking a sorcerer, as vanilla's melee goal works it out. */
    static double reachOf(double width) {
        return Math.sqrt(Math.pow(width * 2.0D, 2.0D) + SORCERER_WIDTH);
    }

    /** A zombie (or a husk) against a sorcerer (measured 13/10/2026). */
    public static Calibration zombie() {
        return new Calibration("zombie", 0.114D, 0.23D * Threat.PER_SPEED, reachOf(0.6D), 3.0D, 20, 20.0D, false,
                null, SORCERER, SORCERER_HEALTH, 0.9D);
    }

    /** A spider: faster, wider (its blow reaches farther), and it leaps (measured 13/10/2026). */
    public static Calibration spider() {
        return new Calibration("spider", 0.18D, 0.3D * Threat.PER_SPEED, reachOf(1.4D), 2.0D, 20, 16.0D, true, null,
                SORCERER, SORCERER_HEALTH, 0.9D);
    }

    /**
     * A skeleton with a bow (normal difficulty): forty ticks between arrows, twenty drawing, fifteen blocks of reach.
     * How often its arrows land is a guess, to be set by playing.
     */
    public static Calibration skeleton() {
        return new Calibration("skeleton", 0.125D, 0.25D * Threat.PER_SPEED, reachOf(0.6D), 2.0D, 20, 20.0D, false,
                new Bow(15.0D, 40, 20, 1.6D, 3.5D, 0.9D, 0.6D, 0.7D), SORCERER, SORCERER_HEALTH, 0.9D);
    }

    public static Calibration of(String foe) {
        return switch (foe) {
            case "zombie" -> zombie();
            case "spider" -> spider();
            case "skeleton" -> skeleton();
            default -> throw new IllegalArgumentException("no arena for " + foe + " (zombie, spider, skeleton)");
        };
    }

    public Calibration withSpeeds(double foe, double caster) {
        return new Calibration(this.foe, foe, guessed, foeReach, foeDamage, foeCooldown, foeHealth, leaps, bow, caster,
                casterHealth, hit);
    }
}
