package com.elderlexicon.mod.spelling.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpellHintTrackerTest {

    @Test
    void recordsMostFrequentSequence() {
        SpellHintTracker tracker = new SpellHintTracker(5);
        tracker.record(List.of("igni", "murus"));
        tracker.record(List.of("igni", "murus"));
        tracker.record(List.of("aqua", "impediunt", "vertere"));

        SpellHintTracker.HintEntry entry = tracker.bestHint().orElseThrow();
        assertEquals(List.of("igni", "murus"), entry.sequence());
        assertEquals(2, entry.count());
    }

    @Test
    void trimsWhenCapacityShrinks() {
        SpellHintTracker tracker = new SpellHintTracker(3);
        tracker.record(List.of("igni", "murus"));
        tracker.record(List.of("igni", "murus"));
        tracker.record(List.of("aqua", "impediunt"));
        tracker.record(List.of("terra", "vertere"));

        tracker.setCapacity(1);

        SpellHintTracker.HintEntry entry = tracker.bestHint().orElseThrow();
        assertEquals(List.of("igni", "murus"), entry.sequence());
        assertEquals(2, entry.count());
    }

    @Test
    void ignoresSequencesShorterThanTwoRunes() {
        SpellHintTracker tracker = new SpellHintTracker(3);
        tracker.record(List.of("igni"));
        tracker.record(List.of());
        tracker.record(List.of("", "murus"));

        assertTrue(tracker.bestHint().isEmpty());
    }
}
