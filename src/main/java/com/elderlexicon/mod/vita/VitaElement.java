package com.elderlexicon.mod.vita;

import java.util.Locale;

/**
 * Represents the elemental components that make up a Vita reserve.
 */
public enum VitaElement {
    AQUA("aqua"),
    AURA("aura"),
    IGNI("igni"),
    FIRMO("firmo"),
    BALANCED("vis");

    private final String runeId;

    VitaElement(String runeId) {
        this.runeId = runeId;
    }

    public String runeId() {
        return runeId;
    }

    public boolean isBalanced() {
        return this == BALANCED;
    }

    public static VitaElement fromRuneId(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return BALANCED;
        }
        String normalized = runeId.toLowerCase(Locale.ROOT);
        for (VitaElement element : values()) {
            if (element.runeId.equals(normalized)) {
                return element;
            }
        }
        return BALANCED;
    }
}
