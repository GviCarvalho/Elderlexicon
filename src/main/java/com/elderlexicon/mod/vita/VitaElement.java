package com.elderlexicon.mod.vita;

import com.elderlexicon.mod.magic.lexicon.Lexicons;

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

    /**
     * The element a source rune follows the laws of: its own for the four and vis, and for any other source (a fusion,
     * or one an addon brings) the one the lexicon gives it ({@code fusus} follows fire's). Anything that is no source
     * is mana, as the book fills the gap.
     */
    public static VitaElement fromRuneId(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return BALANCED;
        }
        String normalized = runeId.toLowerCase(Locale.ROOT).trim();
        for (VitaElement element : values()) {
            if (element.runeId.equals(normalized)) {
                return element;
            }
        }
        try {
            return Lexicons.get().elementOf(normalized);
        } catch (RuntimeException unreadable) {
            return BALANCED;
        }
    }
}
