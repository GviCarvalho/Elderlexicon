package com.elderlexicon.mod.parser;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Loads and resolves tokens defined in {@code ParserList.json}.
 */
public final class ParserDictionary {

    private static final String RESOURCE_PATH = "/com/elderlexicon/mod/parser/ParserList.json";
    private static final Type MAP_TYPE = new TypeToken<Map<String, RuneConfig>>() { }.getType();

    private final Map<String, RuneDefinition> definitions;

    private ParserDictionary(Map<String, RuneDefinition> definitions) {
        this.definitions = definitions;
    }

    public static ParserDictionary load() {
        var stream = ParserDictionary.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            throw new IllegalStateException("Resource '" + RESOURCE_PATH + "' not found on classpath.");
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, RuneConfig> parsed = new Gson().fromJson(reader, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                throw new IllegalStateException("Parser dictionary is empty.");
            }

            Map<String, RuneDefinition> mapped = parsed.entrySet().stream()
                    .collect(Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().toLowerCase(Locale.ROOT),
                            entry -> entry.getValue().toDefinition(entry.getKey())));

            return new ParserDictionary(mapped);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load parser dictionary", exception);
        }
    }

    public Optional<RuneDefinition> lookup(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String lookupKey = token.toLowerCase(Locale.ROOT).trim();
        return Optional.ofNullable(definitions.get(lookupKey));
    }

    public Map<String, RuneDefinition> entries() {
        return Collections.unmodifiableMap(definitions);
    }

    public enum RuneType {
        SOURCE,
        FUNCTION,
        SHAPE;

        static RuneType from(String value) {
            try {
                return RuneType.valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Unknown rune type: " + value, exception);
            }
        }
    }

    public record RuneDefinition(String id, RuneType type, String translation, boolean requiresTarget) { }

    private record RuneConfig(String type, String translation,
                              @SerializedName("requiresTarget") Boolean requiresTarget) {

        RuneDefinition toDefinition(String id) {
            Objects.requireNonNull(type, "Missing type for rune '" + id + "'");
            Objects.requireNonNull(translation, "Missing translation for rune '" + id + "'");

            boolean needsTarget = requiresTarget != null && requiresTarget;
            return new RuneDefinition(id, RuneType.from(type), translation, needsTarget);
        }
    }
}
