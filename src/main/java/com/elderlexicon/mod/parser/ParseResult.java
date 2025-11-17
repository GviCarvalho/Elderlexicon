package com.elderlexicon.mod.parser;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Immutable carrier for results coming out of the parser.
 */
public record ParseResult(boolean success, String command, List<String> arguments, String message,
                          String primarySourceId) {

    public static ParseResult success(String command, List<String> arguments) {
        return success(command, arguments, "", null);
    }

    public static ParseResult success(String command, List<String> arguments, String message) {
        return success(command, arguments, message, null);
    }

    public static ParseResult success(String command, List<String> arguments, String message,
                                      String primarySourceId) {
        return new ParseResult(true, command, List.copyOf(arguments), message,
                normalizePrimarySourceId(primarySourceId));
    }

    public static ParseResult failure(String message) {
        return new ParseResult(false, "", List.of(), message, null);
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
