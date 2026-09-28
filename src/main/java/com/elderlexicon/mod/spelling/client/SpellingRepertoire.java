package com.elderlexicon.mod.spelling.client;

import com.elderlexicon.mod.magic.lexicon.Lexicons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Temporary, client-only repertoire representation. Sprint B will replace this
 * with the real capability + sync path, but Sprint A needs deterministic data
 * so players can record spells immediately.
 */
public final class SpellingRepertoire {

    private static final int HOTBAR_SIZE = 9;

    private final List<String> slots = new ArrayList<>(HOTBAR_SIZE);

    public SpellingRepertoire() {
        // The runes a new mage is given, as the lexicon says.
        List<String> defaults = Lexicons.get().repertoire();
        for (int i = 0; i < HOTBAR_SIZE; i++) {
            slots.add(i < defaults.size() ? defaults.get(i) : "");
        }
    }

    public Optional<String> runeForSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(slots.get(slotIndex)).filter(rune -> !rune.isBlank());
    }

    public List<String> slotsView() {
        return Collections.unmodifiableList(slots);
    }
}
