package com.elderlexicon.mod.parser;

import java.util.List;

/**
 * Bridges the new {@link SpellTranscript} results with legacy {@link ParseResult} callers.
 */
public final class SpellParsingAdapter {

    private SpellParsingAdapter() {
    }

    public static ParseResult toParseResult(SpellTranscript transcript) {
        return toParseResult(transcript, "Falha ao transcrever o spell.");
    }

    public static ParseResult toParseResult(SpellTranscript transcript, String failureFallback) {
        if (transcript == null) {
            return ParseResult.failure(normalizeFallback(failureFallback, "Transcrição ausente."));
        }
        if (!transcript.success()) {
            String message = transcript.message();
            if (message == null || message.isBlank()) {
                message = normalizeFallback(failureFallback, "Falha ao transcrever o spell.");
            }
            return ParseResult.failure(message);
        }
        List<String> lexemes = transcript.lexemes() == null ? List.of() : transcript.lexemes();
        String primarySourceId = transcript.primarySource().orElse(null);
        return ParseResult.success("spell", lexemes, transcript.message(), primarySourceId);
    }

    private static String normalizeFallback(String provided, String defaultMessage) {
        if (provided == null || provided.isBlank()) {
            return defaultMessage;
        }
        return provided;
    }
}
