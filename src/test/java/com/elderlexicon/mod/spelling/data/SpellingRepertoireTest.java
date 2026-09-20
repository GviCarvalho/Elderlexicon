package com.elderlexicon.mod.spelling.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpellingRepertoireTest {

    @Test
    void defaultsPopulateNineSlots() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertEquals(SpellingRepertoire.SLOT_COUNT, repertoire.slots().size());
        assertTrue(repertoire.runeForSlot(0).isPresent());
        assertTrue(repertoire.runeForSlot(3).isPresent());
    }

    @Test
    void assigningSlotPersistsThroughNbt() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        repertoire.assignSlot(2, "vis");
        CompoundTag tag = repertoire.save();

        SpellingRepertoire loaded = new SpellingRepertoire();
        loaded.load(tag);
        assertEquals("vis", loaded.runeForSlot(2).orElse(""));
    }

    @Test
    void rejectsInvalidSlotIndexes() {
        SpellingRepertoire repertoire = new SpellingRepertoire();
        assertFalse(repertoire.assignSlot(-1, "foo"));
        assertFalse(repertoire.assignSlot(SpellingRepertoire.SLOT_COUNT, "foo"));
    }
}
