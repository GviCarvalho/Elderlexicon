package com.elderlexicon.mod.spell.action;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes spell cost based on resolved actions instead of raw lexemes.
 */
public final class SpellCostProcessor {

    private final Map<String, Double> functionCosts;
    private final double defaultSourceCost;

    public SpellCostProcessor() {
        this(defaultFunctionCosts(), 1.0D);
    }

    public SpellCostProcessor(Map<String, Double> functionCosts, double defaultSourceCost) {
        this.functionCosts = new HashMap<>();
        if (functionCosts != null) {
            functionCosts.forEach((key, value) -> {
                if (key != null && value != null) {
                    this.functionCosts.put(key.toLowerCase(Locale.ROOT), Math.max(0.0D, value));
                }
            });
        }
        this.defaultSourceCost = Math.max(0.0D, defaultSourceCost);
    }

    public double computeTotalCost(List<SpellAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return 0.0D;
        }
        double total = 0.0D;
        for (SpellAction action : actions) {
            if (action == null) {
                continue;
            }
            switch (action.type()) {
                case SOURCE -> total += sourceCost(action);
                case FUNCTION -> total += functionCosts.getOrDefault(normalize(action.runeId()), 0.0D);
                default -> {
                }
            }
        }
        return total;
    }

    private double sourceCost(SpellAction action) {
        if (action == null) {
            return 0.0D;
        }
        double suggested = action.suggestedCost();
        if (suggested > 0.0D) {
            return suggested;
        }
        return defaultSourceCost;
    }

    private static String normalize(String runeId) {
        return runeId == null ? "" : runeId.toLowerCase(Locale.ROOT);
    }

    private static Map<String, Double> defaultFunctionCosts() {
        Map<String, Double> map = new HashMap<>();
        map.put("iactare", 2.0D);
        map.put("vocant", 1.0D);
        map.put("vertere", 1.0D);
        map.put("ligabis", 0.1D);
        map.put("transvocatio", 1.0D);
        return map;
    }
}
