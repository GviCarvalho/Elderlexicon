package com.elderlexicon.mod.magic.lexicon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Foci (book 9.3: "Personalize the focus"; docs/varinhas-design.md): what each wand favours, kept as data in
 * {@value #RESOURCE}. A wand is a haste, maybe a grip and maybe a setting. The haste bears a share of the cost of the
 * runes it favours, the grip adds its capacity and its own favours (some runes, or every conversion into some sources),
 * and the setting holds a reserve the caster may spend instead of their own. The runes are named in the data, never in
 * the items.
 */
public final class Foci {

    public static final String RESOURCE = "/data/elderlexicon/lexicon/foci.json";

    /**
     * What a wand may be held by (a wood or a metal): its name, the capacity it adds and what it favours. It is made of
     * the items of {@code tag}, or of {@code item}; both are ids, so this stays free of the game. {@code color} (RGB)
     * tints the grip on the wand's sprite.
     */
    public record Grip(String id, String translationKey, double capacityBonus, Map<String, Double> discounts,
                       Set<String> conversions, String tag, String item, int color) {
    }

    /**
     * A gem set in a wand: how much of each kind ({@code vis} or a source) it holds, and whether it echoes the last
     * spell its bearer says. It is made of {@code item}; {@code color} (RGB) tints the gem on the wand's sprite.
     */
    public record Setting(String id, String translationKey, String item, Map<String, Double> reserves, boolean echo,
                          int color) {
    }

    /** The color a part takes when the data gives none: white, which leaves the sprite as drawn. */
    public static final int NO_COLOR = 0xFFFFFF;

    private static volatile Data data;

    private record Data(Map<String, Map<String, Double>> items, Map<String, Grip> grips, Map<String, Setting> settings) {
    }

    private Foci() {
    }

    /** The share of each rune's cost the focus item bears ({@code wand_bone}: a fifth of firmo's). */
    public static Map<String, Double> discountsOf(String item) {
        return data().items().getOrDefault(normalize(item), Map.of());
    }

    /** Every grip a wand may be held by, by id. */
    public static Map<String, Grip> grips() {
        return data().grips();
    }

    /** Every gem a wand may have set in it, by id. */
    public static Map<String, Setting> settings() {
        return data().settings();
    }

    /**
     * What a wood that favours conversions into {@code targets} bears of a spell: the cost of every conversion written
     * into one of them ({@code vertere igni} for a crimson wand).
     */
    public static double conversionDiscount(List<String> runes, Set<String> targets, ToDoubleFunction<String> runeCost) {
        if (runes == null || runes.isEmpty() || targets == null || targets.isEmpty()) {
            return 0.0D;
        }
        Lexicon lexicon = Lexicons.get();
        double total = 0.0D;
        for (int i = 0; i < runes.size() - 1; i++) {
            String current = normalize(runes.get(i));
            if (lexicon.flowOf(current) != Flow.CONVERT || !lexicon.isRune(current)) {
                continue;
            }
            if (targets.contains(normalize(runes.get(i + 1)))) {
                total += runeCost.applyAsDouble(current);
            }
        }
        return total;
    }

    private static Data data() {
        Data found = data;
        if (found == null) {
            synchronized (Foci.class) {
                found = data;
                if (found == null) {
                    found = read();
                    data = found;
                }
            }
        }
        return found;
    }

    private static Data read() {
        InputStream stream = Foci.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            return new Data(Map.of(), Map.of(), Map.of());
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Map<String, Double>> items = new LinkedHashMap<>();
            if (root.has("items")) {
                for (Map.Entry<String, JsonElement> item : root.getAsJsonObject("items").entrySet()) {
                    items.put(normalize(item.getKey()), discounts(item.getValue().getAsJsonObject()));
                }
            }
            Map<String, Grip> grips = new LinkedHashMap<>();
            if (root.has("grips")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("grips").entrySet()) {
                    JsonObject grip = entry.getValue().getAsJsonObject();
                    Set<String> conversions = new LinkedHashSet<>();
                    if (grip.has("conversions")) {
                        grip.getAsJsonArray("conversions").forEach(target -> conversions.add(normalize(target.getAsString())));
                    }
                    String id = normalize(entry.getKey());
                    grips.put(id, new Grip(id,
                            grip.has("translation") ? grip.get("translation").getAsString() : id,
                            grip.has("capacity") ? grip.get("capacity").getAsDouble() : 0.0D,
                            discounts(grip),
                            Collections.unmodifiableSet(conversions),
                            text(grip, "tag"),
                            text(grip, "item"),
                            color(grip)));
                }
            }
            Map<String, Setting> settings = new LinkedHashMap<>();
            if (root.has("settings")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("settings").entrySet()) {
                    JsonObject setting = entry.getValue().getAsJsonObject();
                    String id = normalize(entry.getKey());
                    Map<String, Double> reserves = new LinkedHashMap<>();
                    if (setting.has("reserves")) {
                        for (Map.Entry<String, JsonElement> reserve : setting.getAsJsonObject("reserves").entrySet()) {
                            if (reserve.getValue().getAsDouble() > 0.0D) {
                                reserves.put(normalize(reserve.getKey()), reserve.getValue().getAsDouble());
                            }
                        }
                    }
                    settings.put(id, new Setting(id,
                            setting.has("translation") ? setting.get("translation").getAsString() : id,
                            text(setting, "item"),
                            Collections.unmodifiableMap(reserves),
                            setting.has("echo") && setting.get("echo").getAsBoolean(),
                            color(setting)));
                }
            }
            return new Data(Collections.unmodifiableMap(items), Collections.unmodifiableMap(grips),
                    Collections.unmodifiableMap(settings));
        } catch (java.io.IOException | RuntimeException unreadable) {
            return new Data(Map.of(), Map.of(), Map.of());
        }
    }

    /** The {@code "#RRGGBB"} color of a part; white when absent or unreadable. */
    private static int color(JsonObject json) {
        if (!json.has("color")) {
            return NO_COLOR;
        }
        String hex = json.get("color").getAsString().trim();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException unreadable) {
            return NO_COLOR;
        }
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) ? normalize(json.get(key).getAsString()) : "";
    }

    private static Map<String, Double> discounts(JsonObject json) {
        Map<String, Double> discounts = new LinkedHashMap<>();
        if (json.has("discounts")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("discounts").entrySet()) {
                discounts.put(normalize(entry.getKey()), entry.getValue().getAsDouble());
            }
        }
        return Collections.unmodifiableMap(discounts);
    }

    private static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
