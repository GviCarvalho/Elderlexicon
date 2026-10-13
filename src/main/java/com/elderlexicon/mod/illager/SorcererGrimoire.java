package com.elderlexicon.mod.illager;

import com.elderlexicon.mod.spell.block.RuneTokens;
import com.elderlexicon.mod.spell.block.SpellBlock;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.util.RandomSource;

/**
 * The grimoire a sorcerer carries, written by hand (docs/feiticeiro-design.md, stage 2): pages of the language, each
 * read with the trance and the surgit, as the book teaches (cap. 5), several spells a page, each at its own step. Which page serves is said by the page itself: how
 * far the target may be, how much the evoker likes it, and whether it is read only when wounded.
 */
public final class SorcererGrimoire {

    public static final String RESOURCE = "/data/elderlexicon/grimoires/sorcerer.json";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile SorcererGrimoire loaded;

    private final List<Page> pages;

    SorcererGrimoire(List<Page> pages) {
        this.pages = List.copyOf(pages);
    }

    /**
     * One page, written as a player writes one (docs/feiticeiro-design.md): a spell a line, each word a column, and each
     * column a step of time; {@code .} is a pause. With it, when it serves.
     */
    public record Page(String name, List<String> lines, double minRange, double maxRange, int weight,
                       boolean wounded) {

        public Page {
            lines = List.copyOf(lines);
            weight = Math.max(1, weight);
        }

        /** Whether the page serves against a target {@code distance} blocks away, the reader being {@code wounded}. */
        public boolean serves(double distance, boolean readerWounded) {
            return distance >= minRange && distance <= maxRange && (!wounded || readerWounded);
        }

        /** The page's text, as a grimoire keeps it. */
        public String text() {
            return String.join("\n", lines);
        }

        /** The page as the spirit reads it: its lines, each released at its own step. */
        public SpellBlock block() {
            return SpellBlock.parse(text(), RuneTokens::normalize);
        }

        /** Every word of the page, in reading order: what is seen above the reader's head. */
        public List<String> words() {
            return block().allRuneIds();
        }
    }

    /** The sorcerer's grimoire, as the mod writes it. */
    public static SorcererGrimoire get() {
        SorcererGrimoire found = loaded;
        if (found == null) {
            found = read();
            loaded = found;
        }
        return found;
    }

    public List<Page> pages() {
        return pages;
    }

    /** A page that serves, chosen by weight among them; empty when none does. */
    public Optional<Page> choose(Predicate<Page> serves, RandomSource random) {
        List<Page> serving = pages.stream().filter(serves).toList();
        int total = serving.stream().mapToInt(Page::weight).sum();
        if (total <= 0) {
            return Optional.empty();
        }
        int roll = random.nextInt(total);
        for (Page page : serving) {
            roll -= page.weight();
            if (roll < 0) {
                return Optional.of(page);
            }
        }
        return Optional.of(serving.get(serving.size() - 1));
    }

    private static SorcererGrimoire read() {
        try (InputStream stream = SorcererGrimoire.class.getResourceAsStream(RESOURCE)) {
            if (stream == null) {
                LOGGER.error("Missing {}", RESOURCE);
                return new SorcererGrimoire(List.of());
            }
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            return parse(root);
        } catch (Exception exception) {
            LOGGER.error("Unable to read {}", RESOURCE, exception);
            return new SorcererGrimoire(List.of());
        }
    }

    static SorcererGrimoire parse(JsonObject root) {
        List<Page> pages = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("pages")) {
            JsonObject page = element.getAsJsonObject();
            List<String> lines = new ArrayList<>();
            page.getAsJsonArray("lines").forEach(line -> lines.add(line.getAsString()));
            JsonArray range = page.getAsJsonArray("range");
            pages.add(new Page(page.get("name").getAsString(), lines,
                    range.get(0).getAsDouble(), range.get(1).getAsDouble(),
                    page.has("weight") ? page.get("weight").getAsInt() : 1,
                    page.has("wounded") && page.get("wounded").getAsBoolean()));
        }
        return new SorcererGrimoire(pages);
    }
}
