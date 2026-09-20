package com.elderlexicon.mod.command;

import java.util.Locale;

/**
 * Shared helpers for formatting UMU cost displays.
 */
public final class SpellCostCalculator {

    private SpellCostCalculator() {
    }

    public static String formatCost(double cost) {
        if (Math.abs(cost - Math.rint(cost)) < 1.0E-4D) {
            return Long.toString(Math.round(cost));
        }
        return String.format(Locale.ROOT, "%.2f", cost);
    }
}
