package com.elderlexicon.mod.spell.scene;

import com.elderlexicon.mod.vita.VitaElement;

/**
 * Physical character of an emission. The laws of the scene work on these numbers, never on rune
 * names, so any combination of elements can interact without a per-pair table.
 * <p>
 * All values are intensities in [0, 1]; {@code mass} and {@code momentum} describe how heavy and
 * how fast-moving the emitted matter is relative to the others.
 */
public record ElementProperties(double heat, double moisture, double charge, double mass, double momentum) {

    public static final ElementProperties NEUTRAL = new ElementProperties(0.0D, 0.0D, 0.0D, 0.0D, 0.0D);

    public static ElementProperties forElement(VitaElement element) {
        if (element == null) {
            return NEUTRAL;
        }
        return switch (element) {
            case IGNI -> new ElementProperties(1.0D, 0.0D, 0.1D, 0.05D, 0.2D);
            case AQUA -> new ElementProperties(0.0D, 1.0D, 0.0D, 0.5D, 0.3D);
            case AURA -> new ElementProperties(0.1D, 0.0D, 0.3D, 0.05D, 1.0D);
            case FIRMO -> new ElementProperties(0.0D, 0.0D, 0.0D, 1.0D, 0.2D);
            case BALANCED -> new ElementProperties(0.25D, 0.25D, 0.25D, 0.25D, 0.25D);
        };
    }
}
