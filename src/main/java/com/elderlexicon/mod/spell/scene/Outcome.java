package com.elderlexicon.mod.spell.scene;

import java.util.Map;
import java.util.UUID;

/**
 * Something the laws of the scene made happen along the stretch {@code from} to {@code to}
 * (the two ends are equal when the emissions only touch at a spot). {@code energy} is the UMU
 * taken out of the emissions to produce it, so an outcome can never be worth more than what was paid.
 * {@code shares} says how much of that energy each caster put in, which is how blame and credit
 * are split.
 */
public record Outcome(Type type, Emission.Point from, Emission.Point to, double energy, Map<UUID, Double> shares) {

    public enum Type {
        /** Charge built up by friction released at once (an electric arc). */
        DISCHARGE,
        /** Water flashing to vapor next to heat. */
        STEAM,
        /** Vapor pressure that got too high and burst. */
        STEAM_BURST
    }

    public Outcome {
        shares = shares == null ? Map.of() : Map.copyOf(shares);
    }

    public Emission.Point center() {
        return new Emission.Extent(from, to).center();
    }

    public double length() {
        return from.distanceTo(to);
    }
}
