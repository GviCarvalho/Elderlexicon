package com.elderlexicon.mod.vita;

/**
 * Represents the severity tiers for elemental Vita imbalances.
 */
public enum VitaImbalanceTier {
    SEVERELY_LOW,
    SLIGHTLY_LOW,
    BALANCED,
    SLIGHTLY_HIGH,
    SEVERELY_HIGH;

    public boolean isBalanced() {
        return this == BALANCED;
    }

    public int severityIndex() {
        return ordinal();
    }
}
