package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Which writing the texts kept in a thing are in (docs/fluxo-design.md). Older texts wrote the digits with the letters
 * Q to Z; since the digits have glyphs of their own and those letters are runes, an older text is written anew the
 * first time it is read ({@link NumberGlyphs#upgrade}), and the thing is marked as being in the new writing.
 * <p>
 * Whatever writes a text in a thing marks it ({@link #mark}), so that a lone {@code S} it holds is never taken for an
 * old 2.
 */
public final class WrittenTexts {

    /** The writing a thing's texts are in; missing, the older one. */
    public static final String KEY = "ElderGlyphs";
    /** The writing in which the digits have their own glyphs. */
    public static final int CURRENT = 2;

    private static final String[] TEXTS = {"Galdraria", "DetachedPageText"};
    private static final String PAGES = "pages";
    private static final String READINGS = "Readings";

    private WrittenTexts() {
    }

    public static void upgrade(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) {
            upgrade(stack.getTag());
        }
    }

    /** Writes the texts of {@code tag} anew if they are in the older writing, and marks it; once is enough. */
    public static void upgrade(CompoundTag tag) {
        if (tag == null || tag.getInt(KEY) >= CURRENT) {
            return;
        }
        boolean any = false;
        for (String key : TEXTS) {
            if (tag.contains(key, Tag.TAG_STRING)) {
                tag.putString(key, NumberGlyphs.upgrade(tag.getString(key)));
                any = true;
            }
        }
        if (tag.contains(PAGES, Tag.TAG_LIST)) {
            ListTag pages = tag.getList(PAGES, Tag.TAG_STRING);
            ListTag written = new ListTag();
            for (int i = 0; i < pages.size(); i++) {
                written.add(StringTag.valueOf(NumberGlyphs.upgrade(pages.getString(i))));
            }
            tag.put(PAGES, written);
            any = true;
        }
        if (tag.contains(READINGS, Tag.TAG_LIST)) {
            // What a page cost is kept by its text: it follows the page into the new writing.
            ListTag readings = tag.getList(READINGS, Tag.TAG_COMPOUND);
            for (int i = 0; i < readings.size(); i++) {
                CompoundTag reading = readings.getCompound(i);
                reading.putString("Text", NumberGlyphs.upgrade(reading.getString("Text")));
            }
            any = true;
        }
        if (any) {
            mark(tag);
        }
    }

    /** Says the texts of {@code tag} are in the new writing: whatever writes one calls this. */
    public static void mark(CompoundTag tag) {
        tag.putInt(KEY, CURRENT);
    }

    public static void mark(ItemStack stack) {
        mark(stack.getOrCreateTag());
    }
}
