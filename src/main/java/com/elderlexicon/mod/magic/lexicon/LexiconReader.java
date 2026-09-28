package com.elderlexicon.mod.magic.lexicon;

import com.elderlexicon.mod.vita.VitaElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads a lexicon file. Its shape:
 * <pre>
 * {
 *   "defaultSource": "vis",
 *   "repertoire": ["igni", ...],
 *   "notes": {"condensed": "..."},
 *   "meetings": [{"elements": ["aqua", "aura"], "name": "Tempestade", "note": "..."}],
 *   "remove": ["rune", ...],
 *   "retired": {"exsugat": "what to write instead", ...},
 *   "runes": {
 *     "igni":    {"class": "source", "glyph": "C", "essence": {"igni": 1}, "traits": {"kindles": true}, ...},
 *     "iactare": {"class": "verb", "glyph": "I", "operation": "project", "cost": 2, "gathering": "hand", ...},
 *     "quantum": {"class": "filter", "glyph": "N", "parameter": "quantity", "argument": "value", "bare": "all"},
 *     "tenet":   {"class": "filter", "glyph": "F", "parameter": "origin", "argument": "operands"},
 *     "hasta":   {"class": "form", "origin": "fusion", "components": ["iactare", "source"], "form": "spear"},
 *     "transiectio": {"class": "verb", "origin": "fusion", "components": ["vertere", "iactare"],
 *                     "expands": ["vertere", "@", "iactare"]}
 *   }
 * }
 * </pre>
 * Every key but a rune's {@code class} may be left out. The older dictionary's keys ({@code type}, {@code function},
 * {@code shape}, {@code fusionOf}, {@code origin: original}) are still read. A retired word is one the language had and
 * no longer has: the spirit says what to write in its place.
 */
public final class LexiconReader {

    private LexiconReader() {
    }

    public static void read(Reader json, LexiconBuilder into) {
        JsonElement parsed = JsonParser.parseReader(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("A lexicon file holds one object");
        }
        JsonObject root = parsed.getAsJsonObject();
        string(root, "defaultSource").ifPresent(into::defaultSource);
        if (root.has("repertoire")) {
            into.repertoire(strings(root.get("repertoire")));
        }
        if (root.has("notes")) {
            root.getAsJsonObject("notes").entrySet()
                    .forEach(entry -> into.note(entry.getKey(), entry.getValue().getAsString()));
        }
        if (root.has("meetings")) {
            for (JsonElement element : root.getAsJsonArray("meetings")) {
                JsonObject meeting = element.getAsJsonObject();
                Set<VitaElement> elements = EnumSet.noneOf(VitaElement.class);
                for (String id : strings(meeting.get("elements"))) {
                    elements.add(element(id));
                }
                into.meeting(new Meeting(elements, string(meeting, "name").orElse(null),
                        string(meeting, "note").orElse("")));
            }
        }
        if (root.has("remove")) {
            strings(root.get("remove")).forEach(into::remove);
        }
        if (root.has("retired")) {
            root.getAsJsonObject("retired").entrySet()
                    .forEach(entry -> into.retire(entry.getKey(), entry.getValue().getAsString()));
        }
        JsonObject runes = root.has("runes") ? root.getAsJsonObject("runes") : new JsonObject();
        for (Map.Entry<String, JsonElement> entry : runes.entrySet()) {
            into.rune(rune(entry.getKey(), entry.getValue().getAsJsonObject()));
        }
    }

    private static Rune rune(String id, JsonObject json) {
        String rawClass = string(json, "class").or(() -> string(json, "type"))
                .orElseThrow(() -> new IllegalArgumentException("rune '" + id + "' has no class"));
        WordClass wordClass = WordClass.parse(rawClass)
                .orElseThrow(() -> new IllegalArgumentException("rune '" + id + "' has an unknown class: " + rawClass));
        Rune.Builder builder = Rune.builder(id, wordClass)
                .origin(Origin.parse(string(json, "origin").orElse(null)))
                .glyph(string(json, "glyph").orElse(null))
                .translation(string(json, "translation").orElse(null))
                .name(string(json, "name").orElse(null))
                .noun(string(json, "noun").orElse(null))
                .lore(string(json, "lore").orElse(null))
                .components(json.has("components") ? strings(json.get("components"))
                        : json.has("fusionOf") ? strings(json.get("fusionOf")) : List.of())
                .expansion(json.has("expands") ? strings(json.get("expands")) : List.of())
                .form(string(json, "form").orElse(null));
        if (json.has("texts")) {
            json.getAsJsonObject("texts").entrySet()
                    .forEach(entry -> builder.text(entry.getKey(), entry.getValue().getAsString()));
        }
        switch (wordClass) {
            case SOURCE -> builder.source(source(id, json));
            case VERB -> {
                if (json.has("operation") || !json.has("expands")) {
                    builder.verb(verb(id, json));
                }
            }
            case FILTER -> builder.filter(filter(id, json));
            default -> {
            }
        }
        return builder.build();
    }

