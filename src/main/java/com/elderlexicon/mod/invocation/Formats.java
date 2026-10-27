package com.elderlexicon.mod.invocation;

import com.elderlexicon.mod.magic.matter.Particles;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The formats of the invocation bench ({@value #RESOURCE}, docs/invocacao-design.md, section 3): a format knows only
 * its drawing and its parts, and matter knows only what it is. Any matter fills a principal part; nothing is made of a
 * list of combinations.
 */
public final class Formats {

    public static final String RESOURCE = "/data/elderlexicon/invocation/formats.json";
    /** The bench's grid is three by three. */
    public static final int SIDE = 3;

    private static volatile Map<String, Format> loaded;

    private Formats() {
    }

    /** Every format, in the order the data gives them. */
    public static Map<String, Format> all() {
        Map<String, Format> found = loaded;
        if (found == null) {
            synchronized (Formats.class) {
                found = loaded;
                if (found == null) {
                    found = read();
                    loaded = found;
                }
            }
        }
        return found;
    }

    private static Map<String, Format> read() {
        try (InputStream in = Formats.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing " + RESOURCE);
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Format> formats = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : root.getAsJsonObject("formats").entrySet()) {
                formats.put(entry.getKey(), format(entry.getKey(), entry.getValue().getAsJsonObject()));
            }
            return Collections.unmodifiableMap(formats);
        } catch (java.io.IOException failed) {
            throw new IllegalStateException("cannot read " + RESOURCE, failed);
        }
    }

    private static Format format(String id, JsonObject json) {
        List<String> pattern = new ArrayList<>();
        json.getAsJsonArray("pattern").forEach(row -> pattern.add(row.getAsString()));
        char principal = json.has("principal") ? json.get("principal").getAsString().charAt(0) : 'M';
        Map<Character, String> keys = new LinkedHashMap<>();
        if (json.has("keys")) {
            json.getAsJsonObject("keys").entrySet()
                    .forEach(key -> keys.put(key.getKey().charAt(0), key.getValue().getAsString()));
        }
        return new Format(id, json.has("name") ? json.get("name").getAsString() : id, List.copyOf(pattern), principal,
                Map.copyOf(keys), ResourceLocation.tryParse(json.get("item").getAsString()),
                json.has("count") ? json.get("count").getAsInt() : 1,
                json.has("damage") ? json.get("damage").getAsDouble() : 0.0D,
                json.has("speed") ? json.get("speed").getAsDouble() : 0.0D,
                json.has("mines") ? json.get("mines").getAsString() : null);
    }

    /** A format the grid draws, and what fills it. */
    public record Drawn(Format format, List<Integer> principal, List<Integer> parts, Particles matter, int window) {
    }

    /**
     * The format the grid draws, anywhere on it and either way round, with every square of its principal part holding
     * the same matter; empty when it draws none.
     */
    public static Optional<Drawn> drawn(Container grid) {
        int minX = SIDE;
        int minY = SIDE;
        int maxX = -1;
        int maxY = -1;
        for (int i = 0; i < SIDE * SIDE; i++) {
            if (!grid.getItem(i).isEmpty()) {
                minX = Math.min(minX, i % SIDE);
                minY = Math.min(minY, i / SIDE);
                maxX = Math.max(maxX, i % SIDE);
                maxY = Math.max(maxY, i / SIDE);
            }
        }
        if (maxX < 0) {
            return Optional.empty();
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        for (Format format : all().values()) {
            if (format.width() != width || format.height() != height) {
                continue;
            }
            for (boolean mirrored : new boolean[] {false, true}) {
                Optional<Drawn> drawn = match(grid, format, minX, minY, mirrored);
                if (drawn.isPresent()) {
                    return drawn;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Drawn> match(Container grid, Format format, int minX, int minY, boolean mirrored) {
        List<Integer> principal = new ArrayList<>();
        List<Integer> parts = new ArrayList<>();
        Particles matter = Particles.NONE;
        Particles first = null;
        int window = 0;
        for (int y = 0; y < format.height(); y++) {
            for (int x = 0; x < format.width(); x++) {
                char key = format.at(mirrored ? format.width() - 1 - x : x, y);
                int square = (minX + x) + SIDE * (minY + y);
                ItemStack stack = grid.getItem(square);
                if (key == ' ') {
                    if (!stack.isEmpty()) {
                        return Optional.empty();
                    }
                } else if (key == format.principal()) {
                    Optional<Particles> held = BenchMatter.of(stack);
                    if (held.isEmpty() || held.get().present().total() <= 0L
                            || (first != null && !BenchMatter.same(first, held.get()))) {
                        return Optional.empty();
                    }
                    first = first == null ? held.get() : first;
                    matter = matter.plus(held.get().present());
                    int written = Conjured.writtenWindow(stack);
                    if (written > 0) {
                        window = window == 0 ? written : Math.min(window, written);
                    }
                    principal.add(square);
                } else {
                    String wanted = format.keys().get(key);
                    if (wanted == null || !holds(stack, wanted)) {
                        return Optional.empty();
                    }
                    parts.add(square);
                }
            }
        }
        return principal.isEmpty() ? Optional.empty()
                : Optional.of(new Drawn(format, principal, parts, matter, window));
    }

    /** Whether a stack is what a key asks: an item by its id, or any item of a tag ({@code #...}). */
    private static boolean holds(ItemStack stack, String wanted) {
        if (stack.isEmpty()) {
            return false;
        }
        if (wanted.startsWith("#")) {
            ResourceLocation tag = ResourceLocation.tryParse(wanted.substring(1));
            return tag != null && stack.is(TagKey.create(Registries.ITEM, tag));
        }
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(wanted));
        return item != null && stack.is(item);
    }

    /** The conjured thing a drawn format makes: each one keeps its share of the matter, the parts, and the window. */
    public static ItemStack make(Drawn drawn, Container grid) {
        Format format = drawn.format();
        Item item = ForgeRegistries.ITEMS.getValue(format.item());
        if (item == null) {
            return ItemStack.EMPTY;
        }
        int count = Math.max(1, format.count());
        ItemStack made = new ItemStack(item, count);
        List<Particles> shares = drawn.matter().split(count);
        Conjured.setMatter(made, shares.get(0));
        Conjured.setFormat(made, format.id());
        Conjured.setRoom(made, (double) drawn.principal().size() / count);
        Conjured.setWindow(made, drawn.window());
        List<ItemStack> parts = new ArrayList<>();
        for (int square : drawn.parts()) {
            parts.add(grid.getItem(square).copyWithCount(1));
        }
        if (count == 1) {
            Conjured.setParts(made, parts);
        }
        return made;
    }
}
