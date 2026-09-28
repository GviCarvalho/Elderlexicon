package com.elderlexicon.mod.magic.matter;

import com.elderlexicon.mod.vita.VitaElement;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.Reader;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads a materials file. Its shape:
 * <pre>
 * {
 *   "remove": ["substance", ...],
 *   "substances": {
 *     "water": {"name": "água", "recipe": {"aqua": 1}, "state": "liquid", "unit": 3,
 *               "forms": {"solid": [{"block": "minecraft:ice"}], "liquid": [{"block": "minecraft:water"}],
 *                         "gas": [{"particle": "minecraft:cloud"}]}},
 *     "iron":  {"name": "ferro", "recipe": {"firmo": 0.9, "igni": 0.1}, "state": "solid", "unit": 5,
 *               "forms": {"solid": [{"block": "minecraft:raw_iron_block"}, {"item": "minecraft:raw_iron"}]}}
 *   }
 * }
 * </pre>
 * A recipe gives each primordial by its aspect, in any proportion (they are normalized). A block holds the substance's
 * unit; an item, a ninth of it unless it says its own {@code umu}. A state with no forms shows as the nearest state
 * that has some.
 */
public final class MaterialTableReader {

    /** Nine items make a block, as the game has it for ingots and raw metals. */
    private static final double ITEMS_PER_UNIT = 9.0D;

    private MaterialTableReader() {
    }

    public static void read(Reader json, MaterialTableBuilder into) {
        JsonElement parsed = JsonParser.parseReader(json);
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("A materials file holds one object");
        }
        JsonObject root = parsed.getAsJsonObject();
        if (root.has("remove")) {
            root.getAsJsonArray("remove").forEach(id -> into.remove(id.getAsString()));
        }
        if (root.has("substances")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("substances").entrySet()) {
                into.substance(substance(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
    }

    private static Substance substance(String id, JsonObject json) {
        if (!json.has("recipe")) {
            throw new IllegalArgumentException("substance '" + id + "' has no recipe");
        }
        Map<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        for (Map.Entry<String, JsonElement> part : json.getAsJsonObject("recipe").entrySet()) {
            amounts.merge(aspect(id, part.getKey()), part.getValue().getAsDouble(), Double::sum);
        }
        Composition recipe = Composition.of(amounts);
        State nature = json.has("state") ? state(id, json.get("state").getAsString())
                : State.of(recipe.pureAspect().orElse(VitaElement.FIRMO)).orElse(State.SOLID);
        double unit = json.has("unit") ? json.get("unit").getAsDouble() : 1.0D;
        Map<State, List<Form>> forms = new EnumMap<>(State.class);
        if (json.has("forms")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("forms").entrySet()) {
                State state = state(id, entry.getKey());
                List<Form> list = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    list.add(form(id, element.getAsJsonObject(), unit));
                }
                forms.put(state, list);
            }
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        return new Substance(MaterialTableBuilder.normalize(id), name, recipe, nature, unit, forms);
    }

    private static Form form(String id, JsonObject json, double unit) {
        for (Form.Kind kind : Form.Kind.values()) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            if (json.has(key)) {
                double fallback = switch (kind) {
                    case BLOCK -> unit;
                    case ITEM -> unit / ITEMS_PER_UNIT;
                    default -> 0.0D;
                };
                double umu = json.has("umu") ? json.get("umu").getAsDouble() : fallback;
                return new Form(kind, json.get(key).getAsString(), umu);
            }
        }
        throw new IllegalArgumentException("a form of '" + id + "' is no block, item, particle nor entity: " + json);
    }

    private static State state(String id, String raw) {
        return State.parse(raw).orElseThrow(() -> new IllegalArgumentException(
                "substance '" + id + "' names no state: " + raw));
    }

    private static VitaElement aspect(String id, String raw) {
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (VitaElement aspect : VitaElement.values()) {
            if (aspect != VitaElement.BALANCED && aspect.runeId().equals(normalized)) {
                return aspect;
            }
        }
        throw new IllegalArgumentException("the recipe of '" + id + "' names '" + raw
                + "', which is none of firmo, aqua, aura and igni");
    }
}
