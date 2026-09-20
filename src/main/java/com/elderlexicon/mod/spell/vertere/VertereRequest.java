package com.elderlexicon.mod.spell.vertere;

import com.elderlexicon.mod.vita.VitaElement;

import java.util.Objects;

/**
 * Represents a single Vertere conversion request detected during parsing.
 */
public record VertereRequest(VitaElement source, VitaElement target, double amount) {

    private static final double EPSILON = 1.0E-4D;

    public VertereRequest {
        source = source == null ? VitaElement.BALANCED : source;
        target = Objects.requireNonNull(target, "target");
        amount = amount <= EPSILON ? 0.0D : amount;
    }

    public boolean isViable() {
        return amount > EPSILON;
    }
}
