package com.elderlexicon.mod.galdraria;

import com.elderlexicon.mod.spell.block.WrittenTexts;
import com.elderlexicon.mod.spell.block.GrimoirePage;
import com.elderlexicon.mod.spell.block.RuneTokens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Runes engraved in an item at the galdraria table (docs/galdraria-design.md): one line of up to ten columns, kept as
 * the grimoire writes it (a rune's name as its glyph). The spirit reads it like a page when the bearer says surgit, or
 * when it is called by the item's mark; the grindstone scrapes it away.
 */
public final class Engravings {

    /** The most columns (runes) an item holds. */
    public static final int MAX_COLUMNS = 10;
    /** The most characters a typed engraving may have, before it is read. */
    public static final int MAX_CHARS = 160;
    /** An empty cell: a column with nothing carved, a pause in the reading, as on a grimoire page. */
    public static final String PAUSE = GrimoirePage.EMPTY_CELL;
    private static final String TAG = "Galdraria";

    private Engravings() {
    }

    /** The runes engraved in {@code stack}, as written; empty when there are none. */
    public static Optional<String> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
        WrittenTexts.upgrade(tag);
        if (tag == null || !tag.contains(TAG, 8) || tag.getString(TAG).isBlank()) {
            return Optional.empty();
        }
        return Optional.of(tag.getString(TAG));
    }

    public static boolean has(ItemStack stack) {
        return of(stack).isPresent();
    }

    /** Engraves {@code written} (already as {@link #written} keeps it) in {@code stack}, over what was there. */
    public static void engrave(ItemStack stack, String written) {
        stack.getOrCreateTag().putString(TAG, written);
        WrittenTexts.mark(stack);
    }

    /** Scrapes the engraving off {@code stack}. */
    public static void scrape(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null) {
            return;
        }
        tag.remove(TAG);
        if (tag.isEmpty()) {
            stack.setTag(null);
        }
    }

    /** The words of a typed engraving, one per column, pauses included: its first line only, pauses at its end dropped. */
    public static List<String> words(String typed) {
        List<String> words = new ArrayList<>();
        if (typed == null) {
            return words;
        }
        String line = typed.split("\r?\n", 2)[0];
        for (String word : line.trim().split("\s+")) {
            if (!word.isEmpty()) {
                words.add(word);
            }
        }
        while (!words.isEmpty() && isPause(words.get(words.size() - 1))) {
            words.remove(words.size() - 1);
        }
        return words;
    }

    public static boolean isPause(String word) {
        return PAUSE.equals(word);
    }

    /** How many runes the words carve: every column but the pauses. This is what the burin spends. */
    public static int runes(List<String> words) {
        return (int) words.stream().filter(word -> !isPause(word)).count();
    }

    /** True when a word that is not a pause is something the spirit does not read. */
    public static boolean unreadable(List<String> words) {
        return words.stream().anyMatch(word -> !isPause(word) && RuneTokens.normalize(RuneTokens.written(word)).isEmpty());
    }

    /** How a typed engraving is kept: each word as the grimoire writes it, a lone glyph in capitals. */
    public static String written(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            if (isPause(word)) {
                kept.add(PAUSE);
                continue;
            }
            String glyph = RuneTokens.written(word);
            kept.add(glyph.length() == 1 ? glyph.toUpperCase(Locale.ROOT) : glyph);
        }
        return String.join(" ", kept);
    }

    /** What the spirit reads in an engraving, column by column: rune names, numbers and marks; blanks for pauses. */
    public static List<String> read(String written) {
        List<String> read = new ArrayList<>();
        for (String word : words(written)) {
            read.add(RuneTokens.normalize(word));
        }
        return read;
    }
}
