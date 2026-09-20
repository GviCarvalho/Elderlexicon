package com.elderlexicon.mod.spelling.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mutable buffer that collects the rune sequence being recorded on the client.
 */
public final class RuneBuffer {

    public static final int DEFAULT_CAPACITY = 7;

    private final int capacity;
    private final List<String> runes = new ArrayList<>();

    public RuneBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public RuneBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public boolean append(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return false;
        }
        if (runes.size() >= capacity) {
            return false;
        }
        runes.add(runeId.toLowerCase(Locale.ROOT));
        return true;
    }

    public void clear() {
        runes.clear();
    }

    public boolean isEmpty() {
        return runes.isEmpty();
    }

    public boolean isFull() {
        return runes.size() >= capacity;
    }

    public List<String> entries() {
        return List.copyOf(runes);
    }

    public int capacity() {
        return capacity;
    }

    public int size() {
        return runes.size();
    }

    public String previewString() {
        if (runes.isEmpty()) {
            return "";
        }
        return String.join(" -> ", runes);
    }
}
