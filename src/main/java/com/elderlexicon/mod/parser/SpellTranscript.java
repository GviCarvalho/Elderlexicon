package com.elderlexicon.mod.parser;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Immutable carrier for parser transcription results generated from spell actions.
 */
public record SpellTranscript(boolean success, String message, List<String> lexemes, String primarySourceId) {

    public SpellTranscript {
        if (message == null) {
            message = "";
        }
        lexemes = lexemes == null ? List.of() : List.copyOf(lexemes);
        primarySourceId = normalizePrimarySourceId(primarySourceId);
    }

    public static SpellTranscript success(String message, List<String> lexemes, String primarySourceId) {
        return new SpellTranscript(true, message, lexemes, primarySourceId);
    }

    public static SpellTranscript failure(String message) {
        return new SpellTranscript(false, message, List.of(), null);
    }

    public Optional<String> primarySource() {
        if (primarySourceId == null || primarySourceId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(primarySourceId);
    }

    private static String normalizePrimarySourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return null;
        }
        return sourceId.trim().toLowerCase(Locale.ROOT);
    }
}
