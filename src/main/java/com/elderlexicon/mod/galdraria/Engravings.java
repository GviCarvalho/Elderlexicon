package com.elderlexicon.mod.galdraria;

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
    private static final String TAG = "Galdraria";

    private Engravings() {
    }

    /** The runes engraved in {@code stack}, as written; empty when there are none. */
    public static Optional<String> of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        CompoundTag tag = stack.getTag();
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

    /** The words of a typed engraving: its first line only, at most {@link #MAX_COLUMNS} of them. */
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
        return words;
    }

    /** How a typed engraving is kept: each word as the grimoire writes it, a lone glyph in capitals. */
    public static String written(List<String> words) {
        List<String> kept = new ArrayList<>();
        for (String word : words) {
            String glyph = RuneTokens.written(word);
            kept.add(glyph.length() == 1 ? glyph.toUpperCase(Locale.ROOT) : glyph);
        }
        return String.join(" ", kept);
    }

    /** What the spirit reads in an engraving, word by word: rune names, numbers and marks; blanks for the unreadable. */
    public static List<String> read(String written) {
        List<String> read = new ArrayList<>();
        for (String word : words(written)) {
            read.add(RuneTokens.normalize(word));
        }
        return read;
    }
}
