package com.elderlexicon.mod.spelling.client;

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
        slots.add("igni");
        slots.add("aqua");
        slots.add("aura");
        slots.add("firmo");
        slots.add("impediunt");
        slots.add("vertere");
        slots.add("vocant");
        slots.add("murus");
        slots.add("iactare");
    }

    public Optional<String> runeForSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return Optional.empty();
        }
        return Optional.ofNullable(slots.get(slotIndex));
    }

    public List<String> slotsView() {
        return Collections.unmodifiableList(slots);
    }
}
