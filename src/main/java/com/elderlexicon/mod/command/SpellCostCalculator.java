package com.elderlexicon.mod.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static java.util.Map.entry;

/**
 * Shared helpers that convert rune sequences into aggregate UMU costs.
 */
public final class SpellCostCalculator {

    private static final double IACTARE_COST_PER_SECOND = 1.0D;
    private static final double IACTARE_DEFAULT_DURATION_SECONDS = 2.0D;
    private static final double VOCANT_TOTAL_COST = 1.0D;

    private static final double SOURCE_DEFAULT_COST = 1.0D;

    private static final Map<String, Double> RUNE_COSTS = Map.ofEntries(
            entry("igni", SOURCE_DEFAULT_COST),
            entry("firmo", SOURCE_DEFAULT_COST),
            entry("aura", SOURCE_DEFAULT_COST),
            entry("aqua", SOURCE_DEFAULT_COST),
            entry("iactare", IACTARE_COST_PER_SECOND * IACTARE_DEFAULT_DURATION_SECONDS),
            entry("vocant", VOCANT_TOTAL_COST),
            entry("surgit", 2.0D),
            entry("reframe", 5.0D),
            entry("vertere", 1.0D)
    );

    private SpellCostCalculator() {
    }

    public static double computeTotalCost(List<String> lexemes) {
        if (lexemes == null || lexemes.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (String token : lexemes) {
            if (token == null || token.isBlank()) {
                continue;
            }
            String lowered = token.toLowerCase(Locale.ROOT);
            total += RUNE_COSTS.getOrDefault(lowered, 0.0D);
        }
        return total;
    }

    public static String formatCost(double cost) {
        if (Math.abs(cost - Math.rint(cost)) < 1.0E-4D) {
            return Long.toString(Math.round(cost));
        }
        return String.format(Locale.ROOT, "%.2f", cost);
    }

    public static Map<String, Double> runeCosts() {
        return RUNE_COSTS;
    }
}
