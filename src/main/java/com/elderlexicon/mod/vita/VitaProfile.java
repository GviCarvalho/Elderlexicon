package com.elderlexicon.mod.vita;

/**
 * Represents a snapshot of Vita energy distributed across the four primary elements.
 */
public record VitaProfile(double totalUmu, double aqua, double aura, double igni, double firmo) {

    private static final double EPSILON = 1.0E-4D;

    public static VitaProfile empty() {
        return new VitaProfile(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public boolean isEmpty() {
        return totalUmu <= EPSILON;
    }
}
