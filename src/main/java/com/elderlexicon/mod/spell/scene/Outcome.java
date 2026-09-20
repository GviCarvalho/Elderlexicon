package com.elderlexicon.mod.spell.scene;

/**
 * Something the laws of the scene made happen. {@code energy} is the UMU taken out of the
 * emissions to produce it, so an outcome can never be worth more than what was paid.
 */
public record Outcome(Type type, Emission.Point at, double energy) {

    public enum Type {
        /** Charge built up by friction released at once (a lightning strike). */
        DISCHARGE,
        /** Water flashing to vapor next to heat. */
        STEAM,
        /** Vapor pressure that got too high and burst. */
        STEAM_BURST
    }
}
