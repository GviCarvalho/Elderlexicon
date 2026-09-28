package com.elderlexicon.mod.spell.action;

import com.elderlexicon.mod.magic.lexicon.Lexicons;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Computes spell cost based on resolved actions instead of raw lexemes.
 */
public final class SpellCostProcessor {

    /** Fixed costs by verb, or null to ask the lexicon in force for each verb's cost. */
    private final Map<String, Double> functionCosts;
    private final double defaultSourceCost;

    /** Costs every verb what the lexicon in force says it costs (iactare 2, vocant 1, vertere 1, ligabis 0.1 ...). */
    public SpellCostProcessor() {
        this.functionCosts = null;
        this.defaultSourceCost = 1.0D;
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

    /**
     * Whether a spell costs anything at all: it does when a verb acts. A spell of sources and filters alone does
     * nothing, and costs nothing. What the world pays (a source taken with an origin filter) is settled as the spell
     * runs.
     */
    public static boolean requiresEnergy(List<SpellAction> actions) {
        if (actions == null || actions.isEmpty()) {
            return false;
        }
        for (SpellAction action : actions) {
            if (action != null && action.type() == SpellActionType.FUNCTION) {
                return true;
            }
        }
        return false;
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
                case FUNCTION -> total += functionCost(normalize(action.runeId()));
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

    private double functionCost(String runeId) {
        if (functionCosts == null) {
            return Lexicons.get().costOf(runeId);
        }
        return functionCosts.getOrDefault(runeId, 0.0D);
    }
}
