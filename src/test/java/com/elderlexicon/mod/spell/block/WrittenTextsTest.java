package com.elderlexicon.mod.spell.block;

import com.elderlexicon.mod.spell.mark.NumberGlyphs;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WrittenTextsTest {

    @Test
    void anOlderThingIsWrittenAnewOnce() {
        CompoundTag tag = new CompoundTag();
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("C N SQ I"));
        tag.put("pages", pages);
        tag.putString("Galdraria", "C O Q I");
        WrittenTexts.upgrade(tag);
        assertEquals("C N " + NumberGlyphs.toGlyphs("20") + " I", tag.getList("pages", Tag.TAG_STRING).getString(0));
        assertEquals("C O " + NumberGlyphs.toGlyphs("0") + " I", tag.getString("Galdraria"));
        assertEquals(WrittenTexts.CURRENT, tag.getInt(WrittenTexts.KEY));
    }

    @Test
    void aThingInTheNewWritingKeepsItsLetters() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Galdraria", "Q C I");
        WrittenTexts.mark(tag);
        WrittenTexts.upgrade(tag);
        assertEquals("Q C I", tag.getString("Galdraria"), "a lone Q is ferit, not an old zero");
    }

    @Test
    void aThingWithNoTextIsLeftUnmarked() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Damage", 3);
        WrittenTexts.upgrade(tag);
        assertEquals(0, tag.getInt(WrittenTexts.KEY));
    }
}
