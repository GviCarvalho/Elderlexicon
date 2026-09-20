package com.elderlexicon.mod.spelling.server;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.InputStreamReader;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Loads fusion rules from the parser list and resolves rune combinations.
 */
final class FusionResolver {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String RESOURCE_PATH = "/com/elderlexicon/mod/parser/ParserList.json";
    private static final Type MAP_TYPE = new TypeToken<Map<String, RuneConfig>>() { }.getType();

    private final Map<String, RuneMeta> runes;
    private final Map<String, FusionRule> fusionByResult;

    private FusionResolver(Map<String, RuneMeta> runes, Map<String, FusionRule> fusionByResult) {
        this.runes = runes;
        this.fusionByResult = fusionByResult;
    }

    boolean isKnownRune(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return false;
        }
        return runes.containsKey(normalize(runeId));
    }

    boolean isOriginalRune(String runeId) {
        if (runeId == null || runeId.isBlank()) {
            return false;
        }
        RuneMeta meta = runes.get(normalize(runeId));
        return meta != null && meta.isOriginal();
    }

    static FusionResolver load() {
        var stream = FusionResolver.class.getResourceAsStream(RESOURCE_PATH);
        if (stream == null) {
            LOGGER.error("Parser list not found at {}", RESOURCE_PATH);
            return new FusionResolver(Map.of(), Map.of());
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            Map<String, RuneConfig> parsed = new Gson().fromJson(reader, MAP_TYPE);
            if (parsed == null || parsed.isEmpty()) {
                LOGGER.warn("Parser list is empty; fusion resolver disabled");
                return new FusionResolver(Map.of(), Map.of());
            }
            Map<String, RuneMeta> runes = new HashMap<>();
            Map<String, FusionRule> rules = new HashMap<>();
            parsed.forEach((id, cfg) -> {
                String key = normalize(id);
                RuneType type = RuneType.from(cfg.type);
                Origin origin = Origin.from(cfg.origin);
                runes.put(key, new RuneMeta(key, type, origin));
                if (cfg.fusionOf != null && cfg.fusionOf.size() == 2) {
                    rules.put(key, new FusionRule(key, cfg.fusionOf.get(0), cfg.fusionOf.get(1)));
                }
            });
            return new FusionResolver(Map.copyOf(runes), Map.copyOf(rules));
        } catch (Exception exception) {
            LOGGER.error("Failed to load fusion rules", exception);
            return new FusionResolver(Map.of(), Map.of());
        }
    }

    Optional<String> fuse(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return Optional.empty();
        }
        String left = normalize(a);
        String right = normalize(b);
        if (left.equals(right)) {
            return Optional.of(left);
        }
        RuneMeta leftMeta = runes.get(left);
        RuneMeta rightMeta = runes.get(right);
        if (leftMeta == null || rightMeta == null) {
            return Optional.empty();
        }
        for (FusionRule rule : fusionByResult.values()) {
            if (rule.matches(leftMeta, rightMeta)) {
                return Optional.of(rule.result());
            }
        }
        return Optional.empty();
    }

    enum RuneType {
        SOURCE,
        FUNCTION,
        SHAPE,
        FILTER,
        UNKNOWN;

        static RuneType from(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            try {
                return RuneType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                return UNKNOWN;
            }
        }
    }

    private enum Origin {
        ORIGINAL,
        FUSION,
        UNKNOWN;

        static Origin from(String raw) {
            if (raw == null) {
                return UNKNOWN;
            }
            String normalized = raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "original" -> ORIGINAL;
                case "fusion" -> FUSION;
                default -> UNKNOWN;
            };
        }

        boolean isOriginal() {
            return this == ORIGINAL;
        }
    }

    private record RuneMeta(String id, RuneType type, Origin origin) {
        boolean isOriginal() {
            return origin != null && origin.isOriginal();
        }
    }

    private record FusionRule(String result, String first, String second) {
        boolean matches(RuneMeta a, RuneMeta b) {
            return (matchesToken(first, a, b) && matchesToken(second, a, b))
                    || (matchesToken(first, b, a) && matchesToken(second, b, a));
        }

        private boolean matchesToken(String token, RuneMeta primary, RuneMeta secondary) {
            if ("source".equalsIgnoreCase(token)) {
                return secondary.type() == RuneType.SOURCE;
            }
            return token.equalsIgnoreCase(primary.id()) || token.equalsIgnoreCase(secondary.id());
        }
    }

    private record RuneConfig(String type,
                              String translation,
                              @SerializedName("requiresTarget") Boolean requiresTarget,
                              List<String> fusionOf,
                              String origin) { }

    private static String normalize(String token) {
        return token == null ? "" : token.trim().toLowerCase(Locale.ROOT);
    }
}
