package com.elderlexicon.mod.spelling.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RuneBufferTest {

    @Test
    void appendsUntilCapacityAndLocksAfter() {
        RuneBuffer buffer = new RuneBuffer(3);
        assertTrue(buffer.append("igni"));
        assertTrue(buffer.append("aqua"));
        assertTrue(buffer.append("impediunt"));
        assertTrue(buffer.isFull());
        assertFalse(buffer.append("extra"));
        assertEquals(3, buffer.size());
        assertEquals("igni -> aqua -> impediunt", buffer.previewString());
    }

    @Test
    void rejectsBlankEntries() {
        RuneBuffer buffer = new RuneBuffer();
        assertFalse(buffer.append(null));
        assertFalse(buffer.append(" "));
        assertTrue(buffer.isEmpty());
    }

    @Test
    void clearResetsState() {
        RuneBuffer buffer = new RuneBuffer();
        buffer.append("igni");
        buffer.append("aqua");
        assertEquals(2, buffer.size());
        buffer.clear();
        assertTrue(buffer.isEmpty());
        assertEquals("", buffer.previewString());
    }
}