    private static SourceSpec source(String id, JsonObject json) {
        Map<VitaElement, Double> essence = new EnumMap<>(VitaElement.class);
        if (json.has("essence")) {
            double total = 0.0D;
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("essence").entrySet()) {
                double share = entry.getValue().getAsDouble();
                if (share < 0.0D) {
                    throw new IllegalArgumentException("source '" + id + "' holds a negative share of " + entry.getKey());
                }
                essence.merge(element(entry.getKey()), share, Double::sum);
                total += share;
            }
            if (total <= 0.0D) {
                throw new IllegalArgumentException("source '" + id + "' holds nothing");
            }
            for (Map.Entry<VitaElement, Double> entry : essence.entrySet()) {
                entry.setValue(entry.getValue() / total);
            }
        } else {
            // A source that says nothing of its essence is the element of its own name, or mana.
            essence.put(elementOr(id, VitaElement.BALANCED), 1.0D);
        }
        VitaElement element = string(json, "element").map(LexiconReader::element).orElse(null);
        Traits traits = json.has("traits") ? traits(json.getAsJsonObject("traits")) : Traits.NONE;
        return new SourceSpec(element, essence, string(json, "bond").orElse(null), traits);
    }

    private static Traits traits(JsonObject json) {
        Traits none = Traits.NONE;
        return new Traits(
                bool(json, "persistent", none.persistent()),
                string(json, "matter").orElse(none.matter()),
                string(json, "image").orElse(none.image()),
                bool(json, "wind", none.wind()),
                bool(json, "windStrikes", none.windStrikes()),
                bool(json, "kindles", none.kindles()),
                bool(json, "touches", none.touches()),
                bool(json, "strikes", none.strikes()),
                bool(json, "quenches", none.quenches()),
                string(json, "particle").orElse(none.particle()),
                string(json, "glow").orElse(none.glow()),
                json.has("burnTicks") ? json.get("burnTicks").getAsInt() : none.burnTicks(),
                string(json, "reveals").orElse(none.reveals()));
    }

    private static VerbSpec verb(String id, JsonObject json) {
        String operation = string(json, "operation").orElse(id);
        ObjectFrame object = null;
        if (json.has("object")) {
            JsonObject frame = json.getAsJsonObject("object");
            Set<ObjectFrame.Kind> takes = EnumSet.noneOf(ObjectFrame.Kind.class);
            for (String kind : strings(frame.get("takes"))) {
                takes.add(ObjectFrame.Kind.parse(kind));
            }
            object = new ObjectFrame(takes,
                    ObjectFrame.Role.parse(string(frame, "as").orElse(null)),
                    bool(frame, "immediate", false),
                    bool(frame, "markNeedsMarkedSubject", false),
                    bool(frame, "optionalWithSubject", false),
                    string(frame, "measure").orElse(null),
                    string(frame, "refusal").orElse(null));
        }
        return new VerbSpec(operation,
                Flow.parse(string(json, "flow").orElse(null)),
                json.has("cost") ? json.get("cost").getAsDouble() : 0.0D,
                VerbSpec.Gathering.parse(string(json, "gathering").orElse(null)),
                object,
                "view".equals(string(json, "beforeVerb").orElse(null)),
                string(json, "sense").orElse(null),
                bool(json, "binds", false),
                string(json, "phrase").orElse(null),
                string(json, "joiner").orElse(null),
                bool(json, "reversible", false),
                bool(json, "transfers", false));
    }

    private static FilterSpec filter(String id, JsonObject json) {
        Parameter parameter = Parameter.parse(string(json, "parameter").orElse(null))
                .orElseThrow(() -> new IllegalArgumentException("filter '" + id + "' sets no known parameter"));
        return new FilterSpec(parameter,
                FilterSpec.Argument.parse(string(json, "argument").orElse(null)),
                "all".equals(string(json, "bare").orElse(null)));
    }

    // ------------------------------------------------------------------ small readers

    private static VitaElement element(String id) {
        return elementOr(id, null);
    }

    private static VitaElement elementOr(String id, VitaElement fallback) {
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (VitaElement element : VitaElement.values()) {
            if (element.runeId().equals(normalized) || element.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return element;
            }
        }
        if (fallback != null) {
            return fallback;
        }
        throw new IllegalArgumentException("'" + id + "' is none of the four elements nor vis");
    }

    private static Optional<String> string(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return Optional.empty();
        }
        return Optional.of(json.get(key).getAsString());
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsBoolean() : fallback;
    }

    private static List<String> strings(JsonElement element) {
        List<String> values = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return values;
        }
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            array.forEach(item -> values.add(item.getAsString()));
        } else {
            values.add(element.getAsString());
        }
        return values;
    }
}
