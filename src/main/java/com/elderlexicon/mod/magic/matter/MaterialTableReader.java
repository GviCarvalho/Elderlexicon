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
 *   "remove": ["substance or being", ...],
 *   "substances": {
 *     "water": {"name": "água", "recipe": {"aqua": 1}, "state": "liquid",
 *               "forms": {"solid": [{"block": "minecraft:ice"}], "liquid": [{"block": "minecraft:water"}],
 *                         "gas": [{"particle": "minecraft:cloud"}]}},
 *     "iron":  {"name": "ferro", "recipe": {"firmo": 0.9, "igni": 0.1}, "state": "solid",
 *               "forms": {"solid": [{"block": "minecraft:raw_iron_block"},
 *                                   {"item": "minecraft:raw_iron", "particles": 455}]}}
 *   },
 *   "beings": {
 *     "cow": {"name": "vaca", "recipe": {"firmo": 0.07, "aqua": 0.60, "aura": 0.30, "igni": 0.03},
 *             "entity": "minecraft:cow"}
 *   }
 * }
 * </pre>
 * A recipe gives each primordial by its aspect, in any proportion (they are normalized). A block holds 4096 particles
 * (16 UMU) of its substance, whatever it is; an item 256 (1 UMU), unless it says its own {@code particles}, as the items
 * the game joins into a block do (nine raw irons are a block of them). A gas or a plasma with no forms shows as the
 * nearest state that has some; a solid or a liquid with none is formless matter. A being's recipe is the proportion of
 * its body, and its entity the creature that shows it.
 */
public final class MaterialTableReader {

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
        if (root.has("beings")) {
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("beings").entrySet()) {
                into.being(being(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
        }
    }

    private static Being being(String id, JsonObject json) {
        if (!json.has("entity")) {
            throw new IllegalArgumentException("being '" + id + "' has no creature to show it");
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        return new Being(MaterialTableBuilder.normalize(id), name, recipe("being", id, json),
                json.get("entity").getAsString());
    }

    private static Composition recipe(String what, String id, JsonObject json) {
        if (!json.has("recipe")) {
            throw new IllegalArgumentException(what + " '" + id + "' has no recipe");
        }
        Map<VitaElement, Double> amounts = new EnumMap<>(VitaElement.class);
        for (Map.Entry<String, JsonElement> part : json.getAsJsonObject("recipe").entrySet()) {
            amounts.merge(aspect(id, part.getKey()), part.getValue().getAsDouble(), Double::sum);
        }
        return Composition.of(amounts);
    }

    private static Substance substance(String id, JsonObject json) {
        Composition recipe = recipe("substance", id, json);
        State nature = json.has("state") ? state(id, json.get("state").getAsString())
                : State.of(recipe.pureAspect().orElse(VitaElement.FIRMO)).orElse(State.SOLID);
        Map<State, List<Form>> forms = new EnumMap<>(State.class);
        if (json.has("forms")) {
            for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject("forms").entrySet()) {
                State state = state(id, entry.getKey());
                List<Form> list = new ArrayList<>();
                for (JsonElement element : entry.getValue().getAsJsonArray()) {
                    list.add(form(id, element.getAsJsonObject()));
                }
                forms.put(state, list);
            }
        }
        String name = json.has("name") ? json.get("name").getAsString() : id;
        return new Substance(MaterialTableBuilder.normalize(id), name, recipe, nature, forms);
    }

    private static Form form(String id, JsonObject json) {
        for (Form.Kind kind : Form.Kind.values()) {
            String key = kind.name().toLowerCase(Locale.ROOT);
            if (json.has(key)) {
                long fallback = switch (kind) {
                    case BLOCK -> Particles.BLOCK;
                    case ITEM -> Particles.ITEM;
                    default -> 0L;
                };
                long particles = json.has("particles") ? json.get("particles").getAsLong() : fallback;
                return new Form(kind, json.get(key).getAsString(), particles);
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
