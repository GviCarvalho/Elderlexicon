package com.elderlexicon.mod.ligabis;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockIntegrityTest {

    @Test
    void dirtIsTheWeakestAndObsidianOutlastsStone() {
        double dirt = BlockIntegrity.capacity(0.5D);
        double stone = BlockIntegrity.capacity(6.0D);
        double obsidian = BlockIntegrity.capacity(1200.0D);

        assertEquals(1.0D, dirt, 1.0E-9);
        assertTrue(stone > dirt, "stone lasts longer than dirt");
        assertTrue(obsidian > stone * 10, "obsidian lasts far longer than stone");
    }

    @Test
    void noBlockHasLessThanTheMinimum() {
        assertEquals(BlockIntegrity.MINIMUM, BlockIntegrity.capacity(0.0D), 1.0E-9);
        assertEquals(BlockIntegrity.MINIMUM, BlockIntegrity.capacity(-5.0D), 1.0E-9);
    }

    @Test
    void theMostResistantBlocksStayFinite() {
        assertTrue(Double.isFinite(BlockIntegrity.capacity(3_600_000.0D)));
    }
}
