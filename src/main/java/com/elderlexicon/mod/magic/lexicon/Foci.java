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
 * Foci (book 9.3: "Personalize the focus"): what each wand favours, kept as data in {@value #RESOURCE}. A focus bears a
 * share of the cost of the runes it favours, and a wand's wood adds its own favours: some runes, or every conversion
 * into some sources. The runes are named in the data, never in the items.
 */
public final class Foci {

    public static final String RESOURCE = "/data/elderlexicon/lexicon/foci.json";

    /** A wood a wand may be made of: its name, the capacity it adds and what it favours. */
    public record Wood(String id, String translationKey, double capacityBonus, Map<String, Double> discounts,
                       Set<String> conversions) {
    }

    private static volatile Data data;

    private record Data(Map<String, Map<String, Double>> items, Map<String, Wood> woods) {
    }

    private Foci() {
    }

    /** The share of each rune's cost the focus item bears ({@code wand_bone}: a fifth of firmo's). */
    public static Map<String, Double> discountsOf(String item) {
        return data().items().getOrDefault(normalize(item), Map.of());
    }

    /** Every wood a wand may be made of, by id. */
    public static Map<String, Wood> woods() {
        return data().woods();
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
            return new Data(Map.of(), Map.of());
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            Map<String, Map<String, Double>> items = new LinkedHashMap<>();
            if (root.has("items")) {
                for (Map.Entry<String, JsonElement> item : root.getAsJsonObject("items").entrySet()) {
                    items.put(normalize(item.getKey()), discounts(item.getValue().getAsJsonObject()));
                }
            }
            Map<String, Wood> woods = new LinkedHashMap<>();
            if (root.has("woods")) {
                for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("woods").entrySet()) {
                    JsonObject wood = entry.getValue().getAsJsonObject();
                    Set<String> conversions = new LinkedHashSet<>();
                    if (wood.has("conversions")) {
                        wood.getAsJsonArray("conversions").forEach(target -> conversions.add(normalize(target.getAsString())));
                    }
                    String id = normalize(entry.getKey());
                    woods.put(id, new Wood(id,
                            wood.has("translation") ? wood.get("translation").getAsString() : id,
                            wood.has("capacity") ? wood.get("capacity").getAsDouble() : 0.0D,
                            discounts(wood),
                            Collections.unmodifiableSet(conversions)));
                }
            }
            return new Data(Collections.unmodifiableMap(items), Collections.unmodifiableMap(woods));
        } catch (java.io.IOException | RuntimeException unreadable) {
            return new Data(Map.of(), Map.of());
        }
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
