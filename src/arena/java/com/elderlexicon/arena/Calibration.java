package com.elderlexicon.arena;

/**
 * What the arena is set by: the world's numbers, as the GameTest {@code theArenaIsSetByTheWorld} measures them (speeds,
 * blocks a tick) and as the game sets them (health, blows). When the world changes, these change with it.
 *
 * @param foeSpeed     how fast the foe comes
 * @param foeReach     how far its blow reaches, from its feet to the caster's (a zombie: the square root of 2.04)
 * @param foeDamage    what its blow takes
 * @param foeCooldown  how long between two blows
 * @param foeHealth    its life
 * @param casterSpeed  how fast the caster backs away or walks up
 * @param casterHealth its life
 * @param half         half the side of the arena: walls beyond it
 * @param hit          how often what is thrown lands (a flow runs three and a half blocks a tick: it seldom misses)
 */
public record Calibration(double foeSpeed, double foeReach, double foeDamage, int foeCooldown, double foeHealth,
                          double casterSpeed, double casterHealth, double half, double hit) {

    /** A zombie (or a husk) against a sorcerer, on open ground walled at twelve blocks (measured 13/10/2026). */
    public static Calibration zombie() {
        return new Calibration(0.114D, 1.43D, 3.0D, 20, 20.0D, 0.345D, 24.0D, 12.0D, 0.9D);
    }

    public Calibration withHalf(double half) {
        return new Calibration(foeSpeed, foeReach, foeDamage, foeCooldown, foeHealth, casterSpeed, casterHealth, half, hit);
    }

    public Calibration withSpeeds(double foe, double caster) {
        return new Calibration(foe, foeReach, foeDamage, foeCooldown, foeHealth, caster, casterHealth, half, hit);
    }
}
