package com.elderlexicon.mod.spelling.client;

import net.minecraft.Util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tracks recently successful spell sequences so the overlay can suggest common combos.
 */
public final class SpellHintTracker {

    private static final int MIN_SEQUENCE_LENGTH = 2;

    private final Map<String, HintEntry> entries = new LinkedHashMap<>();
    private int capacity;

    public SpellHintTracker(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public void setCapacity(int capacity) {
        this.capacity = Math.max(1, capacity);
        trimToCapacity();
    }

    public void record(List<String> runes) {
        if (runes == null || runes.size() < MIN_SEQUENCE_LENGTH) {
            return;
        }
        List<String> normalized = sanitize(runes);
        if (normalized.size() < MIN_SEQUENCE_LENGTH) {
            return;
        }
        String key = canonicalKey(normalized);
        HintEntry entry = entries.computeIfAbsent(key, ignored -> new HintEntry(normalized));
        entry.increment();
        trimToCapacity();
    }

    public Optional<HintEntry> bestHint() {
        return entries.values().stream()
                .max(Comparator
                        .comparingInt(HintEntry::count)
                        .thenComparingLong(HintEntry::lastSeenMs));
    }

    public void clear() {
        entries.clear();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    private void trimToCapacity() {
        while (entries.size() > capacity) {
            Iterator<Map.Entry<String, HintEntry>> iterator = entries.entrySet().iterator();
            Map.Entry<String, HintEntry> weakestEntry = null;
            while (iterator.hasNext()) {
                Map.Entry<String, HintEntry> current = iterator.next();
                if (weakestEntry == null || compareHints(current.getValue(), weakestEntry.getValue()) < 0) {
                    weakestEntry = current;
                }
            }
            if (weakestEntry == null) {
                break;
            }
            entries.remove(weakestEntry.getKey());
        }
    }

    private int compareHints(HintEntry a, HintEntry b) {
        return Comparator
                .comparingInt(HintEntry::count)
                .thenComparingLong(HintEntry::lastSeenMs)
                .compare(a, b);
    }

    private List<String> sanitize(List<String> runes) {
        List<String> sanitized = new ArrayList<>(runes.size());
        for (String rune : runes) {
            if (rune == null || rune.isBlank()) {
                continue;
            }
            sanitized.add(rune.trim());
        }
        return sanitized;
    }

    private String canonicalKey(List<String> runes) {
        return String.join(" ", runes).toLowerCase(Locale.ROOT);
    }

    public static final class HintEntry {
        private final List<String> sequence;
        private int count;
        private long lastSeenMs;

        private HintEntry(List<String> sequence) {
            this.sequence = List.copyOf(sequence);
            this.count = 0;
            this.lastSeenMs = Util.getMillis();
        }

        private void increment() {
            this.count++;
            this.lastSeenMs = Util.getMillis();
        }

        public List<String> sequence() {
            return sequence;
        }

        public int count() {
            return count;
        }

        public long lastSeenMs() {
            return lastSeenMs;
        }

        public String previewString() {
            return String.join(" -> ", sequence);
        }
    }
}
